package com.astraflow.tool.hook.liquidnotif.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * 玻璃的边的光从哪边来(液态玻璃卡片与控制中心共用):手机倾斜的方向(屏幕平面上的重力方向)转 −45°,
 * 竖直拿着时从左上来。原来借旧玻璃库的亮边光源来算,旧库删掉后把同一个算法单独写在这里,结果不变。
 */

/** 光的方向在重力方向上再转的角度 */
private const val LIGHT_DEGREES = -45f

/** 重力在屏幕平面上的分量太小(手机平放)时按竖直拿着算 */
private const val GRAVITY_DIR_THRESHOLD_SQ = 0.01f

/** 倾斜读数每次的平滑系数 */
private const val TILT_SMOOTHING = 0.15f

/** 光的方向变化不到这么多时不更新,手机放着不动时卡片不必每帧重画 */
private const val MOVE_EPSILON = 0.01f

/** 竖直拿着(没有倾斜)时光照过来的方向:左上 */
internal val UPRIGHT_LIGHT = glassLightDirection(0f, 0f)

/**
 * 光照过来的方向(单位向量,指向最亮的那一角):重力方向 [gravityX]、[gravityY](屏幕坐标,+X 向右、+Y 向上)
 * 转 −45°;重力在屏幕上的分量太小时按竖直拿着算。
 */
internal fun glassLightDirection(gravityX: Float, gravityY: Float): Offset {
    val magSq = gravityX * gravityX + gravityY * gravityY
    val (x0, y0) = if (magSq > GRAVITY_DIR_THRESHOLD_SQ) {
        val inv = 1f / sqrt(magSq)
        (gravityX * inv) to (gravityY * inv)
    } else {
        0f to -1f
    }
    val rad = LIGHT_DEGREES * PI / 180.0
    val c = cos(rad).toFloat()
    val s = sin(rad).toFloat()
    return Offset(c * x0 - s * y0, s * x0 + c * y0)
}

/**
 * 光从哪边来:随手机倾斜转动时读方向传感器(游戏用方向传感器,没有时用普通方向传感器;取旋转矩阵第三行的前两项取反
 * 作重力方向,每次按 0.15 平滑);关掉时固定在竖直拿着的方向。
 */
@Composable
internal fun rememberGlassLight(tilt: Boolean): State<Offset> {
    val holder = remember { mutableStateOf(UPRIGHT_LIGHT) }
    if (tilt) {
        val context = LocalContext.current
        DisposableEffect(context) {
            val listener = TiltListener { next ->
                val now = holder.value
                if (abs(next.x - now.x) + abs(next.y - now.y) > MOVE_EPSILON) holder.value = next
            }
            listener.start(context)
            onDispose { listener.stop() }
        }
    } else {
        SideEffect { holder.value = UPRIGHT_LIGHT }
    }
    return holder
}

private class TiltListener(private val onLight: (Offset) -> Unit) : SensorEventListener {
    private val matrix = FloatArray(9)
    private var manager: SensorManager? = null
    private var initialized = false
    private var gravityX = 0f
    private var gravityY = 0f

    fun start(context: Context) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: return
        manager = sm
        sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        manager?.unregisterListener(this)
        manager = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(matrix, event.values)
        val gx = -matrix[6]
        val gy = -matrix[7]
        if (!initialized) {
            gravityX = gx
            gravityY = gy
            initialized = true
        } else {
            gravityX += (gx - gravityX) * TILT_SMOOTHING
            gravityY += (gy - gravityY) * TILT_SMOOTHING
        }
        onLight(glassLightDirection(gravityX, gravityY))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
