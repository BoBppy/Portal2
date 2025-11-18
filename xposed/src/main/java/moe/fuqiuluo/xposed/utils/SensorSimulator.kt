package moe.fuqiuluo.xposed.utils

import android.hardware.SensorEvent
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * 传感器数据模拟器
 * 用于生成逼真的加速度计和陀螺仪数据，模拟人类行走、跑步或驾驶
 */
object SensorSimulator {
    private var walkingPhase = 0.0
    private val lastUpdateTime = System.currentTimeMillis()

    /**
     * 生成模拟的加速度计数据
     * 基于运动模式生成逼真的三轴加速度数据
     * 
     * @param movementMode 运动模式
     * @param stepFrequency 步频（步/分钟）
     * @return 三轴加速度数据 [x, y, z]
     */
    fun generateAccelerometerData(
        movementMode: FakeLoc.MovementMode = FakeLoc.movementMode,
        stepFrequency: Int = FakeLoc.stepFrequency
    ): FloatArray {
        val currentTime = System.currentTimeMillis()
        
        return when (movementMode) {
            FakeLoc.MovementMode.STATIC -> {
                // 静止状态：返回重力加速度，带有微小的噪声
                floatArrayOf(
                    Random.nextFloat() * 0.1f - 0.05f,  // x轴微小抖动
                    Random.nextFloat() * 0.1f - 0.05f,  // y轴微小抖动
                    9.81f + Random.nextFloat() * 0.05f  // z轴重力 + 微小噪声
                )
            }
            
            FakeLoc.MovementMode.WALKING -> {
                // 步行状态：生成周期性的步伐模式
                // 步频转换为Hz: stepFrequency / 60
                val frequencyHz = stepFrequency / 60.0
                
                // 更新步行相位
                walkingPhase += 2 * PI * frequencyHz * 0.1  // 假设100ms更新一次
                if (walkingPhase > 2 * PI) walkingPhase -= 2 * PI
                
                // 生成步行模式的加速度
                // x轴：左右摆动
                val x = 0.5f * sin(walkingPhase).toFloat() + Random.nextFloat() * 0.1f - 0.05f
                
                // y轴：前后移动
                val y = 0.8f * sin(walkingPhase + PI/2).toFloat() + Random.nextFloat() * 0.1f - 0.05f
                
                // z轴：重力 + 垂直运动
                val z = 9.81f + 1.2f * sin(walkingPhase * 2).toFloat() + Random.nextFloat() * 0.15f
                
                floatArrayOf(x, y, z)
            }
            
            FakeLoc.MovementMode.RUNNING -> {
                // 跑步状态：更大幅度和更高频率的运动
                val frequencyHz = (stepFrequency * 1.3) / 60.0  // 跑步频率更高
                
                walkingPhase += 2 * PI * frequencyHz * 0.1
                if (walkingPhase > 2 * PI) walkingPhase -= 2 * PI
                
                // 跑步时加速度幅度更大
                val x = 1.0f * sin(walkingPhase).toFloat() + Random.nextFloat() * 0.2f - 0.1f
                val y = 1.5f * sin(walkingPhase + PI/2).toFloat() + Random.nextFloat() * 0.2f - 0.1f
                val z = 9.81f + 2.5f * sin(walkingPhase * 2).toFloat() + Random.nextFloat() * 0.3f
                
                floatArrayOf(x, y, z)
            }
            
            FakeLoc.MovementMode.DRIVING -> {
                // 驾驶状态：车辆振动和转弯产生的加速度
                // 驾驶时主要是低频振动和偶尔的转弯
                val vibrationPhase = currentTime / 100.0  // 慢速振动
                
                val x = 0.3f * sin(vibrationPhase).toFloat() + Random.nextFloat() * 0.15f - 0.075f
                val y = 0.2f * sin(vibrationPhase * 0.7).toFloat() + Random.nextFloat() * 0.1f - 0.05f
                val z = 9.81f + 0.4f * sin(vibrationPhase * 1.3).toFloat() + Random.nextFloat() * 0.2f
                
                floatArrayOf(x, y, z)
            }
        }
    }

    /**
     * 生成模拟的陀螺仪数据
     * 
     * @param movementMode 运动模式
     * @return 三轴角速度数据 [x, y, z] (弧度/秒)
     */
    fun generateGyroscopeData(
        movementMode: FakeLoc.MovementMode = FakeLoc.movementMode
    ): FloatArray {
        return when (movementMode) {
            FakeLoc.MovementMode.STATIC -> {
                // 静止状态：几乎无角速度，只有传感器噪声
                floatArrayOf(
                    Random.nextFloat() * 0.01f - 0.005f,
                    Random.nextFloat() * 0.01f - 0.005f,
                    Random.nextFloat() * 0.01f - 0.005f
                )
            }
            
            FakeLoc.MovementMode.WALKING -> {
                // 步行时的轻微转动
                floatArrayOf(
                    Random.nextFloat() * 0.1f - 0.05f,
                    Random.nextFloat() * 0.08f - 0.04f,
                    Random.nextFloat() * 0.05f - 0.025f
                )
            }
            
            FakeLoc.MovementMode.RUNNING -> {
                // 跑步时更大的转动
                floatArrayOf(
                    Random.nextFloat() * 0.2f - 0.1f,
                    Random.nextFloat() * 0.15f - 0.075f,
                    Random.nextFloat() * 0.1f - 0.05f
                )
            }
            
            FakeLoc.MovementMode.DRIVING -> {
                // 驾驶时根据转弯产生较大的z轴角速度
                floatArrayOf(
                    Random.nextFloat() * 0.05f - 0.025f,
                    Random.nextFloat() * 0.05f - 0.025f,
                    Random.nextFloat() * 0.3f - 0.15f  // 转弯时的偏航角速度
                )
            }
        }
    }

    /**
     * 重置传感器模拟状态
     */
    fun reset() {
        walkingPhase = 0.0
    }
}
