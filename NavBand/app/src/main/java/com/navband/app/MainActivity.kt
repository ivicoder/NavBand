package com.navband.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity :
    ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        if (
            Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }

        setContent {

            var vibrationEnabled by
                remember {
                    mutableStateOf(
                        NavBandPreferences
                            .roundaboutVibrationEnabled(
                                this
                            )
                    )
                }

            var pause by
                remember {
                    mutableFloatStateOf(
                        NavBandPreferences
                            .vibrationPauseMs(this)
                            .toFloat()
                    )
                }

            var transport by
                remember {
                    mutableStateOf(
                        NavBandPreferences
                            .transport(this)
                    )
                }

            var info by
                remember {
                    mutableStateOf(false)
                }

            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(20.dp),

                verticalArrangement =
                    Arrangement.spacedBy(14.dp)
            ) {

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),

                    horizontalArrangement =
                        Arrangement.SpaceBetween,

                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Column {

                        Text("NavBand")

                        Text(
                            "Navigazione per Mi Band 8"
                        )
                    }

                    Text("● ON")
                }

                Card(
                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    Column(
                        modifier =
                            Modifier.padding(16.dp)
                    ) {

                        Text("DISPOSITIVO")

                        Row(
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {

                            RadioButton(
                                selected =
                                    transport ==
                                        "Mi Fitness",

                                onClick = {

                                    transport =
                                        "Mi Fitness"

                                    NavBandPreferences
                                        .setTransport(
                                            this@MainActivity,
                                            "Mi Fitness"
                                        )
                                }
                            )

                            Text("Mi Fitness")
                        }

                        Row(
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {

                            RadioButton(
                                selected =
                                    transport ==
                                        "Notify for Xiaomi",

                                onClick = {

                                    transport =
                                        "Notify for Xiaomi"

                                    NavBandPreferences
                                        .setTransport(
                                            this@MainActivity,
                                            "Notify for Xiaomi"
                                        )
                                }
                            )

                            Text("Notify for Xiaomi")
                        }
                    }
                }

                Card(
                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    Column(
                        modifier =
                            Modifier.padding(16.dp)
                    ) {

                        Text(
                            "APP DI NAVIGAZIONE"
                        )

                        Text("✓ Google Maps")
                        Text("✓ Waze")
                    }
                }

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),

                    horizontalArrangement =
                        Arrangement.SpaceBetween,

                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Text("ROTATORIE")

                    TextButton(
                        onClick = {
                            info = true
                        }
                    ) {
                        Text("ⓘ")
                    }
                }

                Card(
                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    Column(
                        modifier =
                            Modifier.padding(16.dp)
                    ) {

                        Row(
                            modifier =
                                Modifier.fillMaxWidth(),

                            horizontalArrangement =
                                Arrangement.SpaceBetween,

                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {

                            Text(
                                "Vibrazione per uscita"
                            )

                            Switch(
                                checked =
                                    vibrationEnabled,

                                onCheckedChange = {

                                    vibrationEnabled =
                                        it

                                    NavBandPreferences
                                        .setRoundaboutVibration(
                                            this@MainActivity,
                                            it
                                        )
                                }
                            )
                        }

                        Spacer(
                            Modifier.height(8.dp)
                        )

                        Text(
                            "Pausa: " +
                                pause.toInt() +
                                " ms"
                        )

                        Slider(
                            value = pause,

                            onValueChange = {
                                pause = it
                            },

                            valueRange =
                                100f..1000f,

                            onValueChangeFinished = {

                                NavBandPreferences
                                    .setVibrationPause(
                                        this@MainActivity,
                                        pause.toLong()
                                    )
                            }
                        )
                    }
                }

                Card(
                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    Column(
                        modifier =
                            Modifier.padding(16.dp)
                    ) {

                        Text(
                            "CONTENUTO SULLA BAND"
                        )

                        Text(
                            "✓ Freccia / manovra"
                        )

                        Text(
                            "✓ Distanza"
                        )

                        Text(
                            "✓ Immagine quando disponibile"
                        )
                    }
                }

                Spacer(
                    Modifier.height(4.dp)
                )

                Button(
                    modifier =
                        Modifier.fillMaxWidth(),

                    onClick = {

                        startActivity(
                            Intent(
                                Settings
                                    .ACTION_NOTIFICATION_LISTENER_SETTINGS
                            )
                        )
                    }
                ) {

                    Text(
                        "ATTIVA ACCESSO ALLE NOTIFICHE"
                    )
                }
            }

            if (info) {

                AlertDialog(

                    onDismissRequest = {
                        info = false
                    },

                    title = {
                        Text(
                            "Vibrazione rotatorie"
                        )
                    },

                    text = {

                        Column {

                            Text(
                                "La Band indica quale uscita " +
                                    "prendere attraverso il numero " +
                                    "di vibrazioni:"
                            )

                            Spacer(
                                Modifier.height(12.dp)
                            )

                            Text("1ª uscita   •")
                            Text("2ª uscita   • •")
                            Text("3ª uscita   • • •")
                            Text("4ª uscita   • • • •")

                            Spacer(
                                Modifier.height(12.dp)
                            )

                            Text(
                                "La pausa tra le vibrazioni " +
                                    "è configurabile."
                            )
                        }
                    },

                    confirmButton = {

                        TextButton(
                            onClick = {
                                info = false
                            }
                        ) {
                            Text("CHIUDI")
                        }
                    }
                )
            }
        }
    }
}
