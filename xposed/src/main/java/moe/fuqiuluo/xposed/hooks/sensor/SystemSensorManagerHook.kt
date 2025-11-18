@file:Suppress("UNCHECKED_CAST")
package moe.fuqiuluo.xposed.hooks.sensor

import android.content.pm.FeatureInfo
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEventListener
import android.util.ArrayMap
import de.robv.android.xposed.XposedHelpers
import moe.fuqiuluo.xposed.utils.FakeLoc
import moe.fuqiuluo.xposed.utils.Logger
import moe.fuqiuluo.xposed.utils.afterHook
import moe.fuqiuluo.xposed.utils.beforeHook
import moe.fuqiuluo.xposed.utils.hookAllMethods
import moe.fuqiuluo.xposed.utils.hookMethodAfter
import moe.fuqiuluo.xposed.utils.onceHook
import moe.fuqiuluo.xposed.utils.onceHookAllMethod
import java.util.concurrent.ConcurrentHashMap

// https://github.com/Frazew/VirtualSensor/blob/master/app/src/main/java/fr/frazew/virtualgyroscope/XposedMod.java#L298
object SystemSensorManagerHook {
    private val listenerMap = ConcurrentHashMap<SensorEventListener, Int>()

    operator fun invoke(classLoader: ClassLoader) {
        unlockGeoSensor(classLoader)

        hookSystemSensorManager(classLoader)
        hookSystemSensorManagerQueue(classLoader)
    }

    private fun hookSystemSensorManagerQueue(classLoader: ClassLoader) {
        val cSystemSensorManagerQueue = XposedHelpers.findClassIfExists("android.hardware.SystemSensorManager\$SensorEventQueue", classLoader)
            ?: return

        // Hook the dispatchSensorEvent method to inject simulated sensor data
        cSystemSensorManagerQueue.hookAllMethods("dispatchSensorEvent", beforeHook {
            if (!FakeLoc.enable) return@beforeHook
            if (FakeLoc.movementMode == FakeLoc.MovementMode.STATIC) return@beforeHook
            
            if (args.size < 3) return@beforeHook
            
            val handle = args[0] as? Int ?: return@beforeHook
            val values = args[1] as? FloatArray ?: return@beforeHook
            val sensorType = listenerMap.entries.firstOrNull { 
                // Map sensor listener to sensor type
                // This is a simplified approach
                true 
            }?.value
            
            // Inject simulated data for accelerometer and gyroscope
            when (sensorType) {
                android.hardware.Sensor.TYPE_ACCELEROMETER -> {
                    val simulatedData = moe.fuqiuluo.xposed.utils.SensorSimulator.generateAccelerometerData()
                    System.arraycopy(simulatedData, 0, values, 0, minOf(simulatedData.size, values.size))
                    
                    if (FakeLoc.enableDebugLog) {
                        Logger.debug("Injected accelerometer data: ${simulatedData.contentToString()}")
                    }
                }
                android.hardware.Sensor.TYPE_GYROSCOPE -> {
                    val simulatedData = moe.fuqiuluo.xposed.utils.SensorSimulator.generateGyroscopeData()
                    System.arraycopy(simulatedData, 0, values, 0, minOf(simulatedData.size, values.size))
                    
                    if (FakeLoc.enableDebugLog) {
                        Logger.debug("Injected gyroscope data: ${simulatedData.contentToString()}")
                    }
                }
            }
        })
    }

    private fun hookSystemSensorManager(classLoader: ClassLoader) {
        val cSystemSensorManager = XposedHelpers.findClassIfExists("android.hardware.SystemSensorManager", classLoader)
        if (cSystemSensorManager == null) {
            if (FakeLoc.enableDebugLog) {
                Logger.debug("Failed to find SystemSensorManager")
            }
            return
        }

        val hookRegisterListenerImpl = beforeHook {
            val listener = args[0] as SensorEventListener
            if (FakeLoc.enableDebugLog) {
                Logger.debug("RegisterListenerImpl: $listener, sensor: ${args[1]}")
            }

            val sensor = args[1] as? Sensor ?: return@beforeHook
            listenerMap[listener] = sensor.type

            listener.javaClass.onceHookAllMethod("onSensorChanged", beforeHook {
                if (!FakeLoc.enable) return@beforeHook
                if (FakeLoc.movementMode == FakeLoc.MovementMode.STATIC) return@beforeHook
                
                if (args.isEmpty() || args[0] == null) return@beforeHook
                
                val sensorEvent = args[0]
                val sensorType = listenerMap[listener] ?: return@beforeHook
                
                // Get the values array from SensorEvent
                val valuesField = kotlin.runCatching {
                    sensorEvent.javaClass.getDeclaredField("values").also { it.isAccessible = true }
                }.getOrNull() ?: return@beforeHook
                
                val values = valuesField.get(sensorEvent) as? FloatArray ?: return@beforeHook
                
                // Inject simulated data based on sensor type
                when (sensorType) {
                    android.hardware.Sensor.TYPE_ACCELEROMETER -> {
                        val simulatedData = moe.fuqiuluo.xposed.utils.SensorSimulator.generateAccelerometerData()
                        System.arraycopy(simulatedData, 0, values, 0, minOf(simulatedData.size, values.size))
                        
                        if (FakeLoc.enableDebugLog) {
                            Logger.debug("Injected accelerometer data: ${simulatedData.contentToString()}")
                        }
                    }
                    android.hardware.Sensor.TYPE_GYROSCOPE -> {
                        val simulatedData = moe.fuqiuluo.xposed.utils.SensorSimulator.generateGyroscopeData()
                        System.arraycopy(simulatedData, 0, values, 0, minOf(simulatedData.size, values.size))
                        
                        if (FakeLoc.enableDebugLog) {
                            Logger.debug("Injected gyroscope data: ${simulatedData.contentToString()}")
                        }
                    }
                }
            })
        }
        cSystemSensorManager.declaredMethods.filter {
            it.name == "registerListenerImpl" && it.parameterTypes.isNotEmpty()
                    && it.parameterTypes[0] == SensorEventListener::class.java
                    && it.parameterTypes[1] == Sensor::class.java
        }.forEach {
            it.onceHook(hookRegisterListenerImpl)
        }

        val hookUnregisterListenerImpl = beforeHook {
            val listener = args[0] as SensorEventListener
            if (FakeLoc.enableDebugLog) {
                Logger.debug("UnregisterListenerImpl: $listener")
            }
            listenerMap.remove(listener)
        }
        cSystemSensorManager.declaredMethods.filter {
            it.name == "unregisterListenerImpl" && it.parameterTypes.isNotEmpty()
                    && it.parameterTypes[0] == SensorEventListener::class.java
        }.forEach {
            it.onceHook(hookUnregisterListenerImpl)
        }

        cSystemSensorManager.hookAllMethods("getSensorList", afterHook {
            if (FakeLoc.enableDebugLog) {
                Logger.debug("getSensorList: type: ${args[0]} -> $result")
            }
        })
        cSystemSensorManager.hookAllMethods("getFullSensorsList", afterHook {
            if (FakeLoc.enableDebugLog) {
                Logger.debug("getFullSensorsList-> $result")
            }
        })
    }

    private fun unlockGeoSensor(classLoader: ClassLoader) {
        val cSystemConfig = XposedHelpers.findClassIfExists("com.android.server.SystemConfig", classLoader)
            ?: return

        val openGLVersion = run {
            val cSystemProperties = XposedHelpers.findClassIfExists("android.os.SystemProperties", classLoader)
                ?: return@run 0
            XposedHelpers.callStaticMethod(cSystemProperties, "getInt", "ro.opengles.version", FeatureInfo.GL_ES_VERSION_UNDEFINED) as Int
        }

        cSystemConfig.hookMethodAfter("getAvailableFeatures") {
            val features = result as ArrayMap<String, FeatureInfo>
            if (FakeLoc.enableDebugLog) {
                Logger.debug("getAvailableFeatures: ${features.keys}")
            }

//            if (!features.contains(PackageManager.FEATURE_SENSOR_GYROSCOPE)) {
//                val gyroFeature = FeatureInfo()
//                gyroFeature.name = PackageManager.FEATURE_SENSOR_GYROSCOPE
//                gyroFeature.reqGlEsVersion = openGLVersion
//                features[PackageManager.FEATURE_SENSOR_GYROSCOPE] = gyroFeature
//
//                if (FakeLoc.enableDebugLog) {
//                    Logger.debug("Added FEATURE_SENSOR_GYROSCOPE")
//                }
//            }
//            if (!features.contains(PackageManager.FEATURE_SENSOR_COMPASS)) {
//                val compassFeature = FeatureInfo()
//                compassFeature.name = PackageManager.FEATURE_SENSOR_COMPASS
//                compassFeature.reqGlEsVersion = openGLVersion
//                features[PackageManager.FEATURE_SENSOR_COMPASS] = compassFeature
//
//                if (FakeLoc.enableDebugLog) {
//                    Logger.debug("Added FEATURE_SENSOR_COMPASS")
//                }
//            }
//
//            if (!features.contains(PackageManager.FEATURE_SENSOR_ACCELEROMETER)) {
//                val accelerometerFeature = FeatureInfo()
//                accelerometerFeature.name = PackageManager.FEATURE_SENSOR_ACCELEROMETER
//                accelerometerFeature.reqGlEsVersion = openGLVersion
//                features[PackageManager.FEATURE_SENSOR_ACCELEROMETER] = accelerometerFeature
//
//                if (FakeLoc.enableDebugLog) {
//                    Logger.debug("Added FEATURE_SENSOR_ACCELEROMETER")
//                }
//            }
//
//            if (!features.contains(PackageManager.FEATURE_SENSOR_STEP_COUNTER)) {
//                val lightFeature = FeatureInfo()
//                lightFeature.name = PackageManager.FEATURE_SENSOR_STEP_COUNTER
//                lightFeature.reqGlEsVersion = openGLVersion
//                features[PackageManager.FEATURE_SENSOR_STEP_COUNTER] = lightFeature
//
//                if (FakeLoc.enableDebugLog) {
//                    Logger.debug("Added FEATURE_SENSOR_STEP_COUNTER")
//                }
//            }
//
//            if (!features.contains(PackageManager.FEATURE_SENSOR_STEP_DETECTOR)) {
//                val lightFeature = FeatureInfo()
//                lightFeature.name = PackageManager.FEATURE_SENSOR_STEP_DETECTOR
//                lightFeature.reqGlEsVersion = openGLVersion
//                features[PackageManager.FEATURE_SENSOR_STEP_DETECTOR] = lightFeature
//
//                if (FakeLoc.enableDebugLog) {
//                    Logger.debug("Added FEATURE_SENSOR_STEP_DETECTOR")
//                }
//            }
//
//            kotlin.runCatching {
//                XposedHelpers.setObjectField(thisObject, "mAvailableFeatures", features)
//            }.onFailure {
//                Logger.warn("Failed to set mAvailableFeatures", it)
//            }
            result = features
        }
    }
}