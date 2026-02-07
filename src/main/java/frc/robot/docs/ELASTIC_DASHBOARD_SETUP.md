# Elastic Dashboard Setup Guide

This document explains how to use the Elastic Dashboard configuration for the 2025 BabyTaz robot.

## Issue #40 - Missing Elastic Dashboard Data

### Root Cause
The robot code was publishing NetworkTables data to `Elastic/` paths, but the dashboard configuration was referencing old paths like `Vision/HasTarget` instead of `Elastic/Vision/LL4/HasTarget`. This mismatch caused no data to appear in the dashboard.

### Solution
Two configuration files have been created/updated:

1. **`elastic-dashboard.json`** - Comprehensive Elastic Dashboard layout (root directory)
2. **`shuffleboard-config.json`** - Updated with correct paths (src/main/java/frc/robot/controls/)

## Using Elastic Dashboard

### Option 1: Elastic Dashboard Application (Recommended)

1. **Download Elastic Dashboard 2026**
   - Get it from the FRC Game Tools or WPILib releases
   - Or use the one bundled with WPILib

2. **Connect to Robot**
   - Connect your computer to the robot via WiFi or USB
   - Launch Elastic Dashboard
   - It should auto-connect to team number (set in preferences if needed)

3. **Load Dashboard Layout**
   - In Elastic Dashboard, go to **File → Open Layout**
   - Navigate to the project root and select `elastic-dashboard.json`
   - The dashboard will populate with all available NetworkTables data

### Option 2: Use Shuffleboard (Alternative)

If you prefer Shuffleboard:
- Launch Shuffleboard
- Go to **File → Load Layout**
- Select `src/main/java/frc/robot/controls/shuffleboard-config.json`

## Dashboard Tabs Overview

The `elastic-dashboard.json` includes 6 comprehensive tabs:

### 1. **Overview** - Main robot status
- Match information (event, match number, alliance, active status)
- Battery voltage and power monitoring
- Current draw and brownout status
- Latest alerts and cycle time performance
- Auto mode chooser

### 2. **Vision** - AprilTag detection & alignment
- Vision state and mode
- Alignment state (IDLE → SEARCHING → HUNTING → SEEKING → ALIGNING → ALIGNED)
- AprilTag detection (Has Target, Tag ID)
- Target measurements (Distance, Horizontal Offset, Yaw Angle)
- Detection rate statistics
- Real-time graphs for distance, angles, and offsets

### 3. **Power & Performance** - Battery and system health
- Battery voltage with visual gauge
- Current draw and power consumption
- Min voltage tracking and brownout detection
- Robot loop cycle time monitoring
- Historical graphs for voltage, current, and performance

### 4. **Controls** - Driver & operator inputs
- Driver controller (all axes, buttons, triggers, POV)
- Operator controller (all axes, buttons, triggers, POV)
- Real-time visualization of joystick inputs

### 5. **Alerts** - System warnings and messages
- Latest alert message and severity level
- Alert count and timestamp
- Helps diagnose issues during operation

### 6. **Match Data** - Competition information
- Event name and match details
- Match type and number
- Alliance color
- Match timing (start, end, elapsed, duration)

## NetworkTables Data Structure

All robot data is published under the `Elastic/` prefix:

```
Elastic/
├── Power/
│   ├── BatteryVoltage
│   ├── InputCurrent
│   ├── PowerWatts
│   ├── MinVoltage
│   └── BrownedOut
├── Performance/
│   ├── CycleTimeMs
│   └── MaxCycleTimeMs
├── Vision/
│   ├── State
│   ├── Mode
│   ├── AlignmentState
│   ├── LL4/
│   │   ├── HasTarget
│   │   ├── TagID
│   │   ├── Distance_CM
│   │   ├── HorizontalOffset_CM
│   │   ├── YawAngle_Deg
│   │   └── TX_Raw
│   └── Stats/
│       ├── DetectionRate
│       ├── DetectionRatePercent
│       ├── AvgDistance
│       ├── TargetsDetected
│       └── TotalCycles
├── Controls/
│   ├── Driver/
│   │   ├── LeftX, LeftY, RightX, RightY
│   │   ├── LeftTrigger, RightTrigger
│   │   ├── AButton, BButton, XButton, YButton
│   │   ├── LeftBumper, RightBumper
│   │   └── POV
│   └── Operator/
│       └── (same structure as Driver)
├── Alerts/
│   ├── LatestMessage
│   ├── LatestLevel
│   ├── LatestTimestamp
│   └── AlertCount
├── MatchData/
│   ├── EventName
│   ├── MatchNumber
│   ├── MatchType
│   ├── AllianceColor
│   ├── MatchActive
│   ├── StartTime
│   ├── EndTime
│   ├── Duration
│   └── ElapsedTime
└── Autonomous/
    └── AutoChooser
```

## Live Tuning

The robot supports live PID tuning via NetworkTables:
- Tuning parameters are under `Elastic/[Subsystem]/[Parameter]`
- Changes take effect immediately without redeploying code
- Disable in competition mode: `TunableNumber.setTuningEnabled(false)`

Examples:
- `Elastic/Vision/ModelA/Rotation_kP`
- `Elastic/Vision/ModelB/Range_kP`
- `Elastic/Vision/Main/Forward_kP`

## Troubleshooting

### No Data Appearing?

1. **Check Robot Connection**
   - Verify robot is powered on
   - Confirm network connection (WiFi or USB)
   - Look for connection indicator in dashboard

2. **Verify NetworkTables Connection**
   - In Elastic Dashboard, check NetworkTables status
   - Should show "Connected" with your team number
   - If not connected, check team number in preferences

3. **Check Robot Code**
   - Ensure robot code is deployed
   - Verify telemetry classes are initialized:
     - `PowerMonitor`
     - `PerformanceMonitor`
     - `AlertManager`
     - `MatchDataLogger`
     - `ControlsTelemetry`
     - `VisionSubsystem`

4. **CAN Bus Issues** (from issue #40)
   - Physical wiring may need inspection
   - Check CAN termination resistors
   - Verify all devices appear in Phoenix Tuner or REV Hardware Client

5. **Missing Specific Data?**
   - Open OutlineViewer (bundled with WPILib) to see raw NetworkTables
   - Verify data is being published by robot code
   - Check that paths in dashboard config match actual published paths

### Data Works in Shuffleboard but Not Elastic?

- Ensure you're using `elastic-dashboard.json` (not shuffleboard-config.json)
- Elastic Dashboard may use slightly different widget types
- Check Elastic Dashboard version matches WPILib version (2026)

## Customization

To add more widgets or modify layouts:

1. **Using Dashboard Editor**
   - Most Elastic Dashboards have a built-in editor
   - Drag NetworkTables entries from the sources panel
   - Arrange and configure widgets visually
   - Save layout to `elastic-dashboard.json`

2. **Manual JSON Editing**
   - Edit `elastic-dashboard.json` directly
   - Add widgets with proper topic paths
   - Set positions (x, y), size (width, height)
   - Configure widget-specific properties

## Additional Tools

- **AdvantageScope** - Advanced log analysis (supports AdvantageKit data)
- **OutlineViewer** - Raw NetworkTables browser
- **Glass** - WPILib's built-in dashboard (simple but effective)
- **Phoenix Tuner** - CTRE device configuration
- **REV Hardware Client** - REV device configuration

## Files Modified

- **`elastic-dashboard.json`** (NEW) - Main Elastic Dashboard configuration
- **`src/main/java/frc/robot/controls/shuffleboard-config.json`** (UPDATED) - Fixed NetworkTables paths

## References

- [Elastic Dashboard Documentation](https://docs.wpilib.org/en/stable/docs/software/dashboards/elastic-dashboard.html)
- [NetworkTables Documentation](https://docs.wpilib.org/en/stable/docs/software/networktables/index.html)
- [Issue #40](https://github.com/CyberCoyotes/2025-BabyTaz/issues/40)
