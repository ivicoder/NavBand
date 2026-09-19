package com.navband.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Small on-device maneuver classifier trained from independently generated
 * synthetic maneuver icons. No Google Maps assets or model weights are bundled.
 *
 * Put maneuver_model.bin in app/src/main/res/raw/ as maneuver_model.bin.
 */
object ManeuverBitmapClassifier {

    private const val INPUT_SIZE = 64
    private const val CHANNELS = 1
    private const val MIN_CONFIDENCE = 0.60f
    private const val MAGIC = "NBMV"

    private val labels = arrayOf(
        "LEFT",
        "RIGHT",
        "SLIGHT_LEFT",
        "SLIGHT_RIGHT",
        "STRAIGHT",
        "U_TURN",
        "ROUNDABOUT",
        "KEEP_LEFT",
        "KEEP_RIGHT",
        "UNKNOWN"
    )

    @Volatile
    private var network: Network? = null

    data class Prediction(
        val direction: NavigationDirection,
        val confidence: Float,
        val label: String
    )

    private data class Tensor(
        val shape: IntArray,
        val values: FloatArray
    )

    private data class Network(
        val conv1W: FloatArray,
        val conv1B: FloatArray,
        val conv2W: FloatArray,
        val conv2B: FloatArray,
        val conv3W: FloatArray,
        val conv3B: FloatArray,
        val fc1W: FloatArray,
        val fc1B: FloatArray,
        val fc2W: FloatArray,
        val fc2B: FloatArray
    )

    fun classify(
        context: Context,
        bitmap: Bitmap?
    ): Prediction? {
        if (bitmap == null || bitmap.width < 4 || bitmap.height < 4) {
            return null
        }

        val net = network ?: synchronized(this) {
            network ?: loadNetwork(context.applicationContext).also { network = it }
        }

        val input = preprocess(bitmap)
        val logits = infer(net, input)
        val probabilities = softmax(logits)

        var bestIndex = 0
        var bestProbability = probabilities[0]
        for (i in 1 until probabilities.size) {
            if (probabilities[i] > bestProbability) {
                bestProbability = probabilities[i]
                bestIndex = i
            }
        }

        if (bestProbability < MIN_CONFIDENCE) {
            return null
        }

        val label = labels[bestIndex]
        val direction = when (label) {
            "LEFT" -> NavigationDirection.LEFT
            "RIGHT" -> NavigationDirection.RIGHT
            "SLIGHT_LEFT" -> NavigationDirection.SLIGHT_LEFT
            "SLIGHT_RIGHT" -> NavigationDirection.SLIGHT_RIGHT
            "STRAIGHT" -> NavigationDirection.STRAIGHT
            "U_TURN" -> NavigationDirection.U_TURN
            "ROUNDABOUT" -> NavigationDirection.ROUNDABOUT
            "KEEP_LEFT" -> NavigationDirection.SLIGHT_LEFT
            "KEEP_RIGHT" -> NavigationDirection.SLIGHT_RIGHT
            else -> NavigationDirection.UNKNOWN
        }

        if (direction == NavigationDirection.UNKNOWN) {
            return null
        }

        return Prediction(
            direction = direction,
            confidence = bestProbability,
            label = label
        )
    }

    private fun loadNetwork(context: Context): Network {
        val bytes = context.resources.openRawResource(R.raw.maneuver_model).use { it.readBytes() }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        val magicBytes = ByteArray(4)
        buffer.get(magicBytes)
        val magic = magicBytes.toString(Charsets.US_ASCII)
        require(magic == MAGIC) { "Invalid maneuver model magic: $magic" }

        val version = buffer.int
        require(version == 1) { "Unsupported maneuver model version: $version" }

        val inputSize = buffer.int
        require(inputSize == INPUT_SIZE) { "Unexpected maneuver model input size: $inputSize" }

        val classCount = buffer.int
        require(classCount == labels.size) {
            "Unexpected maneuver model class count: $classCount"
        }

        val tensorCount = buffer.int
        require(tensorCount == 10) { "Unexpected tensor count: $tensorCount" }

        val tensors = ArrayList<Tensor>(tensorCount)
        repeat(tensorCount) {
            val nameLength = buffer.int
            require(nameLength in 1..512) { "Invalid tensor name length: $nameLength" }
            val nameBytes = ByteArray(nameLength)
            buffer.get(nameBytes)

            val rank = buffer.int
            require(rank in 1..8) { "Invalid tensor rank: $rank" }
            val shape = IntArray(rank)
            var valueCount = 1
            for (i in 0 until rank) {
                shape[i] = buffer.int
                require(shape[i] > 0) { "Invalid tensor dimension" }
                valueCount = Math.multiplyExact(valueCount, shape[i])
            }

            val values = FloatArray(valueCount)
            for (i in values.indices) {
                values[i] = buffer.float
            }
            tensors += Tensor(shape, values)
        }

        return Network(
            conv1W = tensors[0].values,
            conv1B = tensors[1].values,
            conv2W = tensors[2].values,
            conv2B = tensors[3].values,
            conv3W = tensors[4].values,
            conv3B = tensors[5].values,
            fc1W = tensors[6].values,
            fc1B = tensors[7].values,
            fc2W = tensors[8].values,
            fc2B = tensors[9].values
        )
    }

    private fun preprocess(source: Bitmap): FloatArray {
        val canvasBitmap = Bitmap.createBitmap(
            INPUT_SIZE,
            INPUT_SIZE,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(canvasBitmap)
        canvas.drawColor(Color.BLACK)

        val scale = minOf(
            INPUT_SIZE.toFloat() / source.width,
            INPUT_SIZE.toFloat() / source.height
        )

        val width = source.width * scale
        val height = source.height * scale
        val left = (INPUT_SIZE - width) / 2f
        val top = (INPUT_SIZE - height) / 2f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(
            source,
            null,
            android.graphics.RectF(
                left,
                top,
                left + width,
                top + height
            ),
            paint
        )

        val input = FloatArray(INPUT_SIZE * INPUT_SIZE * CHANNELS)
        var index = 0

        for (y in 0 until INPUT_SIZE) {
            for (x in 0 until INPUT_SIZE) {
                val pixel = canvasBitmap.getPixel(x, y)
                val alpha = Color.alpha(pixel) / 255f
                val luminance = (
                    0.299f * Color.red(pixel) +
                        0.587f * Color.green(pixel) +
                        0.114f * Color.blue(pixel)
                    ) / 255f

                // Works for both white-on-black bitmaps and alpha-only icons.
                input[index++] = (luminance * alpha).coerceIn(0f, 1f)
            }
        }

        canvasBitmap.recycle()
        return input
    }

    private fun infer(net: Network, input: FloatArray): FloatArray {
        var x = conv3x3(input, 1, INPUT_SIZE, INPUT_SIZE, net.conv1W, net.conv1B, 8)
        x = relu(x)
        x = maxPool2x2(x, 8, 62, 62)

        x = conv3x3(x, 8, 31, 31, net.conv2W, net.conv2B, 16)
        x = relu(x)
        x = maxPool2x2(x, 16, 29, 29)

        x = conv3x3(x, 16, 14, 14, net.conv3W, net.conv3B, 32)
        x = relu(x)
        x = maxPool2x2(x, 32, 12, 12)

        // 32 x 6 x 6 = 1152 features, same layout as PyTorch Flatten().
        val hidden = FloatArray(64)
        for (o in hidden.indices) {
            var sum = net.fc1B[o]
            val base = o * x.size
            for (i in x.indices) {
                sum += net.fc1W[base + i] * x[i]
            }
            hidden[o] = if (sum > 0f) sum else 0f
        }

        val logits = FloatArray(labels.size)
        for (o in logits.indices) {
            var sum = net.fc2B[o]
            val base = o * hidden.size
            for (i in hidden.indices) {
                sum += net.fc2W[base + i] * hidden[i]
            }
            logits[o] = sum
        }

        return logits
    }

    private fun conv3x3(
        input: FloatArray,
        inChannels: Int,
        inputHeight: Int,
        inputWidth: Int,
        weights: FloatArray,
        bias: FloatArray,
        outChannels: Int
    ): FloatArray {
        val outputHeight = inputHeight - 2
        val outputWidth = inputWidth - 2
        val output = FloatArray(outChannels * outputHeight * outputWidth)

        for (oc in 0 until outChannels) {
            for (y in 0 until outputHeight) {
                for (x in 0 until outputWidth) {
                    var sum = bias[oc]

                    for (ic in 0 until inChannels) {
                        val inputChannelBase = ic * inputHeight * inputWidth
                        val weightChannelBase = (oc * inChannels + ic) * 9

                        for (ky in 0..2) {
                            val inputRow = inputChannelBase + (y + ky) * inputWidth + x
                            val weightRow = weightChannelBase + ky * 3
                            sum += input[inputRow] * weights[weightRow]
                            sum += input[inputRow + 1] * weights[weightRow + 1]
                            sum += input[inputRow + 2] * weights[weightRow + 2]
                        }
                    }

                    val outputIndex = oc * outputHeight * outputWidth + y * outputWidth + x
                    output[outputIndex] = sum
                }
            }
        }

        return output
    }

    private fun relu(values: FloatArray): FloatArray {
        for (i in values.indices) {
            if (values[i] < 0f) values[i] = 0f
        }
        return values
    }

    private fun maxPool2x2(
        input: FloatArray,
        channels: Int,
        inputHeight: Int,
        inputWidth: Int
    ): FloatArray {
        val outputHeight = inputHeight / 2
        val outputWidth = inputWidth / 2
        val output = FloatArray(channels * outputHeight * outputWidth)

        for (c in 0 until channels) {
            val inputBase = c * inputHeight * inputWidth
            val outputBase = c * outputHeight * outputWidth

            for (y in 0 until outputHeight) {
                for (x in 0 until outputWidth) {
                    val iy = y * 2
                    val ix = x * 2
                    var max = input[inputBase + iy * inputWidth + ix]
                    max = maxOf(max, input[inputBase + iy * inputWidth + ix + 1])
                    max = maxOf(max, input[inputBase + (iy + 1) * inputWidth + ix])
                    max = maxOf(max, input[inputBase + (iy + 1) * inputWidth + ix + 1])
                    output[outputBase + y * outputWidth + x] = max
                }
            }
        }

        return output
    }

    private fun softmax(logits: FloatArray): FloatArray {
        var max = logits[0]
        for (i in 1 until logits.size) max = maxOf(max, logits[i])

        val result = FloatArray(logits.size)
        var sum = 0.0
        for (i in logits.indices) {
            val e = kotlin.math.exp((logits[i] - max).toDouble())
            result[i] = e.toFloat()
            sum += e
        }

        if (sum == 0.0) return result
        for (i in result.indices) result[i] = (result[i] / sum.toFloat())
        return result
    }
}
