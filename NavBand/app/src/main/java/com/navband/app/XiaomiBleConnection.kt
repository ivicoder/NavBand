package com.navband.app

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

class XiaomiBleConnection(
    private val context: Context,
    private val onConnected: () -> Unit = {},
    private val onDisconnected: () -> Unit = {},
    private val onServicesDiscovered: (BluetoothGatt) -> Unit = {},
    private val onError: (String) -> Unit = {},
    private val onDebug: (String) -> Unit = {}
) {

    companion object {

        private val FE95_SERVICE =
            UUID.fromString(
                "0000fe95-0000-1000-8000-00805f9b34fb"
            )

        private val FE95_READ =
            UUID.fromString(
                "00000051-0000-1000-8000-00805f9b34fb"
            )

        private val FE95_WRITE =
            UUID.fromString(
                "00000052-0000-1000-8000-00805f9b34fb"
            )

        private val CLIENT_CONFIG =
            UUID.fromString(
                "00002902-0000-1000-8000-00805f9b34fb"
            )

        private const val MAX_WRITE_SIZE = 244
    }

    private var bluetoothGatt: BluetoothGatt? = null

    private var writeCharacteristic:
        BluetoothGattCharacteristic? = null

    private var authProtocol:
        XiaomiAuthProtocol? = null

    private var authStarted = false

    private var incomingChunkCount = 0

    private val incomingChunks =
        mutableMapOf<Int, ByteArray>()

    private var outgoingChunks:
        List<ByteArray> = emptyList()

    private var outgoingChunkIndex = 0

    private var waitingForChunkStartAck = false

    private var waitingForWrite = false

    /*
     * Stato separato per distinguere la scrittura
     * del CHUNK START dalla scrittura dei veri chunk.
     */
    private var waitingForChunkStartWrite = false

    private var outgoingChunkInFlight = false

    fun connect(device: BluetoothDevice) {
        disconnect()
        bluetoothGatt = connectGatt(device)
    }

    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null

        authProtocol = null
        authStarted = false

        incomingChunkCount = 0
        incomingChunks.clear()

        outgoingChunks = emptyList()
        outgoingChunkIndex = 0
        waitingForChunkStartAck = false
        waitingForWrite = false
        waitingForChunkStartWrite = false
        outgoingChunkInFlight = false
    }

    @SuppressLint("MissingPermission")
    private fun connectGatt(
        device: BluetoothDevice
    ): BluetoothGatt {

        return device.connectGatt(
            context,
            false,
            gattCallback,
            BluetoothDevice.TRANSPORT_LE
        )
    }

    @SuppressLint("MissingPermission")
    private fun configureFe95(
        gatt: BluetoothGatt
    ) {

        val service =
            gatt.getService(FE95_SERVICE)

        if (service == null) {
            onError("Servizio FE95 non trovato")
            return
        }

        val readCharacteristic =
            service.getCharacteristic(FE95_READ)

        val write =
            service.getCharacteristic(FE95_WRITE)

        if (readCharacteristic == null) {
            onError(
                "Caratteristica FE95/51 non trovata"
            )
            return
        }

        if (write == null) {
            onError(
                "Caratteristica FE95/52 non trovata"
            )
            return
        }

        writeCharacteristic = write

        onDebug(
            ">>> CANALE XIAOMI FE95 IDENTIFICATO\n" +
                "READ/NOTIFY: ${readCharacteristic.uuid}\n" +
                "WRITE: ${write.uuid}"
        )

        val notificationEnabled =
            gatt.setCharacteristicNotification(
                readCharacteristic,
                true
            )

        if (!notificationEnabled) {
            onError(
                "setCharacteristicNotification(FE95/51) fallito"
            )
            return
        }

        val descriptor =
            readCharacteristic.getDescriptor(
                CLIENT_CONFIG
            )

        if (descriptor == null) {
            onError(
                "CCCD FE95/51 non trovato"
            )
            return
        }

        descriptor.value =
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE

        if (!gatt.writeDescriptor(descriptor)) {
            onError(
                "Scrittura CCCD FE95/51 fallita"
            )
            return
        }

        onDebug(
            ">>> NOTIFY FE95/51 RICHIESTA"
        )
    }

    @SuppressLint("MissingPermission")
    private fun startAuthentication() {

        if (authStarted) {
            return
        }

        val authKey =
            XiaomiAuthManager.getAuthKey(context)

        if (authKey == null) {

            onError(
                "Auth key Xiaomi non configurata"
            )

            return
        }

        authProtocol =
            XiaomiAuthProtocol(authKey)

        authStarted = true

        onDebug(
            ">>> XIAOMI AUTH AVVIATA"
        )

        val firstCommand =
            authProtocol!!
                .start()

        onDebug(
            ">>> PHONE NONCE GENERATO\n" +
                "DATA: ${toHex(firstCommand)}"
        )

        sendChunked(
            firstCommand
        )
    }

    @SuppressLint("MissingPermission")
    private fun sendChunked(
        payload: ByteArray
    ) {

        val characteristic =
            writeCharacteristic
                ?: run {
                    onError(
                        "FE95/52 non disponibile"
                    )
                    return
                }

        if (payload.isEmpty()) {
            onError(
                "Payload Xiaomi vuoto"
            )
            return
        }

        val chunkPayloadSize =
            MAX_WRITE_SIZE - 2

        val chunks =
            ArrayList<ByteArray>()

        var offset = 0

        while (offset < payload.size) {

            val end =
                minOf(
                    offset + chunkPayloadSize,
                    payload.size
                )

            chunks.add(
                payload.copyOfRange(
                    offset,
                    end
                )
            )

            offset = end
        }

        outgoingChunks = chunks
        outgoingChunkIndex = 0
        waitingForChunkStartAck = true
        waitingForWrite = false
        waitingForChunkStartWrite = true
        outgoingChunkInFlight = false

        val start =
            ByteBuffer
                .allocate(6)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putShort(0)
                .put(0)
                .put(0)
                .putShort(chunks.size.toShort())
                .array()

        onDebug(
            ">>> XIAOMI CHUNK START\n" +
                "CHUNKS: ${chunks.size}\n" +
                "DATA: ${toHex(start)}"
        )

        writeRaw(
            characteristic,
            start
        )
    }

    @SuppressLint("MissingPermission")
    private fun sendNextOutgoingChunk() {

        if (
            !waitingForChunkStartAck ||
            waitingForWrite
        ) {
            return
        }

        if (
            outgoingChunkIndex >=
            outgoingChunks.size
        ) {

            waitingForChunkStartAck = false

            onDebug(
                ">>> XIAOMI CHUNK TRASMISSIONE COMPLETATA"
            )

            return
        }

        val characteristic =
            writeCharacteristic
                ?: return

        val chunkNumber =
            outgoingChunkIndex + 1

        val payload =
            outgoingChunks[
                outgoingChunkIndex
            ]

        val packet =
            ByteBuffer
                .allocate(
                    2 + payload.size
                )
                .order(ByteOrder.LITTLE_ENDIAN)
                .putShort(
                    chunkNumber.toShort()
                )
                .put(payload)
                .array()

        onDebug(
            ">>> XIAOMI CHUNK $chunkNumber/" +
                "${outgoingChunks.size}\n" +
                "DATA: ${toHex(packet)}"
        )

        waitingForWrite = true
        outgoingChunkInFlight = true
        waitingForChunkStartWrite = false

        writeRaw(
            characteristic,
            packet
        )
    }

    @SuppressLint("MissingPermission")
    private fun writeRaw(
        characteristic:
            BluetoothGattCharacteristic,
        data: ByteArray
    ) {

        characteristic.writeType =
            BluetoothGattCharacteristic
                .WRITE_TYPE_DEFAULT

        characteristic.value = data

        onDebug(
            ">>> FE95 WRITE\\n" +
                "UUID: ${characteristic.uuid}\\n" +
                "WRITE TYPE: ${characteristic.writeType}\\n" +
                "SIZE: ${data.size}\\n" +
                "DATA: ${toHex(data)}"
        )

        val success =
            bluetoothGatt
                ?.writeCharacteristic(
                    characteristic
                )
                ?: false

        onDebug(
            ">>> FE95 WRITE RESULT: $success"
        )

        if (!success) {

            waitingForWrite = false
            waitingForChunkStartWrite = false
            outgoingChunkInFlight = false

            onError(
                "Scrittura FE95/52 fallita"
            )
        }
    }

    private fun handleIncomingPacket(
        data: ByteArray
    ) {

        if (data.size < 3) {
            onDebug(
                ">>> FE95 PACCHETTO TROPPO CORTO\n" +
                    "DATA: ${toHex(data)}"
            )
            return
        }

        val buffer =
            ByteBuffer
                .wrap(data)
                .order(ByteOrder.LITTLE_ENDIAN)

        val chunk =
            buffer.short.toInt() and 0xFFFF

        if (chunk != 0) {

            handleIncomingChunk(
                chunk,
                buffer
            )

        } else {

            handleIncomingAck(
                buffer
            )
        }
    }

    private fun handleIncomingChunk(
        chunk: Int,
        buffer: ByteBuffer
    ) {

        val payload =
            ByteArray(
                buffer.remaining()
            )

        buffer.get(payload)

        incomingChunks[chunk] = payload

        onDebug(
            ">>> XIAOMI CHUNK RICEVUTO $chunk\n" +
                "DATA: ${toHex(payload)}"
        )

        if (
            incomingChunkCount > 0 &&
            incomingChunks.size >=
            incomingChunkCount
        ) {

            val complete =
                assembleIncomingChunks()

            incomingChunks.clear()

            onDebug(
                ">>> XIAOMI PAYLOAD COMPLETO\n" +
                    "DATA: ${toHex(complete)}"
            )

            handleIncomingCompletePayload(
                complete
            )
        }
    }

    private fun handleIncomingCompletePayload(
        payload: ByteArray
    ) {

        if (payload.isEmpty()) {
            return
        }

        handleXiaomiCommand(
            payload
        )
    }

    private fun assembleIncomingChunks():
        ByteArray {

        if (incomingChunks.isEmpty()) {
            return ByteArray(0)
        }

        val output =
            ArrayList<Byte>()

        val maxChunk =
            incomingChunks.keys.maxOrNull()
                ?: return ByteArray(0)

        for (index in 1..maxChunk) {

            val chunk =
                incomingChunks[index]
                    ?: return ByteArray(0)

            for (value in chunk) {
                output.add(value)
            }
        }

        return ByteArray(output.size) {
            output[it]
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendChunkEndAck() {

        val characteristic =
            writeCharacteristic
                ?: return

        val ack =
            byteArrayOf(
                0x00,
                0x00,
                0x01,
                0x00
            )

        writeRaw(
            characteristic,
            ack
        )
    }

    private fun handleIncomingAck(
        buffer: ByteBuffer
    ) {

        if (!buffer.hasRemaining()) {
            return
        }

        val subtype =
            buffer.get().toInt() and 0xFF

        when (subtype) {

            1 -> {

                onDebug(
                    ">>> XIAOMI CHUNK START ACK RICEVUTO"
                )

                waitingForChunkStartAck = true
                waitingForWrite = false
                waitingForChunkStartWrite = false
                outgoingChunkInFlight = false

                sendNextOutgoingChunk()
            }

            0 -> {

                onDebug(
                    ">>> XIAOMI CHUNK END ACK RICEVUTO"
                )
            }

            2 -> {

                onError(
                    "Xiaomi CHUNK NACK ricevuto"
                )
            }

            else -> {

                onDebug(
                    ">>> XIAOMI CHUNK ACK TYPE=$subtype"
                )
            }
        }
    }

    private fun handleIncomingSingleCommand(
        buffer: ByteBuffer
    ) {

        if (!buffer.hasRemaining()) {
            return
        }

        val encryption =
            buffer.get().toInt() and 0xFF

        val payload =
            ByteArray(
                buffer.remaining()
            )

        buffer.get(payload)

        onDebug(
            ">>> XIAOMI SINGLE COMMAND\n" +
                "ENCRYPTION: $encryption\n" +
                "DATA: ${toHex(payload)}"
        )

        handleXiaomiCommand(
            payload
        )
    }

    private fun handleXiaomiCommand(
        payload: ByteArray
    ) {

        val protocol =
            authProtocol
                ?: return

        val result =
            protocol.handleCommand(
                payload
            )

        when (result) {

            is XiaomiAuthProtocol.AuthResult.AuthCommand -> {

                onDebug(
                    ">>> WATCH NONCE RICEVUTO\n" +
                        ">>> WATCH HMAC VERIFICATO"
                )

                onDebug(
                    ">>> AUTH STEP 2 INVIATO"
                )

                sendChunked(
                    result.command
                )
            }

            is XiaomiAuthProtocol.AuthResult.Authenticated -> {

                onDebug(
                    ">>> XIAOMI AUTH OK"
                )
            }

            is XiaomiAuthProtocol.AuthResult.Error -> {

                onError(
                    "Xiaomi authentication: " +
                        result.message
                )
            }

            XiaomiAuthProtocol.AuthResult.Ignored -> {
                onDebug(
                    ">>> XIAOMI COMANDO IGNORATO"
                )
            }
        }
    }

    private val gattCallback =
        object : BluetoothGattCallback() {

            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int
            ) {

                when (newState) {

                    BluetoothProfile.STATE_CONNECTED -> {

                        bluetoothGatt = gatt

                        onDebug(
                            ">>> GATT CONNESSA\n" +
                                "Avvio service discovery..."
                        )

                        if (
                            !gatt.discoverServices()
                        ) {

                            onError(
                                "Avvio service discovery fallito"
                            )
                        }
                    }

                    BluetoothProfile.STATE_DISCONNECTED -> {

                        if (
                            bluetoothGatt == gatt
                        ) {
                            bluetoothGatt = null
                        }

                        gatt.close()

                        onDisconnected()
                    }
                }

                if (
                    status !=
                    BluetoothGatt.GATT_SUCCESS &&
                    newState !=
                    BluetoothProfile.STATE_DISCONNECTED
                ) {

                    onError(
                        "Errore GATT: status=$status"
                    )
                }
            }

            @SuppressLint("MissingPermission")
            override fun onServicesDiscovered(
                gatt: BluetoothGatt,
                status: Int
            ) {

                if (
                    status !=
                    BluetoothGatt.GATT_SUCCESS
                ) {

                    onError(
                        "Service discovery fallita: " +
                            "status=$status"
                    )

                    return
                }

                onConnected()

                onDebug(
                    ">>> SERVICE DISCOVERY COMPLETATA"
                )

                configureFe95(gatt)

                onServicesDiscovered(gatt)
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int
            ) {

                if (
                    descriptor.uuid !=
                    CLIENT_CONFIG
                ) {
                    return
                }

                if (
                    status ==
                    BluetoothGatt.GATT_SUCCESS
                ) {

                    onDebug(
                        ">>> NOTIFY FE95/51 ABILITATA"
                    )

                    startAuthentication()

                } else {

                    onError(
                        "CCCD FE95/51 fallito: " +
                            "status=$status"
                    )
                }
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic:
                    BluetoothGattCharacteristic,
                status: Int
            ) {

                if (
                    characteristic.uuid !=
                    FE95_WRITE
                ) {
                    return
                }

                /*
                 * La scrittura del CHUNK START NON è
                 * un chunk dati. Dopo questa callback
                 * dobbiamo aspettare l'ACK della Band.
                 */
                if (waitingForChunkStartWrite) {

                    waitingForChunkStartWrite = false
                    waitingForWrite = false

                    if (
                        status !=
                        BluetoothGatt.GATT_SUCCESS
                    ) {

                        onError(
                            "FE95/52 CHUNK START fallito: " +
                                "status=$status"
                        )

                    } else {

                        onDebug(
                            ">>> XIAOMI CHUNK START INVIATO, " +
                                "ATTESA ACK"
                        )
                    }

                    return
                }

                /*
                 * Solo la scrittura di un vero chunk
                 * modifica outgoingChunkIndex.
                 */
                if (!outgoingChunkInFlight) {
                    return
                }

                outgoingChunkInFlight = false
                waitingForWrite = false

                if (
                    status !=
                    BluetoothGatt.GATT_SUCCESS
                ) {

                    onError(
                        "FE95/52 write fallita: " +
                            "status=$status"
                    )

                    return
                }

                if (
                    waitingForChunkStartAck &&
                    outgoingChunkIndex <
                    outgoingChunks.size
                ) {

                    outgoingChunkIndex++

                    if (
                        outgoingChunkIndex <
                        outgoingChunks.size
                    ) {

                        sendNextOutgoingChunk()

                    } else {

                        onDebug(
                            ">>> TUTTI I CHUNK INVIATI"
                        )
                    }
                }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic:
                    BluetoothGattCharacteristic
            ) {

                if (
                    characteristic.uuid !=
                    FE95_READ
                ) {
                    return
                }

                val data =
                    characteristic.value

                onDebug(
                    ">>> NOTIFICA FE95/51\n" +
                        "DATA: ${toHex(data)}"
                )

                handleIncomingPacket(
                    data
                )
            }
        }

    private fun toHex(
        data: ByteArray
    ): String {

        if (data.isEmpty()) {
            return "(vuota)"
        }

        return data.joinToString(" ") {
            "%02X".format(
                it.toInt() and 0xFF
            )
        }
    }
}
