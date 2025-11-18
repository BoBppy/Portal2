package moe.fuqiuluo.xposed.hooks.gnss

import android.location.GpsSatellite
import android.location.GpsStatus
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import moe.fuqiuluo.xposed.BaseLocationHook
import moe.fuqiuluo.xposed.utils.FakeLoc
import moe.fuqiuluo.xposed.utils.Logger
import kotlin.random.Random

/**
 * Hook for deprecated GpsStatus API (Android < 24 / API < N)
 * This provides backward compatibility for apps still using the old GPS status listener API
 */
object GpsStatusHook : BaseLocationHook() {
    
    operator fun invoke(classLoader: ClassLoader) {
        hookGpsStatusListener(classLoader)
    }

    private fun hookGpsStatusListener(classLoader: ClassLoader) {
        val cILocationManager = XposedHelpers.findClassIfExists(
            "com.android.server.location.LocationManagerService", 
            classLoader
        ) ?: XposedHelpers.findClassIfExists(
            "android.location.ILocationManager\$Stub",
            classLoader
        ) ?: run {
            Logger.error("Cannot find ILocationManager for GpsStatus hook")
            return
        }

        // Hook addGpsStatusListener method
        XposedBridge.hookAllMethods(cILocationManager, "addGpsStatusListener", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam?) {
                if (param == null || param.args.isEmpty() || param.args[0] == null) return

                if (!FakeLoc.enableMockGnss) {
                    return
                }

                val listener = param.args[0] ?: return
                val cGpsStatusListener = listener.javaClass

                if (FakeLoc.enableDebugLog) {
                    Logger.debug("addGpsStatusListener: hooked listener class = ${cGpsStatusListener.name}")
                }

                // Hook the onGpsStatusChanged callback
                kotlin.runCatching {
                    XposedHelpers.findAndHookMethod(
                        cGpsStatusListener,
                        "onGpsStatusChanged",
                        Int::class.java,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                if (!FakeLoc.enableMockGnss || FakeLoc.enableAGPS) {
                                    return
                                }

                                val event = param.args[0] as? Int ?: return

                                if (FakeLoc.enableDebugLog) {
                                    Logger.debug("onGpsStatusChanged: event = $event")
                                }

                                // GpsStatus events:
                                // GPS_EVENT_STARTED = 1
                                // GPS_EVENT_STOPPED = 2  
                                // GPS_EVENT_FIRST_FIX = 3
                                // GPS_EVENT_SATELLITE_STATUS = 4

                                // We allow all events through, but mock satellite status
                                // The actual satellite data is stored in GpsStatus object
                                // which is managed by the system, so we don't modify it here
                            }
                        }
                    )
                }.onFailure {
                    Logger.error("Failed to hook onGpsStatusChanged: ${it.message}", it)
                }
            }
        })

        // Hook removeGpsStatusListener method
        XposedBridge.hookAllMethods(cILocationManager, "removeGpsStatusListener", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam?) {
                if (param == null || param.args.isEmpty()) return

                if (FakeLoc.enableDebugLog) {
                    Logger.debug("removeGpsStatusListener: listener removed")
                }
            }
        })

        // Hook getGpsStatus method to provide mock GPS status data
        hookGpsStatusGetter(classLoader)
    }

    private fun hookGpsStatusGetter(classLoader: ClassLoader) {
        val cLocationManager = XposedHelpers.findClassIfExists("android.location.LocationManager", classLoader)
        if (cLocationManager != null) {
            XposedBridge.hookAllMethods(cLocationManager, "getGpsStatus", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam?) {
                    if (param == null || !FakeLoc.enableMockGnss || FakeLoc.enableAGPS) {
                        return
                    }

                    if (FakeLoc.enableDebugLog) {
                        Logger.debug("getGpsStatus: injecting mock GPS status")
                    }

                    // The GpsStatus object is managed by the system and contains satellite data
                    // We can modify it using reflection if needed
                    val gpsStatus = param.result as? GpsStatus ?: return

                    try {
                        mockGpsStatusSatellites(gpsStatus)
                    } catch (e: Exception) {
                        Logger.error("Failed to mock GPS status satellites: ${e.message}", e)
                    }
                }
            })
        }
    }

    @Suppress("DEPRECATION")
    private fun mockGpsStatusSatellites(gpsStatus: GpsStatus) {
        // Access internal satellite data through reflection
        // GpsStatus has internal mSatellites list that we need to populate
        
        try {
            val mSatellitesField = GpsStatus::class.java.getDeclaredField("mSatellites")
            mSatellitesField.isAccessible = true
            
            @Suppress("UNCHECKED_CAST")
            val satellites = mSatellitesField.get(gpsStatus) as? ArrayList<GpsSatellite> ?: return

            // Clear existing satellites
            satellites.clear()

            // Generate mock satellites (using BeiDou constellation like in GnssStatus)
            val satelliteCount = Random.nextInt(FakeLoc.minSatellites, 35)
            
            for (i in 0 until satelliteCount) {
                val prn = i + 1 // Satellite PRN number
                val snr = Random.nextFloat() * 30f + 15f // SNR: 15-45 dB
                val elevation = Random.nextFloat() * 90f // Elevation: 0-90 degrees
                val azimuth = Random.nextFloat() * 360f // Azimuth: 0-360 degrees
                val hasEphemeris = Random.nextBoolean()
                val hasAlmanac = Random.nextBoolean()
                val usedInFix = Random.nextFloat() > 0.3f // 70% probability
                
                // Create GpsSatellite using reflection (constructor is package-private)
                val satellite = createGpsSatellite(prn, snr, elevation, azimuth, hasEphemeris, hasAlmanac, usedInFix)
                if (satellite != null) {
                    satellites.add(satellite)
                }
            }

            if (FakeLoc.enableDebugLog) {
                Logger.debug("Mocked $satelliteCount GPS satellites")
            }
        } catch (e: Exception) {
            Logger.error("Failed to access GpsStatus internals: ${e.message}", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun createGpsSatellite(
        prn: Int,
        snr: Float,
        elevation: Float,
        azimuth: Float,
        hasEphemeris: Boolean,
        hasAlmanac: Boolean,
        usedInFix: Boolean
    ): GpsSatellite? {
        return try {
            val constructor = GpsSatellite::class.java.getDeclaredConstructor(Int::class.java)
            constructor.isAccessible = true
            val satellite = constructor.newInstance(prn) as GpsSatellite

            // Set satellite properties using reflection
            setSatelliteField(satellite, "mSnr", snr)
            setSatelliteField(satellite, "mElevation", elevation)
            setSatelliteField(satellite, "mAzimuth", azimuth)
            setSatelliteField(satellite, "mHasEphemeris", hasEphemeris)
            setSatelliteField(satellite, "mHasAlmanac", hasAlmanac)
            setSatelliteField(satellite, "mUsedInFix", usedInFix)
            setSatelliteField(satellite, "mValid", true)

            satellite
        } catch (e: Exception) {
            Logger.error("Failed to create GpsSatellite: ${e.message}")
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun setSatelliteField(satellite: GpsSatellite, fieldName: String, value: Any) {
        try {
            val field = GpsSatellite::class.java.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(satellite, value)
        } catch (e: Exception) {
            // Field might not exist in all Android versions
            if (FakeLoc.enableDebugLog) {
                Logger.debug("Field $fieldName not found in GpsSatellite")
            }
        }
    }
}
