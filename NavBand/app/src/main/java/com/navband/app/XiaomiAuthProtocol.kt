package com.navband.app

import android.os.Build
import org.bouncycastle.crypto.CryptoException
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.modes.CCMBlockCipher
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class XiaomiAuthProtocol(
    private val authKey: ByteArray
) {

    companion object {
        private const val COMMAND_TYPE = 1
        private const val CMD_NONCE = 26
        private const val CMD_AUTH = 27

        private const val PHONE_NONCE_SIZE = 16
        private const val DERIVED_DATA_SIZE = 64

        private const val CCM_TAG_BITS = 32

        private const val FIELD_COMMAND_TYPE = 1
        private const val FIELD_COMMAND_SUBTYPE = 2
        private const val FIELD_COMMAND_AUTH = 3

        private const val FIELD_AUTH_STATUS = 8
        private const val FIELD_AUTH_PHONE_NONCE = 30
        private const val FIELD_AUTH_WATCH_NONCE = 31
        private const val FIELD_AUTH_STEP3 = 32

        private const val FIELD_NONCE = 1
        private const val FIELD_HMAC = 2

        private const val FIELD_STEP3_NONCES = 1
        private const val FIELD_STEP3_DEVICE_INFO = 2

        private const val FIELD_DEVICE_UNKNOWN1 = 1
        private const val FIELD_DEVICE_API_LEVEL = 2
        private const val FIELD_DEVICE_PHONE_NAME = 3
        private const val FIELD_DEVICE_UNKNOWN3 = 4
        private const val FIELD_DEVICE_REGION = 5
    }

    private val secureRandom = SecureRandom()

    private var phoneNonce = ByteArray(0)

    private var encryptionKey = ByteArray(16)
    private var decryptionKey = ByteArray(16)

    private var encryptionNonce = ByteArray(4)
    private var decryptionNonce = ByteArray(4)

    var authenticated: Boolean = false
        private set

    init {
        require(authKey.size == 16) {
            "Xiaomi auth key deve essere di 16 byte"
        }
    }

    fun start(): ByteArray {
        authenticated = false

        phoneNonce =
            ByteArray(PHONE_NONCE_SIZE)

        secureRandom.nextBytes(phoneNonce)

        return buildNonceCommand(phoneNonce)
    }

    fun handleCommand(
        payload: ByteArray
    ): AuthResult {

        val command =
            try {
                parseCommand(payload)
            } catch (e: Exception) {
                return AuthResult.Error(
                    "Parsing comando Xiaomi fallito: ${e.message}"
                )
            }

        if (command.type != COMMAND_TYPE) {
            return AuthResult.Ignored
        }

        return when (command.subtype) {

            CMD_NONCE -> {

                val watchNonce =
                    command.watchNonce
                        ?: return AuthResult.Error(
                            "CMD_NONCE senza watch nonce"
                        )

                handleWatchNonce(watchNonce)
            }

            CMD_AUTH -> {

                authenticated = true

                AuthResult.Authenticated
            }

            else -> {

                if (command.authStatus == 1) {
                    authenticated = true

                    AuthResult.Authenticated
                } else {
                    AuthResult.Ignored
                }
            }
        }
    }

    private fun handleWatchNonce(
        watchNonce: WatchNonce
    ): AuthResult {

        if (phoneNonce.size != PHONE_NONCE_SIZE) {
            return AuthResult.Error(
                "Phone nonce non inizializzato"
            )
        }

        val step2Hmac =
            computeAuthStep3Hmac(
                secretKey = authKey,
                phoneNonce = phoneNonce,
                watchNonce = watchNonce.nonce
            )

        if (step2Hmac.size != DERIVED_DATA_SIZE) {
            return AuthResult.Error(
                "Derivazione chiavi Xiaomi non valida"
            )
        }

        decryptionKey =
            step2Hmac.copyOfRange(0, 16)

        encryptionKey =
            step2Hmac.copyOfRange(16, 32)

        decryptionNonce =
            step2Hmac.copyOfRange(32, 36)

        encryptionNonce =
            step2Hmac.copyOfRange(36, 40)

        val expectedHmac =
            hmacSha256(
                key = decryptionKey,
                input = concat(
                    watchNonce.nonce,
                    phoneNonce
                )
            )

        if (!expectedHmac.contentEquals(watchNonce.hmac)) {
            return AuthResult.Error(
                "Watch HMAC non valido: auth key errata"
            )
        }

        val encryptedNonces =
            hmacSha256(
                key = encryptionKey,
                input = concat(
                    phoneNonce,
                    watchNonce.nonce
                )
            )

        val deviceInfo =
            buildAuthDeviceInfo()

        val encryptedDeviceInfo =
            encryptAuthDeviceInfo(
                deviceInfo
            )

        val command =
            buildAuthCommand(
                encryptedNonces = encryptedNonces,
                encryptedDeviceInfo = encryptedDeviceInfo
            )

        return AuthResult.AuthCommand(
            command = command
        )
    }

    private fun buildNonceCommand(
        nonce: ByteArray
    ): ByteArray {

        val phoneNonceMessage =
            protoMessage(
                fieldBytes(
                    FIELD_NONCE,
                    nonce
                )
            )

        val authMessage =
            protoMessage(
                fieldBytes(
                    FIELD_AUTH_PHONE_NONCE,
                    phoneNonceMessage
                )
            )

        return protoMessage(
            fieldVarint(
                FIELD_COMMAND_TYPE,
                COMMAND_TYPE.toLong()
            ),
            fieldVarint(
                FIELD_COMMAND_SUBTYPE,
                CMD_NONCE.toLong()
            ),
            fieldBytes(
                FIELD_COMMAND_AUTH,
                authMessage
            )
        )
    }

    private fun buildAuthCommand(
        encryptedNonces: ByteArray,
        encryptedDeviceInfo: ByteArray
    ): ByteArray {

        val authStep3 =
            protoMessage(
                fieldBytes(
                    FIELD_STEP3_NONCES,
                    encryptedNonces
                ),
                fieldBytes(
                    FIELD_STEP3_DEVICE_INFO,
                    encryptedDeviceInfo
                )
            )

        val authMessage =
            protoMessage(
                fieldBytes(
                    FIELD_AUTH_STEP3,
                    authStep3
                )
            )

        return protoMessage(
            fieldVarint(
                FIELD_COMMAND_TYPE,
                COMMAND_TYPE.toLong()
            ),
            fieldVarint(
                FIELD_COMMAND_SUBTYPE,
                CMD_AUTH.toLong()
            ),
            fieldBytes(
                FIELD_COMMAND_AUTH,
                authMessage
            )
        )
    }

    private fun buildAuthDeviceInfo(): ByteArray {

        val region =
            Locale.getDefault()
                .language
                .take(2)
                .uppercase(Locale.ROOT)

        return protoMessage(
            fieldVarint(
                FIELD_DEVICE_UNKNOWN1,
                0
            ),
            fieldFixed32(
                FIELD_DEVICE_API_LEVEL,
                Build.VERSION.SDK_INT.toFloat()
                    .toBits()
            ),
            fieldString(
                FIELD_DEVICE_PHONE_NAME,
                Build.MODEL
            ),
            fieldVarint(
                FIELD_DEVICE_UNKNOWN3,
                224
            ),
            fieldString(
                FIELD_DEVICE_REGION,
                region
            )
        )
    }

    private fun encryptAuthDeviceInfo(
        deviceInfo: ByteArray
    ): ByteArray {

        val nonce =
            ByteBuffer
                .allocate(12)
                .order(ByteOrder.LITTLE_ENDIAN)
                .put(encryptionNonce)
                .putInt(0)
                .putInt(0)
                .array()

        return aesCcmEncrypt(
            key = encryptionKey,
            nonce = nonce,
            payload = deviceInfo
        )
    }

    private fun aesCcmEncrypt(
        key: ByteArray,
        nonce: ByteArray,
        payload: ByteArray
    ): ByteArray {

        return try {

            val cipher =
                CCMBlockCipher(
                    AESEngine()
                )

            cipher.init(
                true,
                AEADParameters(
                    KeyParameter(key),
                    CCM_TAG_BITS,
                    nonce,
                    null
                )
            )

            val output =
                ByteArray(
                    cipher.getOutputSize(
                        payload.size
                    )
                )

            val count =
                cipher.processBytes(
                    payload,
                    0,
                    payload.size,
                    output,
                    0
                )

            cipher.doFinal(
                output,
                count
            )

            output

        } catch (e: CryptoException) {

            throw IllegalStateException(
                "AES-CCM Xiaomi fallito",
                e
            )
        }
    }

    private fun computeAuthStep3Hmac(
        secretKey: ByteArray,
        phoneNonce: ByteArray,
        watchNonce: ByteArray
    ): ByteArray {

        val hmacKeyBytes =
            hmacSha256(
                key = concat(
                    phoneNonce,
                    watchNonce
                ),
                input = secretKey
            )

        val output =
            ByteArray(DERIVED_DATA_SIZE)

        var previous =
            ByteArray(0)

        var counter =
            1

        var offset =
            0

        while (offset < output.size) {

            val input =
                concat(
                    previous,
                    "miwear-auth"
                        .toByteArray(Charsets.UTF_8),
                    byteArrayOf(
                        counter.toByte()
                    )
                )

            previous =
                hmacSha256(
                    key = hmacKeyBytes,
                    input = input
                )

            val count =
                minOf(
                    previous.size,
                    output.size - offset
                )

            System.arraycopy(
                previous,
                0,
                output,
                offset,
                count
            )

            offset += count
            counter++
        }

        return output
    }

    private fun hmacSha256(
        key: ByteArray,
        input: ByteArray
    ): ByteArray {

        val mac =
            Mac.getInstance(
                "HmacSHA256"
            )

        mac.init(
            SecretKeySpec(
                key,
                "HmacSHA256"
            )
        )

        return mac.doFinal(input)
    }

    private fun concat(
        vararg arrays: ByteArray
    ): ByteArray {

        val output =
            ByteArrayOutputStream()

        arrays.forEach {
            output.write(it)
        }

        return output.toByteArray()
    }

    private fun protoMessage(
        vararg fields: ByteArray
    ): ByteArray {

        val output =
            ByteArrayOutputStream()

        fields.forEach {
            output.write(it)
        }

        return output.toByteArray()
    }

    private fun fieldVarint(
        field: Int,
        value: Long
    ): ByteArray {

        return concat(
            encodeVarint(
                ((field shl 3) or 0).toLong()
            ),
            encodeVarint(value)
        )
    }

    private fun fieldFixed32(
        field: Int,
        value: Int
    ): ByteArray {

        val bytes =
            ByteBuffer
                .allocate(4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(value)
                .array()

        return concat(
            encodeVarint(
                ((field shl 3) or 5).toLong()
            ),
            bytes
        )
    }

    private fun fieldBytes(
        field: Int,
        value: ByteArray
    ): ByteArray {

        return concat(
            encodeVarint(
                ((field shl 3) or 2).toLong()
            ),
            encodeVarint(
                value.size.toLong()
            ),
            value
        )
    }

    private fun fieldString(
        field: Int,
        value: String
    ): ByteArray {

        return fieldBytes(
            field,
            value.toByteArray(
                Charsets.UTF_8
            )
        )
    }

    private fun encodeVarint(
        value: Long
    ): ByteArray {

        var current =
            value

        val output =
            ByteArrayOutputStream()

        while (true) {

            if (
                current and 0x7FL
                == current
            ) {

                output.write(
                    current.toInt()
                )

                return output.toByteArray()
            }

            output.write(
                ((current and 0x7F) or 0x80)
                    .toInt()
            )

            current =
                current ushr 7
        }
    }

    private data class ParsedCommand(
        val type: Int,
        val subtype: Int,
        val authStatus: Int?,
        val watchNonce: WatchNonce?
    )

    private data class WatchNonce(
        val nonce: ByteArray,
        val hmac: ByteArray
    )

    private fun parseCommand(
        payload: ByteArray
    ): ParsedCommand {

        val reader =
            ProtoReader(payload)

        var type =
            0

        var subtype =
            0

        var authStatus:
            Int? = null

        var watchNonce:
            WatchNonce? = null

        while (reader.hasRemaining()) {

            val tag =
                reader.readVarint()

            val field =
                (tag ushr 3).toInt()

            val wireType =
                (tag and 7).toInt()

            when (field) {

                FIELD_COMMAND_TYPE -> {

                    if (wireType == 0) {
                        type =
                            reader
                                .readVarint()
                                .toInt()
                    } else {
                        reader.skip(wireType)
                    }
                }

                FIELD_COMMAND_SUBTYPE -> {

                    if (wireType == 0) {
                        subtype =
                            reader
                                .readVarint()
                                .toInt()
                    } else {
                        reader.skip(wireType)
                    }
                }

                FIELD_COMMAND_AUTH -> {

                    if (wireType == 2) {

                        val auth =
                            reader
                                .readBytes()

                        val parsedAuth =
                            parseAuth(auth)

                        if (
                            parsedAuth != null
                        ) {
                            watchNonce =
                                parsedAuth.watchNonce

                            authStatus =
                                parsedAuth.status
                        }

                    } else {
                        reader.skip(wireType)
                    }
                }

                else -> {
                    reader.skip(wireType)
                }
            }
        }

        return ParsedCommand(
            type = type,
            subtype = subtype,
            authStatus = authStatus,
            watchNonce = watchNonce
        )
    }

    private data class ParsedAuth(
        val status: Int?,
        val watchNonce: WatchNonce?
    )

    private fun parseAuth(
        payload: ByteArray
    ): ParsedAuth? {

        val reader =
            ProtoReader(payload)

        var status:
            Int? = null

        var watchNonce:
            WatchNonce? = null

        while (reader.hasRemaining()) {

            val tag =
                reader.readVarint()

            val field =
                (tag ushr 3).toInt()

            val wireType =
                (tag and 7).toInt()

            when (field) {

                FIELD_AUTH_STATUS -> {

                    if (wireType == 0) {
                        status =
                            reader
                                .readVarint()
                                .toInt()
                    } else {
                        reader.skip(wireType)
                    }
                }

                FIELD_AUTH_WATCH_NONCE -> {

                    if (wireType == 2) {

                        watchNonce =
                            parseWatchNonce(
                                reader.readBytes()
                            )

                    } else {
                        reader.skip(wireType)
                    }
                }

                else -> {
                    reader.skip(wireType)
                }
            }
        }

        return ParsedAuth(
            status = status,
            watchNonce = watchNonce
        )
    }

    private fun parseWatchNonce(
        payload: ByteArray
    ): WatchNonce? {

        val reader =
            ProtoReader(payload)

        var nonce:
            ByteArray? = null

        var hmac:
            ByteArray? = null

        while (reader.hasRemaining()) {

            val tag =
                reader.readVarint()

            val field =
                (tag ushr 3).toInt()

            val wireType =
                (tag and 7).toInt()

            when (field) {

                FIELD_NONCE -> {

                    if (wireType == 2) {
                        nonce =
                            reader.readBytes()
                    } else {
                        reader.skip(wireType)
                    }
                }

                FIELD_HMAC -> {

                    if (wireType == 2) {
                        hmac =
                            reader.readBytes()
                    } else {
                        reader.skip(wireType)
                    }
                }

                else -> {
                    reader.skip(wireType)
                }
            }
        }

        if (
            nonce == null ||
            hmac == null
        ) {
            return null
        }

        return WatchNonce(
            nonce = nonce,
            hmac = hmac
        )
    }

    private class ProtoReader(
        private val data: ByteArray
    ) {

        private var position =
            0

        fun hasRemaining(): Boolean =
            position < data.size

        fun readVarint(): Long {

            var result =
                0L

            var shift =
                0

            while (true) {

                if (
                    position >= data.size
                ) {
                    throw IllegalStateException(
                        "protobuf troncato"
                    )
                }

                val value =
                    data[position++]
                        .toInt() and 0xFF

                result =
                    result or
                        (
                            (value and 0x7F)
                                .toLong()
                                .shl(shift)
                        )

                if (
                    value and 0x80
                    == 0
                ) {
                    return result
                }

                shift += 7

                if (shift > 63) {
                    throw IllegalStateException(
                        "protobuf varint troppo lungo"
                    )
                }
            }
        }

        fun readBytes(): ByteArray {

            val length =
                readVarint()
                    .toInt()

            if (
                length < 0 ||
                position + length > data.size
            ) {
                throw IllegalStateException(
                    "protobuf bytes non validi"
                )
            }

            val result =
                data.copyOfRange(
                    position,
                    position + length
                )

            position += length

            return result
        }

        fun skip(
            wireType: Int
        ) {

            when (wireType) {

                0 -> readVarint()

                1 -> skipBytes(8)

                2 -> {
                    val length =
                        readVarint()
                            .toInt()
                    skipBytes(length)
                }

                5 -> skipBytes(4)

                else ->
                    throw IllegalStateException(
                        "wire type protobuf non supportato: $wireType"
                    )
            }
        }

        private fun skipBytes(
            count: Int
        ) {

            if (
                count < 0 ||
                position + count > data.size
            ) {
                throw IllegalStateException(
                    "protobuf fuori limite"
                )
            }

            position += count
        }
    }

    sealed class AuthResult {

        data class AuthCommand(
            val command: ByteArray
        ) : AuthResult()

        data object Authenticated :
            AuthResult()

        data object Ignored :
            AuthResult()

        data class Error(
            val message: String
        ) : AuthResult()
    }
}
