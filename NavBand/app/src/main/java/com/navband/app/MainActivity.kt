package com.navband.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestNotificationPermission()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp)
                            .verticalScroll(
                                rememberScrollState()
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {

                        Text(
                            text = "NavBand",
                            style = MaterialTheme.typography.headlineLarge
                        )

                        Text(
                            text = "NavBand è attivo"
                        )

                        Button(
                            onClick = {
                                recreate()
                            }
                        ) {
                            Text("Aggiorna debug")
                        }

                        /*
                         * TEST VIBRAZIONI
                         */

                        Text(
                            text = "TEST NOTIFICA",
                            style = MaterialTheme.typography.titleLarge
                        )

                        Text(
                            text = "Invia una vera notifica Android di NavBand"
                        )

                        Button(
                            onClick = {
                                testNotification()
                            }
                        ) {
                            Text("Test notifica ← Sinistra")
                        }

                        Text(
                            text = "TEST VIBRAZIONI",
                            style = MaterialTheme.typography.titleLarge
                        )

                        Text(
                            text = "Testa direttamente il VibrationEngine"
                        )

                        VibrationButtonRow(
                            leftText = "← Sinistra",
                            rightText = "Destra →",
                            onLeft = {
                                testVibration(
                                    NavigationDirection.LEFT
                                )
                            },
                            onRight = {
                                testVibration(
                                    NavigationDirection.RIGHT
                                )
                            }
                        )

                        VibrationButtonRow(
                            leftText = "↙ Leggera",
                            rightText = "Leggera ↘",
                            onLeft = {
                                testVibration(
                                    NavigationDirection.SLIGHT_LEFT
                                )
                            },
                            onRight = {
                                testVibration(
                                    NavigationDirection.SLIGHT_RIGHT
                                )
                            }
                        )

                        VibrationButtonRow(
                            leftText = "↑ Dritto",
                            rightText = "↩ Inversione",
                            onLeft = {
                                testVibration(
                                    NavigationDirection.STRAIGHT
                                )
                            },
                            onRight = {
                                testVibration(
                                    NavigationDirection.U_TURN
                                )
                            }
                        )

                        Text(
                            text = "TEST ROTATORIA",
                            style = MaterialTheme.typography.titleLarge
                        )

                        Text(
                            text = "Il numero di impulsi corrisponde all'uscita"
                        )

                        VibrationButtonRow(
                            leftText = "Rotatoria 1",
                            rightText = "Rotatoria 2",
                            onLeft = {
                                testRoundabout(1)
                            },
                            onRight = {
                                testRoundabout(2)
                            }
                        )

                        VibrationButtonRow(
                            leftText = "Rotatoria 3",
                            rightText = "Rotatoria 4",
                            onLeft = {
                                testRoundabout(3)
                            },
                            onRight = {
                                testRoundabout(4)
                            }
                        )

                        VibrationButtonRow(
                            leftText = "Rotatoria 5",
                            rightText = "Rotatoria 6",
                            onLeft = {
                                testRoundabout(5)
                            },
                            onRight = {
                                testRoundabout(6)
                            }
                        )

                        /*
                         * DEBUG MAPS
                         */

                        val debugImage =
                            try {
                                openFileInput(
                                    "debug_navigation_image.png"
                                ).use { input ->
                                    BitmapFactory.decodeStream(input)
                                }
                            } catch (_: Exception) {
                                null
                            }

                        if (debugImage != null) {

                            Text(
                                text = "IMMAGINE ESTRATTA",
                                style = MaterialTheme.typography.titleMedium
                            )

                            Image(
                                bitmap = debugImage.asImageBitmap(),
                                contentDescription =
                                    "Immagine estratta da Google Maps",
                                modifier = Modifier
                                    .background(Color.Black)
                                    .padding(10.dp)
                            )

                        } else {

                            Text(
                                text = "Nessuna immagine estratta",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Text(
                            text = NavigationNotificationListener
                                .getDebug(this@MainActivity),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (
                checkSelfPermission(
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(
                        Manifest.permission.POST_NOTIFICATIONS
                    ),
                    REQUEST_NOTIFICATION_PERMISSION
                )
            }
        }
    }

    private fun testNotification() {
        val bitmap =
            Bitmap.createBitmap(
                128,
                128,
                Bitmap.Config.ARGB_8888
            )

        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.WHITE
        paint.strokeWidth = 14f
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.SQUARE

        canvas.drawLine(30f, 64f, 98f, 64f, paint)
        canvas.drawLine(30f, 64f, 58f, 36f, paint)
        canvas.drawLine(30f, 64f, 58f, 92f, paint)

        val event =
            NavigationEvent(
                direction = NavigationDirection.LEFT,
                distance = "100 m",
                instruction = "TEST FRECCIA SINISTRA",
                subText = "Bitmap 128x128",
                image = bitmap
            )

        NotificationForwarder(this).send(event)
    }

    private fun testVibration(
        direction: NavigationDirection
    ) {
        val event =
            NavigationEvent(
                direction = direction
            )

        VibrationEngine.vibrate(
            context = this,
            event = event
        )
    }

    private fun testRoundabout(
        exit: Int
    ) {
        val event =
            NavigationEvent(
                direction = NavigationDirection.ROUNDABOUT,
                roundaboutExit = exit
            )

        VibrationEngine.vibrate(
            context = this,
            event = event
        )
    }

    companion object {
        private const val REQUEST_NOTIFICATION_PERMISSION = 1001
    }
}

@androidx.compose.runtime.Composable
private fun VibrationButtonRow(
    leftText: String,
    rightText: String,
    onLeft: () -> Unit,
    onRight: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onLeft,
            modifier = Modifier.weight(1f)
        ) {
            Text(leftText)
        }

        Button(
            onClick = onRight,
            modifier = Modifier.weight(1f)
        ) {
            Text(rightText)
        }
    }
}
