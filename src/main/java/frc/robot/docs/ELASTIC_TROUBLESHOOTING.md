# Elastic Dashboard 2026 Troubleshooting Checklist

## Current Status
- ✅ Robot code publishes to NetworkTables under `Elastic/` namespace
- ✅ Shuffleboard can see and display NetworkTables data
- ❌ Elastic Dashboard 2026 shows no data

## Diagnostic Steps

### 1. Verify Robot Connection
- [ ] Robot code is deployed and running
- [ ] Robot is on the same network as your driver station
- [ ] Can ping robot: `ping 10.TE.AM.2` (replace with your team number)

### 2. Check Shuffleboard Data (Baseline Test)
Open Shuffleboard and verify you can see:
- [ ] `Elastic/Power/BatteryVoltage` - updating in real-time
- [ ] `Elastic/Performance/CycleTimeMs` - updating in real-time
- [ ] `Elastic/MatchData/EventName` - shows a value
- [ ] `Elastic/Controls/Driver/LeftX` - changes when you move joystick
- [ ] `Elastic/Vision/LL4/HasTarget` - exists (may be true/false)

If you CAN'T see these in Shuffleboard, the issue is with robot code publishing, not Elastic.

### 3. Elastic Dashboard Connection Check

#### Open Elastic Dashboard 2026:
- [ ] Application launches successfully
- [ ] Check menu: `File` → `Preferences` or `Settings`
- [ ] Find NetworkTables connection settings:
  - Team Number: _____ (your team number)
  - Server Mode: Should be "Client" (connecting TO robot)
  - Server Address: Should auto-populate from team number

#### Connection Status:
- [ ] Look for connection indicator (usually top-right or bottom-right)
- [ ] Status should show "Connected" with green indicator
- [ ] If disconnected, check:
  - Firewall blocking NetworkTables port (1735)
  - Incorrect team number
  - Robot not on network

### 4. NetworkTables Test (Advanced)

If Shuffleboard works but Elastic doesn't, try:

**Option A: OutlineViewer Tool**
1. Open WPILib OutlineViewer (comes with WPILib installation)
2. Connect to robot (same team number)
3. Expand `Elastic/` tree
4. Verify all subtables exist: Power, Vision, Controls, etc.

**Option B: Check NetworkTables Version Compatibility**
- Elastic 2026 might require NetworkTables 4.x
- Shuffleboard might be using NetworkTables 3.x
- Check if there's a protocol version mismatch

### 5. Create Elastic Layout

Once connected, Elastic won't auto-populate widgets. You need to:

1. **Add widgets manually:**
   - Right-click on canvas → Add Widget
   - Browse NetworkTables → Elastic → Power → BatteryVoltage
   - Drag to dashboard

2. **Or create a layout file:**
   - Check if Elastic supports JSON layout import
   - Look for example layouts in Elastic documentation

3. **Save your layout:**
   - File → Save Layout
   - Elastic should remember this for next time

## Data Being Published (Reference)

Your robot code publishes to these NetworkTables paths:

### High-Priority Data (Good for initial testing):
- `Elastic/Power/BatteryVoltage` - Real-time battery voltage
- `Elastic/Performance/CycleTimeMs` - Loop cycle time
- `Elastic/Controls/Driver/LeftX` - Driver joystick X-axis

### All Available Data:
```
Elastic/
├── Power/ (BatteryVoltage, InputCurrent, PowerWatts, MinVoltage, BrownedOut)
├── Performance/ (CycleTimeMs, MaxCycleTimeMs)
├── Controls/Driver/ (LeftX, LeftY, RightX, RightY, buttons, triggers, POV)
├── Controls/Operator/ (same as Driver)
├── MatchData/ (MatchNumber, MatchType, EventName, AllianceColor, timing)
├── Alerts/ (Alert_1, Alert_2, LatestMessage, AlertCount)
├── Vision/LL4/ (HasTarget, TagID, Distance_CM, HorizontalOffset_CM, YawAngle_Deg)
├── Vision/Stats/ (DetectionRate, AvgDistance, TargetsDetected)
├── VisionTest/ (Model buttons, ActiveModel, Status, TX, Distance, Aligned)
└── PoseFusion/ (Enabled, Status, LastRejectReason, SentHeading)
```

## Common Issues & Solutions

### Issue: "Elastic shows blank screen"
**Cause:** No layout configured
**Solution:** Add widgets manually or import layout

### Issue: "Connection indicator shows red/disconnected"
**Cause:** Can't find NetworkTables server
**Solutions:**
- Verify team number in settings
- Check robot is on network (`ping 10.TE.AM.2`)
- Disable firewall temporarily to test
- Check if robot code is actually running

### Issue: "Some data appears but not others"
**Cause:** Robot code not publishing that data OR widgets not added
**Solution:**
- Verify in OutlineViewer that data exists in NetworkTables
- Add missing widgets to Elastic layout

### Issue: "Data appears stale/not updating"
**Cause:** Robot code not running or CAN bus errors preventing sensor reads
**Solution:**
- Check Driver Station for robot code status
- Check for CAN errors (you mentioned wiring issue)
- Verify sensors are connected

## Next Steps

1. Follow diagnostic steps above in order
2. Document where the connection fails
3. If Shuffleboard works but Elastic doesn't, it's likely:
   - Connection settings in Elastic are wrong, OR
   - Elastic requires manual widget configuration
4. Check Elastic Dashboard 2026 documentation for:
   - How to configure team number
   - How to add NetworkTables widgets
   - Example layout files

## Resources

- WPILib Docs: https://docs.wpilib.org/
- NetworkTables documentation
- Elastic Dashboard GitHub (if open source)
- Chief Delphi forums for FRC programming help
