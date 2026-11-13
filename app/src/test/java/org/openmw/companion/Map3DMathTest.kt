package org.openmw.companion

import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The 3D map's picture maths against a matrix built the way the engine builds it
 * (companionmap3d.cpp: OSG makeLookAt and makePerspective, then view * projection, row-major,
 * row vectors). If the Kotlin side read the matrix the wrong way round, the player arrow and the
 * door markers would land in the wrong places.
 */
class Map3DMathTest {
    // 4x4 row-major, as osg::Matrixd keeps _mat[row][col].
    private fun multiply(a: DoubleArray, b: DoubleArray): DoubleArray {
        val r = DoubleArray(16)
        for (i in 0 until 4) for (j in 0 until 4) {
            var s = 0.0
            for (k in 0 until 4) s += a[i * 4 + k] * b[k * 4 + j]
            r[i * 4 + j] = s
        }
        return r
    }

    private fun lookAt(eye: DoubleArray, center: DoubleArray, up: DoubleArray): DoubleArray {
        fun norm(v: DoubleArray): DoubleArray { val l = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]); return doubleArrayOf(v[0] / l, v[1] / l, v[2] / l) }
        fun cross(a: DoubleArray, b: DoubleArray) = doubleArrayOf(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])
        fun dot(a: DoubleArray, b: DoubleArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
        val f = norm(doubleArrayOf(center[0] - eye[0], center[1] - eye[1], center[2] - eye[2]))
        val s = norm(cross(f, up))
        val u = norm(cross(s, f))
        return doubleArrayOf(
            s[0], u[0], -f[0], 0.0,
            s[1], u[1], -f[1], 0.0,
            s[2], u[2], -f[2], 0.0,
            -dot(s, eye), -dot(u, eye), dot(f, eye), 1.0,
        )
    }

    private fun perspective(fovyDeg: Double, aspect: Double, near: Double, far: Double): DoubleArray {
        val t = tan(Math.toRadians(fovyDeg * 0.5))
        val right = t * aspect * near; val left = -right
        val top = t * near; val bottom = -top
        return doubleArrayOf(
            2 * near / (right - left), 0.0, 0.0, 0.0,
            0.0, 2 * near / (top - bottom), 0.0, 0.0,
            (right + left) / (right - left), (top + bottom) / (top - bottom), -(far + near) / (far - near), -1.0,
            0.0, 0.0, -2 * far * near / (far - near), 0.0,
        )
    }

    /** The engine's camera: 55 degrees down, 45 degree field of view, [yawDeg] from north. */
    private fun engineMatrix(fx: Double, fy: Double, fz: Double, distance: Double, yawDeg: Double, aspect: Double): FloatArray {
        val pitch = Math.toRadians(55.0)
        val yaw = Math.toRadians(yawDeg)
        val back = doubleArrayOf(-sin(yaw) * cos(pitch), -cos(yaw) * cos(pitch), sin(pitch))
        val eye = doubleArrayOf(fx + back[0] * distance, fy + back[1] * distance, fz + back[2] * distance)
        val view = lookAt(eye, doubleArrayOf(fx, fy, fz), doubleArrayOf(0.0, 0.0, 1.0))
        val proj = perspective(45.0, aspect, maxOf(10.0, distance * 0.05), distance * 6 + 16384)
        return multiply(view, proj).map { it.toFloat() }.toFloatArray()
    }

    @Test fun theFocusIsTheMiddleOfThePicture() {
        val m = engineMatrix(-20480.0, -12288.0, 600.0, 2400.0, 0.0, 640.0 / 430.0)
        val p = Map3DMath.project(m, -20480f, -12288f, 600f)!!
        assertEquals(0.5f, p.u, 1e-3f)
        assertEquals(0.5f, p.v, 1e-3f)
    }

    @Test fun northIsUpAndEastIsRightWhenTheMapIsNotTurned() {
        val m = engineMatrix(0.0, 0.0, 0.0, 2400.0, 0.0, 1.5)
        val north = Map3DMath.project(m, 0f, 500f, 0f)!!
        val east = Map3DMath.project(m, 500f, 0f, 0f)!!
        assertTrue("north is above the middle", north.v < 0.5f)
        assertEquals(0.5f, north.u, 1e-3f)
        assertTrue("east is right of the middle", east.u > 0.5f)
    }

    @Test fun turningTheMapNinetyDegreesPutsEastUp() {
        val m = engineMatrix(0.0, 0.0, 0.0, 2400.0, 90.0, 1.5)
        val east = Map3DMath.project(m, 500f, 0f, 0f)!!
        assertTrue(east.v < 0.5f)
        assertEquals(0.5f, east.u, 1e-3f)
    }

    @Test fun somethingTallerShowsHigherInThePicture() {
        val m = engineMatrix(0.0, 0.0, 0.0, 2400.0, 0.0, 1.5)
        val ground = Map3DMath.project(m, 0f, 0f, 0f)!!
        val roof = Map3DMath.project(m, 0f, 0f, 300f)!!
        assertTrue(roof.v < ground.v)
    }

    @Test fun aPlaceBehindTheCameraIsNotShown() {
        val m = engineMatrix(0.0, 0.0, 0.0, 2400.0, 0.0, 1.5)
        // Far behind the eye, which is south of the focus and up.
        assertNull(Map3DMath.project(m, 0f, -40000f, 5000f))
        assertNotNull(Map3DMath.project(m, 0f, -500f, 0f))
    }

    @Test fun theCommandUsesDotsAndWholeNumbersWhateverTheLocale() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals(
                "CMP:map3d:view:1,2400,12.5,-30,40,640,430",
                Map3DMath.viewCommand(true, 2400.4f, 12.5f, -30f, 40f, 640, 430)
            )
            assertEquals("CMP:map3d:view:0,800,0.0,0,0,64,64", Map3DMath.viewCommand(false, 800f, 0f, 0f, 0f, 64, 64))
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test fun thePictureKeepsThePanelsShapeWithinTheCap() {
        assertEquals(640 to 430, Map3DMath.pictureSize(640, 430, 640))
        assertEquals(640 to 320, Map3DMath.pictureSize(1280, 640, 640))
        assertEquals(64 to 64, Map3DMath.pictureSize(10, 10, 640))
    }
    @Test fun interiorsAndLoadingKeepTheFlatMap() {
        assertTrue(Map3DMath.shouldRender(true, true, true))
        org.junit.Assert.assertFalse(Map3DMath.shouldRender(true, true, false))
        org.junit.Assert.assertFalse(Map3DMath.shouldRender(true, false, true))
        org.junit.Assert.assertFalse(Map3DMath.shouldRender(false, true, true))
    }

    @Test fun doorsUseTheirOwnElevationAndOldPayloadsKeepAFallback() {
        val m = engineMatrix(0.0, 0.0, 0.0, 2400.0, 0.0, 1.5)
        val ground = Map3DMath.project(m, 0f, 0f, Map3DMath.markerHeight(0f, 300f))!!
        val high = Map3DMath.project(m, 0f, 0f, Map3DMath.markerHeight(300f, 0f))!!
        assertTrue(high.v < ground.v)
        assertEquals(42f, Map3DMath.markerHeight(null, 42f), 0f)
    }
}
