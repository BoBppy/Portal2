# Portal2 Implementation Details

## Enhanced Location Spoofing Architecture

This document describes the implementation of the enhanced location spoofing system based on the technical specification.

## Architecture Overview

The system consists of three main components:

1. **Control App** (User Interface Layer)
2. **Xposed Module** (System Hooks Layer) 
3. **IPC Communication** (ContentProvider)

```
┌─────────────────────────────────────────────────────────────┐
│                       Control App                            │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐  │
│  │  Map UI      │    │  Joystick    │    │  Settings    │  │
│  └──────────────┘    └──────────────┘    └──────────────┘  │
│            │                 │                    │          │
│            └─────────────────┴────────────────────┘          │
│                              │                               │
│                    ┌─────────▼──────────┐                    │
│                    │  ConfigHelper      │                    │
│                    └─────────┬──────────┘                    │
│                              │                               │
│                    ┌─────────▼──────────┐                    │
│                    │ ContentProvider    │                    │
│                    │  (IPC Interface)   │                    │
└────────────────────┴────────────────────┴────────────────────┘
                              ▲
                              │ Query every 250ms
                              │
┌─────────────────────────────┼─────────────────────────────────┐
│                 system_server Process                         │
│                    ┌─────────▼──────────┐                     │
│                    │  ConfigSyncService │                     │
│                    └─────────┬──────────┘                     │
│                              │                                │
│                    ┌─────────▼──────────┐                     │
│                    │     FakeLoc        │                     │
│                    │  (Configuration)   │                     │
│                    └─────────┬──────────┘                     │
│                              │                                │
│         ┌────────────────────┼────────────────────┐          │
│         │                    │                    │          │
│  ┌──────▼──────┐   ┌─────────▼────────┐  ┌───────▼──────┐  │
│  │ Location    │   │   Telephony      │  │   WiFi       │  │
│  │   Hooks     │   │     Hooks        │  │   Hooks      │  │
│  └─────────────┘   └──────────────────┘  └──────────────┘  │
│         │                    │                    │          │
│  ┌──────▼──────┐   ┌─────────▼────────┐  ┌───────▼──────┐  │
│  │   GNSS      │   │    Sensor        │  │   NMEA       │  │
│  │   Hooks     │   │    Hooks         │  │   Hooks      │  │
│  └─────────────┘   └──────────────────┘  └──────────────┘  │
└───────────────────────────────────────────────────────────────┘
```

## Key Features Implemented

### 1. UID-Based Selective Spoofing

**Location**: `xposed/src/main/java/moe/fuqiuluo/xposed/utils/FakeLoc.kt`

```kotlin
/**
 * Target application packages for selective spoofing
 * Empty set = spoof all non-system apps
 */
var targetPackages = mutableSetOf<String>()

/**
 * Check if a UID should be spoofed
 */
fun shouldSpoofUid(uid: Int): Boolean {
    if (!enable) return false
    if (BinderUtils.isSystemAppsCall(uid)) return false
    if (targetPackages.isEmpty()) return true
    
    val packageNames = BinderUtils.getUidPackageNames(uid = uid) ?: return false
    return packageNames.any { it in targetPackages }
}
```

**Usage in hooks**:
```kotlin
// Example: In LocationServiceHook.kt
if (!FakeLoc.shouldSpoofUid(BinderUtils.getCallerUid())) {
    return@beforeHook
}
```

### 2. Sensor Data Simulation

**Location**: `xposed/src/main/java/moe/fuqiuluo/xposed/utils/SensorSimulator.kt`

Generates realistic sensor data for different movement modes:

- **STATIC**: Gravity + minimal noise
- **WALKING**: Periodic sine wave at step frequency (e.g., 120 steps/min = 2Hz)
- **RUNNING**: Higher amplitude and frequency  
- **DRIVING**: Low-frequency vibration patterns

**Accelerometer Data Generation**:
```kotlin
fun generateAccelerometerData(
    movementMode: FakeLoc.MovementMode = FakeLoc.movementMode,
    stepFrequency: Int = FakeLoc.stepFrequency
): FloatArray
```

**Integration**: Hooks in `SystemSensorManagerHook.kt` inject this data into `onSensorChanged` callbacks.

### 3. ContentProvider IPC

**App Side**: `app/src/main/java/moe/fuqiuluo/portal/provider/LocationConfigProvider.kt`

Exposes configuration via standard Android ContentProvider:
- Authority: `moe.fuqiuluo.portal.provider.location`
- URI: `content://moe.fuqiuluo.portal.provider.location/config`

**Xposed Side**: `xposed/src/main/java/moe/fuqiuluo/xposed/ipc/ConfigSyncService.kt`

Background service that:
- Polls ContentProvider every 250ms (4 times/second)
- Updates FakeLoc configuration in real-time
- Runs in system_server process

**Configuration Parameters**:
- `enable`: Boolean - Master switch
- `latitude`, `longitude`, `altitude`: Location coordinates
- `speed`, `bearing`, `accuracy`: Movement parameters
- `movement_mode`: String - "STATIC", "WALKING", "RUNNING", "DRIVING"
- `step_frequency`: Int - Steps per minute for sensor simulation
- `enable_mock_gnss`: Boolean - Mock satellite data
- `enable_mock_wifi`: Boolean - Mock WiFi scan results
- `target_packages`: String - Comma-separated package names

### 4. Helper API

**Location**: `app/src/main/java/moe/fuqiuluo/portal/provider/ConfigHelper.kt`

Provides easy-to-use methods for updating configuration:

```kotlin
// Update location
ConfigHelper.updateLocation(context, 39.9042, 116.4074, altitude = 80.0)

// Enable/disable spoofing
ConfigHelper.setEnabled(context, true)

// Set movement mode
ConfigHelper.setMovementMode(context, "WALKING", stepFrequency = 120)

// Set target apps
ConfigHelper.setTargetPackages(context, setOf("com.example.app"))
```

## Existing Hook Points

The following hooks were already implemented in the original codebase:

### Location Hooks

1. **LocationServiceHook** (`hooks/LocationServiceHook.kt`)
   - `getLastLocation()` - afterHook
   - `requestLocationUpdates()` - beforeHook
   - `registerLocationListener()` - beforeHook
   - `getCurrentLocation()` - beforeHook
   - `registerGnssStatusCallback()` - afterHook

2. **LocationProviderManagerHook** (`hooks/provider/LocationProviderManagerHook.kt`)
   - `reportLocation()` - beforeHook (in various provider classes)
   - `onReportLocation()` - beforeHook
   - `getCurrentLocation()` - beforeHook

3. **LocationManagerHook** (`hooks/LocationManagerHook.kt`)
   - `getLastKnownLocation()` - afterHook
   - `onLocationChanged()` - beforeHook (in listeners)

### Telephony Hooks

**TelephonyHook** (`hooks/telephony/TelephonyHook.kt`)
- `getAllCellInfo()` - afterHook
- `getCellLocation()` - afterHook
- `getNeighboringCellInfo()` - beforeHook
- `notifyCellInfo()` - beforeHook
- Returns fake CDMA cell tower data

### WiFi Hooks

**WlanHook** (`hooks/wlan/WlanHook.kt`)
- `getScanResults()` - afterHook
- `getConnectionInfo()` - beforeHook
- Returns empty scan results when spoofing enabled

### GNSS Hooks

**LocationServiceHook** - `onSvStatusChanged()` beforeHook
- Generates mock BeiDou satellite constellation
- Simulates realistic C/N0 values (20-45 dB-Hz)
- Includes Gaussian jitter for signal strength
- Supports GPS, GLONASS, Galileo, BeiDou

### NMEA Hooks

**BaseLocationHook** - `injectNMEA()` method
- Parses and modifies NMEA sentences
- Supports: GGA, GNS, RMC, VTG, GSA, GSV, DTM
- Replaces coordinates while maintaining format

## Configuration Flow

1. User updates settings in Control App UI
2. App calls `ConfigHelper.updateConfig()` 
3. ConfigHelper updates SharedPreferences and notifies ContentProvider
4. ConfigSyncService in system_server queries ContentProvider (every 250ms)
5. ConfigSyncService updates FakeLoc singleton
6. All hooks use FakeLoc configuration to spoof data

## Detection Avoidance

### Level 1: API Detection
- ✅ `isMock` / `isFromMockProvider` always returns `false` when `FakeLoc.hideMock = true`

### Level 2: Permission Detection  
- ✅ No mock location permissions required
- ✅ All hooks run in system_server, not app process

### Level 3: Consistency Detection
- ✅ GPS coordinates match WiFi/Cell tower data (when database implemented)
- ✅ Sensor data matches movement speed (via SensorSimulator)
- ✅ GNSS satellite data realistic with C/N0 jitter

## Usage Example

```kotlin
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable spoofing
        ConfigHelper.setEnabled(this, true)
        
        // Set location to New York City
        ConfigHelper.updateLocation(
            context = this,
            latitude = 40.7128,
            longitude = -74.0060,
            altitude = 10.0
        )
        
        // Set walking mode
        ConfigHelper.setMovementMode(
            context = this,
            mode = "WALKING",
            stepFrequency = 120
        )
        
        // Only spoof specific apps
        ConfigHelper.setTargetPackages(
            context = this,
            packages = setOf("com.example.app1", "com.example.app2")
        )
    }
}
```

## Future Enhancements

1. **Database Support**: Add SQLite database for WiFi BSSID and Cell tower data
2. **NMEA Generator**: Complete NMEA sentence generator with all sentence types
3. **Route Simulation**: GPX file import and playback
4. **UI Improvements**: Add configuration UI for all new features
5. **Network Location**: Implement realistic WiFi/Cell data injection from database

## Testing Recommendations

1. Test IPC communication between app and xposed module
2. Verify sensor data generation for all movement modes
3. Test selective spoofing with target package list
4. Verify `isMock` always returns false
5. Test C/N0 jitter in satellite data
6. Validate NMEA sentence generation

## Security Considerations

- ContentProvider is exported to allow system_server access
- Configuration stored in SharedPreferences (consider encryption)
- UID-based filtering prevents system apps from being spoofed
- Sensor simulation only active when movement mode != STATIC
