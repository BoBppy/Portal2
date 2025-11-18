package moe.fuqiuluo.portal.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

/**
 * ContentProvider用于在控制App和Xposed模块之间进行IPC通信
 * Xposed模块运行在system_server进程中，通过查询此ContentProvider获取最新的位置配置
 */
class LocationConfigProvider : ContentProvider() {
    
    companion object {
        const val AUTHORITY = "moe.fuqiuluo.portal.provider.location"
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/config")
        
        private const val CONFIG = 1
        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, "config", CONFIG)
        }
        
        // Column names for the configuration cursor
        const val COL_ENABLE = "enable"
        const val COL_LATITUDE = "latitude"
        const val COL_LONGITUDE = "longitude"
        const val COL_ALTITUDE = "altitude"
        const val COL_SPEED = "speed"
        const val COL_BEARING = "bearing"
        const val COL_ACCURACY = "accuracy"
        const val COL_MOVEMENT_MODE = "movement_mode"
        const val COL_STEP_FREQUENCY = "step_frequency"
        const val COL_ENABLE_MOCK_GNSS = "enable_mock_gnss"
        const val COL_ENABLE_MOCK_WIFI = "enable_mock_wifi"
        const val COL_TARGET_PACKAGES = "target_packages"
    }
    
    override fun onCreate(): Boolean {
        return true
    }
    
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        return when (uriMatcher.match(uri)) {
            CONFIG -> {
                val context = context ?: return null
                val prefs = context.getSharedPreferences("portal_config", android.content.Context.MODE_PRIVATE)
                
                // Create cursor with current configuration
                val cursor = MatrixCursor(arrayOf(
                    COL_ENABLE,
                    COL_LATITUDE,
                    COL_LONGITUDE,
                    COL_ALTITUDE,
                    COL_SPEED,
                    COL_BEARING,
                    COL_ACCURACY,
                    COL_MOVEMENT_MODE,
                    COL_STEP_FREQUENCY,
                    COL_ENABLE_MOCK_GNSS,
                    COL_ENABLE_MOCK_WIFI,
                    COL_TARGET_PACKAGES
                ))
                
                cursor.addRow(arrayOf(
                    if (prefs.getBoolean("enable", false)) 1 else 0,
                    prefs.getDouble("latitude", 0.0),
                    prefs.getDouble("longitude", 0.0),
                    prefs.getDouble("altitude", 80.0),
                    prefs.getDouble("speed", 3.05),
                    prefs.getDouble("bearing", 0.0),
                    prefs.getFloat("accuracy", 25.0f),
                    prefs.getString("movement_mode", "STATIC") ?: "STATIC",
                    prefs.getInt("step_frequency", 120),
                    if (prefs.getBoolean("enable_mock_gnss", false)) 1 else 0,
                    if (prefs.getBoolean("enable_mock_wifi", false)) 1 else 0,
                    prefs.getStringSet("target_packages", emptySet())?.joinToString(",") ?: ""
                ))
                
                cursor
            }
            else -> null
        }
    }
    
    override fun getType(uri: Uri): String? {
        return when (uriMatcher.match(uri)) {
            CONFIG -> "vnd.android.cursor.item/vnd.$AUTHORITY.config"
            else -> null
        }
    }
    
    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        // Not supported
        return null
    }
    
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        // Not supported
        return 0
    }
    
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        return when (uriMatcher.match(uri)) {
            CONFIG -> {
                val context = context ?: return 0
                val prefs = context.getSharedPreferences("portal_config", android.content.Context.MODE_PRIVATE)
                val editor = prefs.edit()
                
                values?.let { cv ->
                    cv.getAsBoolean("enable")?.let { editor.putBoolean("enable", it) }
                    cv.getAsDouble("latitude")?.let { editor.putDouble("latitude", it) }
                    cv.getAsDouble("longitude")?.let { editor.putDouble("longitude", it) }
                    cv.getAsDouble("altitude")?.let { editor.putDouble("altitude", it) }
                    cv.getAsDouble("speed")?.let { editor.putDouble("speed", it) }
                    cv.getAsDouble("bearing")?.let { editor.putDouble("bearing", it) }
                    cv.getAsFloat("accuracy")?.let { editor.putFloat("accuracy", it) }
                    cv.getAsString("movement_mode")?.let { editor.putString("movement_mode", it) }
                    cv.getAsInteger("step_frequency")?.let { editor.putInt("step_frequency", it) }
                    cv.getAsBoolean("enable_mock_gnss")?.let { editor.putBoolean("enable_mock_gnss", it) }
                    cv.getAsBoolean("enable_mock_wifi")?.let { editor.putBoolean("enable_mock_wifi", it) }
                    cv.getAsString("target_packages")?.let { 
                        val packages = it.split(",").filter { pkg -> pkg.isNotEmpty() }.toSet()
                        editor.putStringSet("target_packages", packages)
                    }
                }
                
                editor.apply()
                context.contentResolver.notifyChange(uri, null)
                1
            }
            else -> 0
        }
    }
}

// Extension function for SharedPreferences.Editor
private fun android.content.SharedPreferences.Editor.putDouble(key: String, value: Double): android.content.SharedPreferences.Editor {
    return putLong(key, java.lang.Double.doubleToRawLongBits(value))
}

// Extension function for SharedPreferences
private fun android.content.SharedPreferences.getDouble(key: String, defaultValue: Double): Double {
    return java.lang.Double.longBitsToDouble(getLong(key, java.lang.Double.doubleToRawLongBits(defaultValue)))
}
