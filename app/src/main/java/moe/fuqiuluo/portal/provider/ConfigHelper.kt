package moe.fuqiuluo.portal.provider

import android.content.ContentValues
import android.content.Context

/**
 * Helper class for updating location configuration via ContentProvider
 */
object ConfigHelper {
    
    /**
     * Update the location configuration
     */
    fun updateConfig(
        context: Context,
        enable: Boolean? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        altitude: Double? = null,
        speed: Double? = null,
        bearing: Double? = null,
        accuracy: Float? = null,
        movementMode: String? = null,
        stepFrequency: Int? = null,
        enableMockGnss: Boolean? = null,
        enableMockWifi: Boolean? = null,
        targetPackages: Set<String>? = null
    ): Boolean {
        val values = ContentValues().apply {
            enable?.let { put(LocationConfigProvider.COL_ENABLE, it) }
            latitude?.let { put(LocationConfigProvider.COL_LATITUDE, it) }
            longitude?.let { put(LocationConfigProvider.COL_LONGITUDE, it) }
            altitude?.let { put(LocationConfigProvider.COL_ALTITUDE, it) }
            speed?.let { put(LocationConfigProvider.COL_SPEED, it) }
            bearing?.let { put(LocationConfigProvider.COL_BEARING, it) }
            accuracy?.let { put(LocationConfigProvider.COL_ACCURACY, it) }
            movementMode?.let { put(LocationConfigProvider.COL_MOVEMENT_MODE, it) }
            stepFrequency?.let { put(LocationConfigProvider.COL_STEP_FREQUENCY, it) }
            enableMockGnss?.let { put(LocationConfigProvider.COL_ENABLE_MOCK_GNSS, it) }
            enableMockWifi?.let { put(LocationConfigProvider.COL_ENABLE_MOCK_WIFI, it) }
            targetPackages?.let { put(LocationConfigProvider.COL_TARGET_PACKAGES, it.joinToString(",")) }
        }
        
        val updated = context.contentResolver.update(
            LocationConfigProvider.CONTENT_URI,
            values,
            null,
            null
        )
        
        return updated > 0
    }
    
    /**
     * Update location coordinates
     */
    fun updateLocation(
        context: Context,
        latitude: Double,
        longitude: Double,
        altitude: Double = 80.0
    ) = updateConfig(
        context = context,
        latitude = latitude,
        longitude = longitude,
        altitude = altitude
    )
    
    /**
     * Enable/disable location spoofing
     */
    fun setEnabled(context: Context, enabled: Boolean) = updateConfig(
        context = context,
        enable = enabled
    )
    
    /**
     * Set movement mode
     */
    fun setMovementMode(
        context: Context,
        mode: String,
        stepFrequency: Int = 120
    ) = updateConfig(
        context = context,
        movementMode = mode,
        stepFrequency = stepFrequency
    )
    
    /**
     * Set target packages for selective spoofing
     */
    fun setTargetPackages(
        context: Context,
        packages: Set<String>
    ) = updateConfig(
        context = context,
        targetPackages = packages
    )
}
