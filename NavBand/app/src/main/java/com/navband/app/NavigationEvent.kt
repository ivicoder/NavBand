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
    val subText: String = "",
    val roundaboutExit: Int? = null,
    val image: Bitmap? = null
)
