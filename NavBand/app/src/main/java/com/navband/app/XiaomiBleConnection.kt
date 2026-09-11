package com.navband.app

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.content.Context

class XiaomiBleConnection(
    private val context: Context,
    private val onConnected: () -> Unit = {},
    private val onDisconnected: () -> Unit = {},
    private val onServicesDiscovered: (BluetoothGatt) -> Unit = {},
    private val onError: (String) -> Unit = {},
    private val onDebug: (String) -> Unit = {}
) {

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

            override fun onServicesDiscovered(
                gatt: BluetoothGatt,
                status: Int
            ) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    onError(
                        "Service discovery fallita: status=$status"
                    )
                    return
                }

                onConnected()

                val result =
                    StringBuilder()

                result.append(
                    ">>> SERVIZI XIAOMI IDENTIFICATI\n\n"
                )

                var foundXiaomiService = false

                for (service in gatt.services) {

                    val uuid =
                        service.uuid.toString()

                    val isFdab =
                        uuid.startsWith(
                            "0000fdab-",
                            ignoreCase = true
                        )

                    val isFe95 =
                        uuid.startsWith(
                            "0000fe95-",
                            ignoreCase = true
                        )

                    if (!isFdab && !isFe95) {
                        continue
                    }

                    foundXiaomiService = true

                    result.append(
                        "SERVICE "
                    )

                    result.append(
                        if (isFdab) {
                            "FDAB"
                        } else {
                            "FE95"
                        }
                    )

                    result.append(
                        "\n"
                    )

                    result.append(
                        uuid
                    )

                    result.append(
                        "\n\n"
                    )

                    for (
                        characteristic
                        in service.characteristics
                    ) {

                        result.append(
                            "CHAR\n"
                        )

                        result.append(
                            characteristic.uuid
                        )

                        result.append(
                            "\n"
                        )

                        result.append(
                            "properties="
                        )

                        result.append(
                            characteristic.properties
                        )

                        result.append(
                            "\n"
                        )

                        result.append(
                            "permissions="
                        )

                        result.append(
                            propertyNames(
                                characteristic
                            )
                        )

                        result.append(
                            "\n\n"
                        )
                    }

                    result.append(
                        "\n"
                    )
                }

                if (!foundXiaomiService) {
                    result.append(
                        "Nessun servizio Xiaomi FDAB/FE95 trovato.\n"
                    )
                }

                onDebug(
                    result.toString()
                )

                onServicesDiscovered(gatt)
            }
        }

    private fun propertyNames(
        characteristic: BluetoothGattCharacteristic
    ): String {

        val properties =
            characteristic.properties

        val names =
            ArrayList<String>()

        if (
            properties and
            BluetoothGattCharacteristic.PROPERTY_READ
            != 0
        ) {
            names.add("READ")
        }

        if (
            properties and
            BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
            != 0
        ) {
            names.add("WRITE_NO_RESPONSE")
        }

        if (
            properties and
            BluetoothGattCharacteristic.PROPERTY_WRITE
            != 0
        ) {
            names.add("WRITE")
        }

        if (
            properties and
            BluetoothGattCharacteristic.PROPERTY_NOTIFY
            != 0
        ) {
            names.add("NOTIFY")
        }

        if (
            properties and
            BluetoothGattCharacteristic.PROPERTY_INDICATE
            != 0
        ) {
            names.add("INDICATE")
        }

        if (
            properties and
            BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE
            != 0
        ) {
            names.add("SIGNED_WRITE")
        }

        if (
            properties and
            BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS
            != 0
        ) {
            names.add("EXTENDED_PROPS")
        }

        if (names.isEmpty()) {
            return "NONE"
        }

        return names.joinToString(
            separator = " + "
        )
    }
}
