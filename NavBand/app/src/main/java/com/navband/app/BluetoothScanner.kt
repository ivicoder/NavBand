package com.navband.app

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

object BluetoothScanner {

    private const val SCAN_TIME_MS = 10_000L

    @SuppressLint("MissingPermission")
    fun scan(
        context: Context,
        onDevice: (String) -> Unit
    ): Boolean {

        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        val manager =
            context.getSystemService(
                Context.BLUETOOTH_SERVICE
            ) as BluetoothManager

        val adapter =
            manager.adapter ?: return false

        if (!adapter.isEnabled) {
            return false
        }

        var connectionStarted = false
        var xiaomiConnection: XiaomiBleConnection? = null

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

                    val deviceInfo =
                        "$name\n${device.address}\nRSSI: $rssi"

                    Handler(
                        Looper.getMainLooper()
                    ).post {
                        onDevice(deviceInfo)
                    }

                    if (
                        !connectionStarted &&
                        name.contains(
                            "Xiaomi Smart Band 8",
                            ignoreCase = true
                        )
                    ) {

                        connectionStarted = true

                        Handler(
                            Looper.getMainLooper()
                        ).post {

                            onDevice(
                                ">>> MI BAND 8 RILEVATA\n" +
                                    "Connessione GATT in corso..."
                            )

                            xiaomiConnection =
                                XiaomiBleConnection(
                                    context = context,

                                    onConnected = {
                                        onDevice(
                                            ">>> GATT CONNESSA\n" +
                                                "Service discovery completata"
                                        )
                                    },

                                    onDisconnected = {
                                        onDevice(
                                            ">>> GATT DISCONNESSA"
                                        )
                                    },

                                    onServicesDiscovered = { gatt ->

                                        val result =
                                            StringBuilder()

                                        result.append(
                                            ">>> SERVIZI GATT TROVATI\n\n"
                                        )

                                        for (
                                            service in
                                            gatt.services
                                        ) {

                                            result.append(
                                                "SERVICE\n"
                                            )

                                            result.append(
                                                service.uuid
                                            )

                                            result.append(
                                                "\n"
                                            )

                                            for (
                                                characteristic in
                                                service.characteristics
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
                                                    characteristic
                                                        .properties
                                                )

                                                result.append(
                                                    "\n\n"
                                                )
                                            }

                                            result.append(
                                                "\n"
                                            )
                                        }

                                        onDevice(
                                            result.toString()
                                        )
                                    },

                                    onError = { error ->
                                        onDevice(
                                            ">>> ERRORE GATT\n" +
                                                error
                                        )
                                    },

                                    onDebug = { debug ->
                                        onDevice(debug)
                                    }
                                )

                            xiaomiConnection?.connect(device)
                        }
                    }
                }
            }

        adapter.startLeScan(callback)

        Handler(
            Looper.getMainLooper()
        ).postDelayed(
            {
                try {
                    adapter.stopLeScan(callback)
                } catch (_: Exception) {
                }

                Handler(
                    Looper.getMainLooper()
                ).post {
                    onDevice(
                        ">>> SCANSIONE TERMINATA"
                    )
                }
            },
            SCAN_TIME_MS
        )

        return true
    }
}
