package com.astraflow.tool.hook.liquidcc

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import kotlin.math.abs

/**
 * 手机的倾斜方向,给控制中心玻璃的亮边转动用(普通视图用不了界面里的传感器读法)。
 * 算法与界面库的倾斜读数相同:游戏用方向传感器(没有时用普通方向传感器),取旋转矩阵第三行的前两项取反作重力方向,
 * 每次按 0.15 平滑。这样控制中心的亮边与液态玻璃卡片、底栏转得一样。只在控制中心显示时开着。
 */
internal object GlassTilt : SensorEventListener {

    /** 与界面库相同的平滑系数 */
    private const val SMOOTHING = 0.15f

    /** 方向变化小于这个(约 0.6 度)不刷新亮边 */
    private const val MOVE_EPSILON = 0.01f

    private val matrix = FloatArray(9)
    private var manager: SensorManager? = null
    private var listening = false
    private var initialized = false
    private var notifiedX = 0f
    private var notifiedY = 0f

    /** 平滑后的重力方向(与界面库倾斜读数的 gravityX / gravityY 同一个意思) */
    var gravityX = 0f
        private set
    var gravityY = 0f
        private set

    /** 方向明显变了(系统界面主线程) */
    var onMoved: (() -> Unit)? = null

    fun start(context: Context) {
        if (listening) return
        val sm = context.getSystemService(SensorManager::class.java) ?: return
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: return
        manager = sm
        initialized = false
        listening = sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI, Handler(Looper.getMainLooper()))
    }

    fun stop() {
        if (!listening) return
        manager?.unregisterListener(this)
        listening = false
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
            gravityX += (gx - gravityX) * SMOOTHING
            gravityY += (gy - gravityY) * SMOOTHING
        }
        if (abs(gravityX - notifiedX) > MOVE_EPSILON || abs(gravityY - notifiedY) > MOVE_EPSILON) {
            notifiedX = gravityX
            notifiedY = gravityY
            onMoved?.invoke()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
