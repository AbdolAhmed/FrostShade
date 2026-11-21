package com.astraflow.tool.hook.glass

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import com.astraflow.frostshade.FrostStyle

/** 普通系统视图与侧边栏共用的玻璃表面；通知卡片使用同样的背景与上游折射参数。 */
internal class GlassMaterial {
    private val program by lazy { RuntimeShader(PROGRAM) }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var bitmap: Bitmap? = null
    private var background: BitmapShader? = null
    private val matrix = Matrix()

    fun draw(
        canvas: Canvas,
        frame: BackgroundFrame?,
        screenToLocal: Matrix,
        shape: Path,
        bounds: RectF,
        corners: FloatArray,
        density: Float,
        tint: Int,
        alpha: Float,
        highlight: Float,
        lightX: Float = -0.7071f,
        lightY: Float = -0.7071f,
        blurPx: Float = FrostStyle.BLUR_DP * density,
        press: Float = 0f,
    ): Boolean {
        frame ?: return false
        if (!canvas.isHardwareAccelerated || bounds.isEmpty || alpha <= 0f) return false
        if (bitmap !== frame.bitmap) {
            bitmap = frame.bitmap
            background = BitmapShader(frame.bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                filterMode = BitmapShader.FILTER_MODE_LINEAR
            }
        }
        val image = background ?: return false
        matrix.set(screenToLocal)
        matrix.preScale(frame.screenWidth.toFloat() / frame.bitmap.width, frame.screenHeight.toFloat() / frame.bitmap.height)
        image.setLocalMatrix(matrix)
        val shader = program
        shader.setInputShader("background", image)
        shader.setFloatUniform("bounds", bounds.left, bounds.top, bounds.width(), bounds.height())
        shader.setFloatUniform("radii", corners)
        shader.setFloatUniform("light", lightX, lightY)
        shader.setFloatUniform("band", minOf(FrostStyle.LENS_INSET_DP * density, bounds.width() * 0.22f, bounds.height() * 0.22f).coerceAtLeast(1f))
        shader.setFloatUniform("amount", FrostStyle.LENS_SHIFT_DP * density * (1f + press.coerceIn(0f, 1f) * 0.12f))
        shader.setFloatUniform("blurRadius", blurPx.coerceAtLeast(0f) * 0.5f)
        shader.setFloatUniform("surface", Color.red(tint) / 255f, Color.green(tint) / 255f, Color.blue(tint) / 255f, Color.alpha(tint) / 255f)
        shader.setFloatUniform("opacity", alpha.coerceIn(0f, 1f))
        shader.setFloatUniform("shine", highlight.coerceIn(0f, 1f))
        paint.shader = shader
        canvas.drawPath(shape, paint)
        return true
    }

    companion object {
        // 圆角距离与法线用于背景位移。厚度沿边缘连续变化，不用多道描边模拟弧面。
        private const val PROGRAM = """
            uniform shader background;
            uniform float4 bounds;
            uniform float4 radii;
            uniform float2 light;
            uniform float band;
            uniform float amount;
            uniform float blurRadius;
            uniform float4 surface;
            uniform float opacity;
            uniform float shine;
            float radiusAt(float2 p) {
                return p.x < 0.0 ? (p.y < 0.0 ? radii.x : radii.w) : (p.y < 0.0 ? radii.y : radii.z);
            }
            float distanceAt(float2 p, float2 halfSize, float radius) {
                float2 q = abs(p) - (halfSize - radius);
                return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
            }
            half4 blurred(float2 p) {
                float2 x = float2(blurRadius, 0.0);
                float2 y = float2(0.0, blurRadius);
                return background.eval(p) * 0.25
                    + (background.eval(p+x) + background.eval(p-x) + background.eval(p+y) + background.eval(p-y)) * 0.125
                    + (background.eval(p+x+y) + background.eval(p-x-y) + background.eval(p+x-y) + background.eval(p-x+y)) * 0.0625;
            }
            half4 main(float2 coord) {
                float2 halfSize = bounds.zw * 0.5;
                float2 p = coord - bounds.xy - halfSize;
                float radius = radiusAt(p);
                float sd = distanceAt(p, halfSize, radius);
                float2 g = float2(distanceAt(p+float2(0.4,0.0),halfSize,radius) - distanceAt(p-float2(0.4,0.0),halfSize,radius),
                                  distanceAt(p+float2(0.0,0.4),halfSize,radius) - distanceAt(p-float2(0.0,0.4),halfSize,radius));
                g /= max(length(g), 0.001);
                float t = clamp(-sd / band, 0.0, 1.0);
                float curvature = 1.0 - sqrt(max(0.0, 1.0 - (1.0-t)*(1.0-t)));
                half4 source = blurred(coord - g * curvature * amount);
                half gray = dot(source.rgb, half3(0.2126,0.7152,0.0722));
                half3 color = clamp(mix(half3(gray), source.rgb, 0.92), 0.0, 1.0);
                color = mix(color, half3(surface.rgb), surface.a);
                float lit = dot(g, light);
                float edge = exp(-pow((-sd-0.7)/0.65,2.0));
                float rim = edge * (0.12 + 0.62*pow(max(lit,0.0),6.0) + 0.2*pow(max(-lit,0.0),8.0)) * shine;
                float shade = exp(min(sd,0.0)/max(band*0.3,1.0)) * max(-lit,0.0) * 0.07;
                color *= 1.0-shade;
                color = mix(color, half3(1.0), rim);
                half a = half(clamp(0.5-sd,0.0,1.0) * opacity);
                return half4(color*a, a);
            }
        """
    }
}
