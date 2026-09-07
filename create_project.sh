#!/usr/bin/env bash

set -e

PROJECT="NavBand"

echo "=============================================="
echo "          NavBand V1 Project Creator"
echo "=============================================="
echo

if [ -d "$PROJECT" ]; then
    echo "ERRORE: la cartella '$PROJECT' esiste gia'."
    echo "Eliminala prima di eseguire nuovamente lo script."
    exit 1
fi

echo "[1/9] Creazione struttura..."

mkdir -p "$PROJECT/.github/workflows"
mkdir -p "$PROJECT/app/src/main/java/com/navband/app"
mkdir -p "$PROJECT/app/src/main/res/drawable"
mkdir -p "$PROJECT/app/src/main/res/values"
mkdir -p "$PROJECT/app/src/main/res/xml"

cd "$PROJECT"

echo "[2/9] File Gradle..."

cat > settings.gradle.kts <<'EOF'
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(
        RepositoriesMode.FAIL_ON_PROJECT_REPOS
    )

    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "NavBand"

include(":app")
EOF

cat > build.gradle.kts <<'EOF'
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
EOF

cat > gradle.properties <<'EOF'
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
EOF

cat > app/build.gradle.kts <<'EOF'
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.navband.app"

    compileSdk = 35

    defaultConfig {
        applicationId = "com.navband.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false

            proguardFiles(
                getDefaultProguardFile(
                    "proguard-android-optimize.txt"
                ),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {

    implementation(
        "androidx.core:core-ktx:1.15.0"
    )

    implementation(
        "androidx.activity:activity-compose:1.10.1"
    )

    implementation(
        "androidx.compose.ui:ui:1.7.8"
    )

    implementation(
        "androidx.compose.ui:ui-tooling-preview:1.7.8"
    )

    implementation(
        "androidx.compose.material3:material3:1.3.1"
    )

    debugImplementation(
        "androidx.compose.ui:ui-tooling:1.7.8"
    )
}
EOF

cat > app/proguard-rules.pro <<'EOF'
# NavBand V1
EOF

echo "[3/9] Modello navigazione..."

cat > app/src/main/java/com/navband/app/NavigationEvent.kt <<'EOF'
package com.navband.app

import android.graphics.Bitmap

enum class NavigationDirection {
    LEFT,
    RIGHT,
    SLIGHT_LEFT,
    SLIGHT_RIGHT,
    STRAIGHT,
    U_TURN,
    ROUNDABOUT,
    UNKNOWN
}

data class NavigationEvent(
    val direction: NavigationDirection,
    val distance: String = "",
    val instruction: String = "",
    val roundaboutExit: Int? = null,
    val image: Bitmap? = null
)
EOF

echo "[4/9] Parser Google Maps / Waze..."

cat > app/src/main/java/com/navband/app/NavigationParser.kt <<'EOF'
package com.navband.app

import android.graphics.Bitmap
import java.util.Locale

object NavigationParser {

    private val distanceRegex =
        Regex(
            """(?i)\b\d+(?:[.,]\d+)?\s*(?:m|km|ft|mi)\b"""
        )

    private val numericExitRegex =
        Regex(
            """(?i)(?:uscita|exit|salida|sortie)\s*(?:n\.?|numero)?\s*(\d+)"""
        )

    private val reverseExitRegex =
        Regex(
            """(?i)\b(\d+)\s*(?:ª|a|°|º)?\s*(?:uscita|exit)\b"""
        )

    private val ordinalExitRegex =
        Regex(
            """(?i)\b(prima|seconda|terza|quarta|quinta|sesta|settima|ottava)\s+(?:uscita|exit)\b"""
        )

    fun parse(
        title: String?,
        text: String?,
        subText: String?,
        image: Bitmap? = null
    ): NavigationEvent? {

        val source =
            listOf(title, text, subText)
                .filterNotNull()
                .joinToString(" ")
                .trim()

        if (source.isBlank()) {
            return null
        }

        val normalized =
            source
                .lowercase(Locale.ITALIAN)
                .replace("’", "'")
                .replace("º", "°")

        val distance =
            distanceRegex
                .find(source)
                ?.value
                ?: ""

        val exit =
            findExit(normalized)

        val isRoundabout =
            containsAny(
                normalized,
                "rotatoria",
                "rotonda",
                "roundabout",
                "round about",
                "alla rotonda",
                "in rotatoria"
            )

        val direction =
            when {

                isRoundabout ->
                    NavigationDirection.ROUNDABOUT

                containsAny(
                    normalized,
                    "inversione a u",
                    "inversione",
                    "u-turn",
                    "u turn",
                    "fai inversione"
                ) ->
                    NavigationDirection.U_TURN

                containsAny(
                    normalized,
                    "svolta a sinistra",
                    "gira a sinistra",
                    "turn left",
                    "left turn",
                    "a sinistra"
                ) ->
                    NavigationDirection.LEFT

                containsAny(
                    normalized,
                    "svolta a destra",
                    "gira a destra",
                    "turn right",
                    "right turn",
                    "a destra"
                ) ->
                    NavigationDirection.RIGHT

                containsAny(
                    normalized,
                    "leggermente a sinistra",
                    "mantieni la sinistra",
                    "tieni la sinistra",
                    "slight left"
                ) ->
                    NavigationDirection.SLIGHT_LEFT

                containsAny(
                    normalized,
                    "leggermente a destra",
                    "mantieni la destra",
                    "tieni la destra",
                    "slight right"
                ) ->
                    NavigationDirection.SLIGHT_RIGHT

                containsAny(
                    normalized,
                    "prosegui",
                    "continua dritto",
                    "vai dritto",
                    "straight",
                    "keep straight"
                ) ->
                    NavigationDirection.STRAIGHT

                else ->
                    NavigationDirection.UNKNOWN
            }

        return NavigationEvent(
            direction = direction,
            distance = distance,
            instruction = source,
            roundaboutExit = exit,
            image = image
        )
    }

    private fun findExit(
        text: String
    ): Int? {

        numericExitRegex
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.let {
                return it
            }

        reverseExitRegex
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.let {
                return it
            }

        val ordinal =
            ordinalExitRegex
                .find(text)
                ?.groupValues
                ?.getOrNull(1)

        return when (ordinal) {
            "prima" -> 1
            "seconda" -> 2
            "terza" -> 3
            "quarta" -> 4
            "quinta" -> 5
            "sesta" -> 6
            "settima" -> 7
            "ottava" -> 8
            else -> null
        }
    }

    private fun containsAny(
        text: String,
        vararg values: String
    ): Boolean {
        return values.any {
            text.contains(it)
        }
    }
}
EOF

echo "[5/9] Estrazione immagini e preferenze..."

cat > app/src/main/java/com/navband/app/ImageExtractor.kt <<'EOF'
package com.navband.app

import android.app.Notification
import android.graphics.Bitmap
import android.os.Build

object ImageExtractor {

    fun extract(
        notification: Notification
    ): Bitmap? {

        val extras =
            notification.extras ?: return null

        if (Build.VERSION.SDK_INT >= 33) {

            extras.getParcelable(
                Notification.EXTRA_PICTURE,
                Bitmap::class.java
            )?.let {
                return it
            }

            extras.getParcelable(
                Notification.EXTRA_LARGE_ICON,
                Bitmap::class.java
            )?.let {
                return it
            }

        } else {

            @Suppress("DEPRECATION")
            val picture =
                extras.getParcelable(
                    Notification.EXTRA_PICTURE
                )

            if (picture is Bitmap) {
                return picture
            }

            @Suppress("DEPRECATION")
            val icon =
                extras.getParcelable(
                    Notification.EXTRA_LARGE_ICON
                )

            if (icon is Bitmap) {
                return icon
            }
        }

        return null
    }
}
EOF

cat > app/src/main/java/com/navband/app/NavBandPreferences.kt <<'EOF'
package com.navband.app

import android.content.Context

object NavBandPreferences {

    private const val PREFS = "navband"

    private const val KEY_ROUNDABOUT =
        "roundabout_vibration"

    private const val KEY_PAUSE =
        "vibration_pause"

    private const val KEY_TRANSPORT =
        "transport"

    fun roundaboutVibrationEnabled(
        context: Context
    ): Boolean {

        return context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .getBoolean(
                KEY_ROUNDABOUT,
                true
            )
    }

    fun setRoundaboutVibration(
        context: Context,
        enabled: Boolean
    ) {

        context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .edit()
            .putBoolean(
                KEY_ROUNDABOUT,
                enabled
            )
            .apply()
    }

    fun vibrationPauseMs(
        context: Context
    ): Long {

        return context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .getLong(
                KEY_PAUSE,
                300L
            )
    }

    fun setVibrationPause(
        context: Context,
        value: Long
    ) {

        context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .edit()
            .putLong(
                KEY_PAUSE,
                value
            )
            .apply()
    }

    fun transport(
        context: Context
    ): String {

        return context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .getString(
                KEY_TRANSPORT,
                "Mi Fitness"
            ) ?: "Mi Fitness"
    }

    fun setTransport(
        context: Context,
        value: String
    ) {

        context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .edit()
            .putString(
                KEY_TRANSPORT,
                value
            )
            .apply()
    }
}
EOF

echo "[6/9] Notification Listener..."

cat > app/src/main/java/com/navband/app/NavigationNotificationListener.kt <<'EOF'
package com.navband.app

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NavigationNotificationListener :
    NotificationListenerService() {

    private lateinit var forwarder:
        NotificationForwarder

    override fun onCreate() {

        super.onCreate()

        forwarder =
            NotificationForwarder(this)
    }

    override fun onNotificationPosted(
        sbn: StatusBarNotification
    ) {

        if (!isNavigationApp(sbn.packageName)) {
            return
        }

        val notification =
            sbn.notification ?: return

        val extras =
            notification.extras ?: return

        val title =
            extras
                .getCharSequence(
                    Notification.EXTRA_TITLE
                )
                ?.toString()

        val text =
            extras
                .getCharSequence(
                    Notification.EXTRA_TEXT
                )
                ?.toString()

        val subText =
            extras
                .getCharSequence(
                    Notification.EXTRA_SUB_TEXT
                )
                ?.toString()

        val image =
            ImageExtractor.extract(
                notification
            )

        val event =
            NavigationParser.parse(
                title = title,
                text = text,
                subText = subText,
                image = image
            ) ?: return

        forwarder.send(event)
    }

    private fun isNavigationApp(
        packageName: String
    ): Boolean {

        return packageName ==
            "com.google.android.apps.maps" ||
            packageName ==
            "com.waze"
    }
}
EOF

echo "[7/9] Forwarder..."

cat > app/src/main/java/com/navband/app/NotificationForwarder.kt <<'EOF'
package com.navband.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat

class NotificationForwarder(
    private val context: Context
) {

    companion object {

        private const val CHANNEL =
            "navband_navigation"

        private const val NOTIFICATION_ID =
            5000
    }

    private val manager =
        context.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager

    init {
        createChannel()
    }

    private fun createChannel() {

        if (Build.VERSION.SDK_INT >= 26) {

            val channel =
                NotificationChannel(
                    CHANNEL,
                    "NavBand navigazione",
                    NotificationManager.IMPORTANCE_LOW
                )

            channel.setSound(null, null)
            channel.enableVibration(false)

            manager.createNotificationChannel(
                channel
            )
        }
    }

    fun send(
        event: NavigationEvent
    ) {

        val arrow =
            when (event.direction) {

                NavigationDirection.LEFT -> "←"
                NavigationDirection.RIGHT -> "→"
                NavigationDirection.SLIGHT_LEFT -> "↖"
                NavigationDirection.SLIGHT_RIGHT -> "↗"
                NavigationDirection.STRAIGHT -> "↑"
                NavigationDirection.U_TURN -> "↶"
                NavigationDirection.ROUNDABOUT -> "⟳"
                NavigationDirection.UNKNOWN -> "•"
            }

        val title =
            if (
                event.direction ==
                    NavigationDirection.ROUNDABOUT &&
                event.roundaboutExit != null
            ) {
                "Rotatoria • " +
                    event.roundaboutExit +
                    "ª uscita"
            } else {
                "Navigazione"
            }

        val text =
            buildString {

                append(arrow)

                if (
                    event.distance.isNotBlank()
                ) {
                    append("   ")
                    append(event.distance)
                }

                if (
                    event.instruction.isNotBlank()
                ) {
                    append("\n")
                    append(event.instruction)
                }
            }

        val builder =
            NotificationCompat
                .Builder(
                    context,
                    CHANNEL
                )
                .setSmallIcon(
                    R.drawable.ic_navband
                )
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(
                    NotificationCompat
                        .BigTextStyle()
                        .bigText(text)
                )
                .setPriority(
                    NotificationCompat
                        .PRIORITY_LOW
                )
                .setOnlyAlertOnce(true)
                .setAutoCancel(false)
                .setCategory(
                    NotificationCompat
                        .CATEGORY_NAVIGATION
                )

        val image: Bitmap? =
            event.image

        if (image != null) {
            builder.setLargeIcon(image)
        }

        manager.notify(
            NOTIFICATION_ID,
            builder.build()
        )
    }
}
EOF

echo "[8/9] Interfaccia..."

cat > app/src/main/java/com/navband/app/MainActivity.kt <<'EOF'
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
EOF

echo "[9/9] Risorse, Manifest e GitHub Actions..."

cat > app/src/main/res/values/strings.xml <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">NavBand</string>
</resources>
EOF

cat > app/src/main/res/values/colors.xml <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="navband_blue">#1976D2</color>
</resources>
EOF

cat > app/src/main/res/values/themes.xml <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<resources>

    <style
        name="Theme.NavBand"
        parent="android:style/Theme.Material.Light.NoActionBar">

        <item name="android:fontFamily">sans</item>

        <item name="android:colorAccent">
            @color/navband_blue
        </item>

    </style>

</resources>
EOF

cat > app/src/main/res/drawable/ic_navband.xml <<'EOF'
<vector
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">

    <path
        android:fillColor="#FFFFFF"
        android:pathData="
        M12,2
        L4,10
        L4,13
        L10,13
        L10,22
        L14,22
        L14,13
        L20,13
        L20,10
        Z" />

</vector>
EOF

cat > app/src/main/res/xml/notification_listener_config.xml <<'EOF'
<?xml version="1.0" encoding="utf-8"?>

<notification-listener-filter
    xmlns:android="http://schemas.android.com/apk/res/android">
</notification-listener-filter>
EOF

cat > app/src/main/AndroidManifest.xml <<'EOF'
<?xml version="1.0" encoding="utf-8"?>

<manifest
    xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission
        android:name="android.permission.POST_NOTIFICATIONS" />

    <application
        android:allowBackup="true"
        android:label="NavBand"
        android:theme="@style/Theme.NavBand">

        <activity
            android:name=".MainActivity"
            android:exported="true">

            <intent-filter>

                <action
                    android:name="android.intent.action.MAIN" />

                <category
                    android:name="android.intent.category.LAUNCHER" />

            </intent-filter>

        </activity>

        <service
            android:name=".NavigationNotificationListener"
            android:label="NavBand"
            android:permission=
                "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
            android:exported="true">

            <intent-filter>

                <action
                    android:name=
                        "android.service.notification.NotificationListenerService" />

            </intent-filter>

            <meta-data
                android:name=
                    "android.service.notification"
                android:resource=
                    "@xml/notification_listener_config" />

        </service>

    </application>

</manifest>
EOF

cat > .gitignore <<'EOF'
.gradle/
.idea/
build/
*/build/
local.properties
*.iml
.DS_Store
.externalNativeBuild/
.cxx/
EOF

cat > README.md <<'EOF'
# NavBand

NavBand V1

Applicazione Android per intercettare le notifiche di
Google Maps e Waze e preparare informazioni di navigazione
per Xiaomi Smart Band 8.

## Funzioni

- Google Maps
- Waze
- NotificationListenerService
- direzione
- distanza
- rotatorie
- riconoscimento uscita
- vibrazione rotatorie configurabile
- Mi Fitness
- Notify for Xiaomi
- immagini quando disponibili
- interfaccia Jetpack Compose

## Compilazione

Il progetto viene compilato tramite GitHub Actions.

Aprire:

Actions -> Build NavBand APK

e scaricare l'artifact:

NavBand-debug

## Nota

La trasmissione alla Smart Band viene effettuata attraverso
il sistema di notifiche dell'app ponte selezionata.

La gestione specifica dei pattern di vibrazione di Notify
verrà perfezionata dopo il primo test reale sulla Band 8.
EOF

cat > .github/workflows/build.yml <<'EOF'
name: Build NavBand APK

on:
  workflow_dispatch:
  push:
    branches:
      - main

permissions:
  contents: read

jobs:

  build:

    runs-on: ubuntu-latest

    steps:

      - name: Checkout
        uses: actions/checkout@v4

      - name: Setup Java 17
        uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: '17'

      - name: Setup Android SDK
        uses: android-actions/setup-android@v3

      - name: Setup Gradle 8.9
        uses: gradle/actions/setup-gradle@v6
        with:
          gradle-version: '8.9'

      - name: Build debug APK
        run: gradle assembleDebug --stacktrace

      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: NavBand-debug
          path: app/build/outputs/apk/debug/app-debug.apk
EOF

echo
echo "=============================================="
echo "          NAVBAND V1 CREATA"
echo "=============================================="
echo
echo "Percorso:"
echo "$(pwd)"
echo
echo "Il progetto contiene:"
echo "- Kotlin"
echo "- Jetpack Compose"
echo "- Google Maps"
echo "- Waze"
echo "- parser rotatorie"
echo "- uscita 1/2/3/4..."
echo "- immagini notifiche"
echo "- Mi Fitness"
echo "- Notify for Xiaomi"
echo "- GitHub Actions"
echo
echo "=============================================="
echo "FINE"
echo "=============================================="
echo
