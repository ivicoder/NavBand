package com.navband.app

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView

object ImageExtractor {

    private const val TAG = "NavBandImage"

    fun extract(
        context: Context,
        notification: Notification
    ): Bitmap? {

        try {

            val packageName =
                "com.google.android.apps.maps"

            val appContext =
                context.createPackageContext(
                    packageName,
                    Context.CONTEXT_IGNORE_SECURITY
                )

            val builder =
                Notification.Builder.recoverBuilder(
                    context,
                    notification
                )

            val remoteViews =
                builder.createBigContentView()
                    ?: builder.createContentView()

            if (remoteViews != null) {

                val inflater =
                    appContext.getSystemService(
                        Context.LAYOUT_INFLATER_SERVICE
                    ) as LayoutInflater

                val root =
                    inflater.inflate(
                        remoteViews.layoutId,
                        null
                    ) as? ViewGroup

                if (root != null) {

                    remoteViews.reapply(
                        appContext,
                        root
                    )

                    val bitmap =
                        findNavigationIcon(
                            appContext,
                            root
                        )

                    if (bitmap != null) {
                        Log.d(
                            TAG,
                            "RemoteViews SUCCESS: ${bitmap.width}x${bitmap.height}"
                        )
                        return bitmap
                    } else {
                        Log.d(TAG, "RemoteViews: no matching ImageView found")
                    }
                }
            }

        } catch (e: Throwable) {
            Log.e(
                TAG,
                "RemoteViews extraction FAILED",
                e
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            try {

                val icon =
                    notification.extras?.getParcelable(
                        Notification.EXTRA_LARGE_ICON,
                        android.graphics.drawable.Icon::class.java
                    )

                if (icon != null) {

                    val drawable =
                        icon.loadDrawable(context)

                    if (drawable != null) {
                        val bitmap = drawableToBitmap(drawable)

                        if (bitmap != null) {
                            Log.d(
                                TAG,
                                "LARGE_ICON SUCCESS: ${bitmap.width}x${bitmap.height}"
                            )
                        } else {
                            Log.d(TAG, "LARGE_ICON drawable -> bitmap FAILED")
                        }

                        return bitmap
                    } else {
                        Log.d(TAG, "LARGE_ICON loadDrawable returned null")
                    }
                }

            } catch (_: Throwable) {
            }
        }

        try {

            @Suppress("DEPRECATION")
            val picture =
                notification.extras?.get(
                    Notification.EXTRA_PICTURE
                )

            if (picture is Bitmap) {
                Log.d(
                    TAG,
                    "PICTURE SUCCESS: ${picture.width}x${picture.height}"
                )
                return picture
            } else {
                Log.d(
                    TAG,
                    "PICTURE not available"
                )
            }

        } catch (e: Throwable) {
            Log.e(
                TAG,
                "PICTURE extraction FAILED",
                e
            )
        }

        Log.d(TAG, "EXTRACT RESULT: NULL")
        return null
    }

    private fun findNavigationIcon(
        context: Context,
        view: View
    ): Bitmap? {

        if (view is ImageView) {

            val resourceName =
                getResourceName(
                    context,
                    view.id
                )

            if (
                resourceName == "nav_notification_icon" ||
                resourceName == "right_icon" ||
                resourceName == "lockscreen_notification_icon"
            ) {

                val drawable =
                    view.drawable

                if (drawable != null) {
                    return drawableToBitmap(drawable)
                }
            }
        }

        if (view is ViewGroup) {

            for (index in 0 until view.childCount) {

                val result =
                    findNavigationIcon(
                        context,
                        view.getChildAt(index)
                    )

                if (result != null) {
                    return result
                }
            }
        }

        return null
    }

    private fun getResourceName(
        context: Context,
        id: Int
    ): String? {

        if (id <= 0) {
            return null
        }

        return try {
            context.resources.getResourceEntryName(id)
        } catch (_: Exception) {
            null
        }
    }

    private fun drawableToBitmap(
        drawable: Drawable
    ): Bitmap? {

        return try {

            if (
                drawable is BitmapDrawable &&
                drawable.bitmap != null
            ) {

                return drawable.bitmap.copy(
                    Bitmap.Config.ARGB_8888,
                    false
                )
            }

            val width =
                if (drawable.intrinsicWidth > 0) {
                    drawable.intrinsicWidth
                } else {
                    90
                }

            val height =
                if (drawable.intrinsicHeight > 0) {
                    drawable.intrinsicHeight
                } else {
                    90
                }

            val bitmap =
                Bitmap.createBitmap(
                    width,
                    height,
                    Bitmap.Config.ARGB_8888
                )

            val canvas =
                Canvas(bitmap)

            drawable.setBounds(
                0,
                0,
                canvas.width,
                canvas.height
            )

            drawable.draw(canvas)

            bitmap

        } catch (_: Throwable) {
            null
        }
    }
}
