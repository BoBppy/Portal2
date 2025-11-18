package moe.fuqiuluo.xposed.ipc

import android.content.ContentResolver
import android.net.Uri
import moe.fuqiuluo.xposed.utils.BinderUtils
import moe.fuqiuluo.xposed.utils.FakeLoc
import moe.fuqiuluo.xposed.utils.Logger
import kotlin.concurrent.thread

/**
 * 配置同步服务
 * 从Portal App的ContentProvider中定期拉取最新配置
 */
object ConfigSyncService {
    private const val AUTHORITY = "moe.fuqiuluo.portal.provider.location"
    private val CONTENT_URI = Uri.parse("content://$AUTHORITY/config")
    
    private var syncThread: Thread? = null
    private var running = false
    
    /**
     * 启动配置同步服务
     */
    fun start() {
        if (running) {
            Logger.warn("ConfigSyncService already running")
            return
        }
        
        running = true
        syncThread = thread(name = "ConfigSyncService", isDaemon = true) {
            Logger.info("ConfigSyncService started")
            
            while (running) {
                try {
                    // Poll configuration every 250ms (4 times per second as per spec)
                    Thread.sleep(250)
                    
                    syncConfiguration()
                } catch (e: InterruptedException) {
                    Logger.warn("ConfigSyncService interrupted")
                    break
                } catch (e: Exception) {
                    Logger.error("Error syncing configuration", e)
                }
            }
            
            Logger.info("ConfigSyncService stopped")
        }
    }
    
    /**
     * 停止配置同步服务
     */
    fun stop() {
        running = false
        syncThread?.interrupt()
        syncThread = null
    }
    
    /**
     * 同步配置
     */
    private fun syncConfiguration() {
        val context = BinderUtils.getSystemContext() ?: run {
            return
        }
        
        val contentResolver: ContentResolver = context.contentResolver
        
        kotlin.runCatching {
            contentResolver.query(CONTENT_URI, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    // Read configuration from cursor
                    val enableCol = cursor.getColumnIndex("enable")
                    val latCol = cursor.getColumnIndex("latitude")
                    val lonCol = cursor.getColumnIndex("longitude")
                    val altCol = cursor.getColumnIndex("altitude")
                    val speedCol = cursor.getColumnIndex("speed")
                    val bearingCol = cursor.getColumnIndex("bearing")
                    val accuracyCol = cursor.getColumnIndex("accuracy")
                    val movementModeCol = cursor.getColumnIndex("movement_mode")
                    val stepFreqCol = cursor.getColumnIndex("step_frequency")
                    val mockGnssCol = cursor.getColumnIndex("enable_mock_gnss")
                    val mockWifiCol = cursor.getColumnIndex("enable_mock_wifi")
                    val targetPackagesCol = cursor.getColumnIndex("target_packages")
                    
                    // Update FakeLoc configuration
                    if (enableCol >= 0) {
                        FakeLoc.enable = cursor.getInt(enableCol) == 1
                    }
                    if (latCol >= 0) {
                        FakeLoc.latitude = cursor.getDouble(latCol)
                    }
                    if (lonCol >= 0) {
                        FakeLoc.longitude = cursor.getDouble(lonCol)
                    }
                    if (altCol >= 0) {
                        FakeLoc.altitude = cursor.getDouble(altCol)
                    }
                    if (speedCol >= 0) {
                        FakeLoc.speed = cursor.getDouble(speedCol)
                    }
                    if (bearingCol >= 0) {
                        FakeLoc.bearing = cursor.getDouble(bearingCol)
                    }
                    if (accuracyCol >= 0) {
                        FakeLoc.accuracy = cursor.getFloat(accuracyCol)
                    }
                    if (movementModeCol >= 0) {
                        val modeStr = cursor.getString(movementModeCol)
                        FakeLoc.movementMode = when (modeStr) {
                            "WALKING" -> FakeLoc.MovementMode.WALKING
                            "RUNNING" -> FakeLoc.MovementMode.RUNNING
                            "DRIVING" -> FakeLoc.MovementMode.DRIVING
                            else -> FakeLoc.MovementMode.STATIC
                        }
                    }
                    if (stepFreqCol >= 0) {
                        FakeLoc.stepFrequency = cursor.getInt(stepFreqCol)
                    }
                    if (mockGnssCol >= 0) {
                        FakeLoc.enableMockGnss = cursor.getInt(mockGnssCol) == 1
                    }
                    if (mockWifiCol >= 0) {
                        FakeLoc.enableMockWifi = cursor.getInt(mockWifiCol) == 1
                    }
                    if (targetPackagesCol >= 0) {
                        val packagesStr = cursor.getString(targetPackagesCol)
                        if (packagesStr != null && packagesStr.isNotEmpty()) {
                            FakeLoc.targetPackages.clear()
                            FakeLoc.targetPackages.addAll(packagesStr.split(",").filter { it.isNotEmpty() })
                        }
                    }
                }
            }
        }.onFailure {
            // Silently ignore - app may not be installed
        }
    }
}
