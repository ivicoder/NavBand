package com.navband.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun XiaomiAuthPanel(
    onSave: (String) -> Boolean
) {
    val context = LocalContext.current

    var authKey by remember {
        mutableStateOf("")
    }

    var showAuthKey by remember {
        mutableStateOf(false)
    }

    var message by remember {
        mutableStateOf(
            if (XiaomiAuthManager.hasAuthKey(context)) {
                "Auth key già configurata"
            } else {
                "Auth key non configurata"
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {

        Text("XIAOMI AUTHENTICATION")

        Text(
            "Inserisci la auth key Xiaomi di 32 caratteri."
        )

        OutlinedTextField(
            value = authKey,
            onValueChange = {
                authKey = it
                message = ""
            },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text("Auth key")
            },
            singleLine = true,
            visualTransformation =
                if (showAuthKey) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            Button(
                onClick = {
                    showAuthKey = !showAuthKey
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    if (showAuthKey) {
                        "Nascondi"
                    } else {
                        "Mostra"
                    }
                )
            }

            Button(
                onClick = {

                    if (onSave(authKey)) {
                        authKey = ""
                        showAuthKey = false
                        message =
                            "Auth key salvata correttamente"
                    } else {
                        message =
                            "Errore: servono esattamente 32 caratteri HEX"
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Salva")
            }
        }

        Text(message)
    }
}
