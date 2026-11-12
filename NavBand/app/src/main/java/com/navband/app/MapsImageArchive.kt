package com.navband.app

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps a small rolling archive of the exact bitmap extracted from Maps notifications.
 * Files stay private to NavBand; no storage permission or network access is required.
 */
object MapsImageArchive {
    private const val DIRECTORY = "maps_notification_images"
    private const val MAX_IMAGES = 30
    private val lock = Any()

    fun save(
        context: Context,
        bitmap: Bitmap,
        source: String,
        notificationKey: String,
        postTime: Long
    ): String? = synchronized(lock) {
        try {
            val directory = File(context.filesDir, DIRECTORY)
            if (!directory.exists() && !directory.mkdirs()) return null

            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.ITALY)
                .format(Date())
            val baseName = "maps_${stamp}_${postTime}"
            val imageFile = File(directory, "$baseName.png")
            val metadataFile = File(directory, "$baseName.txt")

            imageFile.outputStream().buffered().use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    imageFile.delete()
                    return null
                }
            }

            metadataFile.writeText(
                "CAPTURED_AT=${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ITALY).format(Date())}\n" +
                    "SOURCE=$source\n" +
                    "WIDTH=${bitmap.width}\n" +
                    "HEIGHT=${bitmap.height}\n" +
                    "CONFIG=${bitmap.config}\n" +
                    "NOTIFICATION_POST_TIME=$postTime\n" +
                    "NOTIFICATION_KEY=$notificationKey\n",
                Charsets.UTF_8
            )

            val images = directory.listFiles { file ->
                file.isFile && file.extension.equals("png", ignoreCase = true)
            }?.sortedByDescending { it.lastModified() }.orEmpty()

            images.drop(MAX_IMAGES).forEach { oldImage ->
                oldImage.delete()
                File(directory, oldImage.nameWithoutExtension + ".txt").delete()
            }
            baseName
        } catch (_: Exception) {
            null
        }
    }

    fun archiveFiles(context: Context): List<File> = synchronized(lock) {
        val directory = File(context.filesDir, DIRECTORY)
        directory.listFiles { file ->
            file.isFile && (file.extension.equals("png", true) || file.extension.equals("txt", true))
        }?.sortedBy { it.name }.orEmpty()
    }
}
