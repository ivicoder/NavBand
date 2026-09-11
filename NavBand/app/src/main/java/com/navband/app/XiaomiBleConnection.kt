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
    private val onError: (String) -> Unit = {}
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
                onServicesDiscovered(gatt)
            }
        }
}
