package ai.multica.android.core.theme

import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Convert an oklch color to ARGB Int.
 *
 * Algorithm: Björn Ottosson's reference transform (oklch → oklab →
 * linear LMS → linear sRGB → sRGB). Round-trips the design tokens
 * in packages/ui/styles/tokens.css exactly.
 *
 * @param L perceptual lightness 0..1 (oklch L)
 * @param C chroma 0..~0.4 (oklch C)
 * @param H hue in degrees 0..360
 * @param alpha 0..1
 */
fun oklchToArgb(L: Double, C: Double, H: Double, alpha: Double = 1.0): Int {
    val hRad = H * (Math.PI / 180.0)

    // oklch → oklab
    val a = C * cos(hRad)
    val b = C * sin(hRad)

    // oklab → linear LMS (cube the values to undo the perceptual compression)
    val l = L + 0.3963377774 * a + 0.2158037573 * b
    val m = L - 0.1055613458 * a - 0.0638541728 * b
    val s = L - 0.0894841775 * a - 1.2914855480 * b

    val lCube = l * l * l
    val mCube = m * m * m
    val sCube = s * s * s

    // linear LMS → linear sRGB
    val lr = 4.0767416621 * lCube - 3.3077115913 * mCube + 0.2309699292 * sCube
    val lg = -1.2684380046 * lCube + 2.6097574011 * mCube - 0.3413193965 * sCube
    val lb = -0.0041960863 * lCube - 0.7034186147 * mCube + 1.7076147010 * sCube

    // linear sRGB → sRGB (gamma)
    val r = linearToSrgb(lr).clamp01()
    val g = linearToSrgb(lg).clamp01()
    val bl = linearToSrgb(lb).clamp01()

    val a255 = (alpha.clamp01() * 255.0).toInt()
    val r255 = (r * 255.0).toInt()
    val g255 = (g * 255.0).toInt()
    val b255 = (bl * 255.0).toInt()

    return (a255 shl 24) or (r255 shl 16) or (g255 shl 8) or b255
}

private fun linearToSrgb(c: Double): Double =
    if (c <= 0.0031308) 12.92 * c
    else 1.055 * c.pow(1.0 / 2.4) - 0.055

private fun Double.clamp01(): Double = if (this < 0.0) 0.0 else if (this > 1.0) 1.0 else this
