package com.navband.app

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
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
    }

    private var bluetoothGatt: BluetoothGatt? = null

    fun connect(device: BluetoothDevice) {
        disconnect()
        bluetoothGatt = connectGatt(device)
    }

    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
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
            onError(
                "Servizio FE95 non trovato"
            )
            return
        }

        val readCharacteristic =
            service.getCharacteristic(FE95_READ)

        val writeCharacteristic =
            service.getCharacteristic(FE95_WRITE)

        if (readCharacteristic == null) {
            onError(
                "Caratteristica FE95/51 non trovata"
            )
            return
        }

        if (writeCharacteristic == null) {
            onError(
                "Caratteristica FE95/52 non trovata"
            )
            return
        }

        onDebug(
            ">>> CANALE XIAOMI FE95 IDENTIFICATO\n" +
                "READ/NOTIFY: ${readCharacteristic.uuid}\n" +
                "WRITE: ${writeCharacteristic.uuid}"
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

                        if (!gatt.discoverServices()) {
                            onError(
                                "Avvio service discovery fallito"
                            )
                        }
                    }

                    BluetoothProfile.STATE_DISCONNECTED -> {

                        if (bluetoothGatt == gatt) {
                            bluetoothGatt = null
                        }

                        gatt.close()

                        onDisconnected()
                    }
                }

                if (
                    status != BluetoothGatt.GATT_SUCCESS &&
                    newState != BluetoothProfile.STATE_DISCONNECTED
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
                        "Service discovery fallita: status=$status"
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
                    descriptor.uuid == CLIENT_CONFIG
                ) {

                    if (
                        status ==
                        BluetoothGatt.GATT_SUCCESS
                    ) {

                        onDebug(
                            ">>> NOTIFY FE95/51 ABILITATA"
                        )

                    } else {

                        onError(
                            "CCCD FE95/51 fallito: status=$status"
                        )
                    }
                }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {

                if (
                    characteristic.uuid != FE95_READ
                ) {
                    return
                }

                val data =
                    characteristic.value

                onDebug(
                    ">>> NOTIFICA FE95/51\n" +
                        "DATA: ${toHex(data)}"
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
