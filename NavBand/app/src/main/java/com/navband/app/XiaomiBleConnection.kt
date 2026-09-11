package com.navband.app

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
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
                        "\n"
                    )

                    for (
                        characteristic
                        in service.characteristics
                    ) {

                        result.append(
                            "  CHAR\n"
                        )

                        result.append(
                            characteristic.uuid
                        )

                        result.append(
                            "\n"
                        )

                        result.append(
                            "  properties="
                        )

                        result.append(
                            characteristic.properties
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
}
