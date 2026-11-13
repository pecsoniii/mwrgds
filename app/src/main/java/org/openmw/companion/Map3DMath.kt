package org.openmw.companion

import java.util.Locale

/** A place in a 3D map picture: 0..1 across (left to right) and down (top to bottom). */
data class Map3DPoint(val u: Float, val v: Float)

/**
 * RG DS 3D local map: the pure parts, kept apart from Android and Compose so they can be unit-tested.
 * The engine side is companionmap3d.cpp.
 */
object Map3DMath {
    /**
     * Where a world position shows in a picture made with [matrix], or null when it is behind the
     * camera. [matrix] is the engine's view x projection as OSG keeps it: row-major, taking a world
     * position (x, y, z, 1) as a ROW vector to clip space.
     */
    fun project(matrix: FloatArray, x: Float, y: Float, z: Float): Map3DPoint? {
        val m = matrix
        val cx = x * m[0] + y * m[4] + z * m[8] + m[12]
        val cy = x * m[1] + y * m[5] + z * m[9] + m[13]
        val cw = x * m[3] + y * m[7] + z * m[11] + m[15]
        if (cw <= 1e-4f) return null
        return Map3DPoint((cx / cw + 1f) / 2f, 1f - (cy / cw + 1f) / 2f)
    }

    /** The companion command that sets the engine's map camera (see companionMap3DCommand). */
    fun viewCommand(on: Boolean, distance: Float, yaw: Float, panX: Float, panY: Float, width: Int, height: Int): String =
        String.format(
            Locale.ROOT, "CMP:map3d:view:%d,%.0f,%.1f,%.0f,%.0f,%d,%d",
            if (on) 1 else 0, distance, yaw, panX, panY, width, height
        )

    /**
     * The picture size to ask for: the panel's size, scaled down to fit [maxSide] on its longer side
     * (keeping its shape), and at least 64 on each side.
     */
    fun pictureSize(panelWidth: Int, panelHeight: Int, maxSide: Int): Pair<Int, Int> {
        val scale = minOf(1f, maxSide.toFloat() / maxOf(panelWidth, panelHeight))
        val w = Math.round(panelWidth * scale).coerceAtLeast(64)
        val h = Math.round(panelHeight * scale).coerceAtLeast(64)
        return w to h
    }
}
