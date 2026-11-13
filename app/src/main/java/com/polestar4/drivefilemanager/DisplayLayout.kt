package com.polestar4.drivefilemanager

internal object DisplayLayout {
    fun widthDp(windowWidthPx: Int, density: Float, fallbackWidthDp: Int): Int =
        if (windowWidthPx > 0 && density > 0f) (windowWidthPx / density).toInt() else fallbackWidthDp

    fun isVehicleLayout(automotive: Boolean, landscape: Boolean, widthDp: Int) =
        automotive || (landscape && widthDp >= 600)

    fun isWideLayout(landscape: Boolean, widthDp: Int) = landscape && widthDp >= 700
}
