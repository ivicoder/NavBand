package com.navband.app

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

object BluetoothScanner {

    @SuppressLint("MissingPermission")
    fun scan(context: Context, onDevice: (String) -> Unit): Boolean {

        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        val manager =
            context.getSystemService(
                Context.BLUETOOTH_SERVICE
            ) as BluetoothManager

        val adapter: BluetoothAdapter =
            manager.adapter ?: return false

        if (!adapter.isEnabled) {
            return false
        }

        val callback =
            object : BluetoothAdapter.LeScanCallback {

                override fun onLeScan(
                    device: BluetoothDevice,
                    rssi: Int,
                    scanRecord: ByteArray
                ) {
                    val name =
                        try {
                            device.name ?: "(senza nome)"
                        } catch (_: SecurityException) {
                            "(nome non disponibile)"
                        }

                    onDevice(
                        "$name\n${device.address}\nRSSI: $rssi"
                    )
                }
            }

        adapter.startLeScan(callback)

        android.os.Handler(
            android.os.Looper.getMainLooper()
        ).postDelayed(
            {
                try {
                    adapter.stopLeScan(callback)
                } catch (_: Exception) {
                }
            },
            10_000L
        )

        return true
    }
}
