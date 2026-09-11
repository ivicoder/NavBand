package com.navband.app

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothProfile
import android.content.Context

class XiaomiBleConnection(
    private val context: Context
) {

    private var bluetoothGatt: BluetoothGatt? = null

    fun connect(device: BluetoothDevice) {
        disconnect()

        bluetoothGatt =
            connectGatt(device)
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
            gattCallback
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

                        gatt.discoverServices()
                    }

                    BluetoothProfile.STATE_DISCONNECTED -> {
                        if (bluetoothGatt == gatt) {
                            bluetoothGatt = null
                        }

                        gatt.close()
                    }
                }
            }

            override fun onServicesDiscovered(
                gatt: BluetoothGatt,
                status: Int
            ) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    return
                }

                // I servizi BLE del dispositivo sono ora disponibili.
                // L'autenticazione Xiaomi verrà aggiunta nel passo successivo.
            }
        }
}
