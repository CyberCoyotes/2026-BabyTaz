# Live Vision Tuning Guide

This guide explains how to tune your AprilTag alignment PID values in real-time using SmartDashboard or Shuffleboard **without redeploying code**.

## Overview

The tuning system allows you to:
- Adjust all PID gains (kP, kI, kD) while the robot is running
- Change speed limits and tolerances on the fly
- Modify target distances and other parameters
- Test changes immediately and iterate rapidly
- Save successful configurations back to code

## Quick Start

### Option 1: SmartDashboard (Simple)

1. **Deploy your code** to the robot
2. **Open SmartDashboard** (comes with WPILib)
3. **Connect to robot** (10.TE.AM.2 or USB)
4. **View → Add** → Navigate to the `Tuning` table
5. **Expand folders** to see all tunable parameters organized by model:
   - `Vision/ModelA/` - Rotation only parameters
   - `Vision/ModelB/` - Rotation + Range parameters
   - `Vision/ModelC/` - Full 3-axis parameters
   - `Vision/ModelD/` - Color blob hunt parameters
   - `Vision/Main/` - AlignToAprilTagCommand parameters

6. **Edit values** by double-clicking and typing new numbers
7. **Test immediately** - changes take effect on next command execution
8. **Record good values** in a notebook or spreadsheet

### Option 2: Shuffleboard (Advanced, Recommended)

Shuffleboard provides a better UI with sliders, graphs, and custom layouts.

1. **Deploy your code** to the robot
2. **Open Shuffleboard** (comes with WPILib)
3. **Connect to robot**
4. **Import the layout**:
   - File → Load Layout
   - Select `shuffleboard_vision_tuning.json` from your project root
   - This gives you pre-configured sliders and displays

5. **Use the tabs**:
   - **Model A - Rotation**: Tune rotation-only alignment
   - **Model B - Rot+Range**: Tune 2-axis alignment
   - **Model C - Full 3-Axis**: Tune perpendicular alignment

6. **Adjust sliders** in real-time while running alignment commands
7. **Watch telemetry** panels to see immediate effects

## Available Parameters

### Model A: Rotation Only

Located in `Tuning/Vision/ModelA/`:

| Parameter | Description | Default | Typical Range |
|-----------|-------------|---------|---------------|
| `Rotation_kP` | Proportional gain | 0.05 | 0.02 - 0.15 |
| `Rotation_kI` | Integral gain | 0.0 | 0.0 - 0.01 |
| `Rotation_kD` | Derivative gain | 0.002 | 0.0 - 0.05 |
| `MaxRotationSpeed` | Max rotation rate (rad/s) | 2.0 | 0.5 - 4.0 |
| `MinRotationSpeed` | Min speed to overcome friction | 0.20 | 0.05 - 0.5 |
| `RotationTolerance_deg` | Success tolerance (degrees) | 1.5 | 0.5 - 5.0 |

### Model B: Rotation + Range

Located in `Tuning/Vision/ModelB/`:

**Rotation:**
- `Rotation_kP`, `Rotation_kI`, `Rotation_kD`
- `MaxRotationSpeed`, `RotationTolerance_deg`

**Range (Forward/Back):**
- `Range_kP` (default: 1.0, range: 0.5 - 3.0)
- `Range_kI` (default: 0.0)
- `Range_kD` (default: 0.05, range: 0.0 - 0.5)
- `MaxForwardSpeed` (default: 0.8 m/s)
- `DistanceTolerance_m` (default: 0.05 m)
- `TargetDistance_m` (default: 0.75 m, range: 0.3 - 2.0 m)

### Model C: Full 3-Axis Perpendicular

Located in `Tuning/Vision/ModelC/`:

**All parameters from Model B, plus:**

**Lateral (Left/Right Strafe):**
- `Lateral_kP` (default: 0.04, range: 0.01 - 0.15)
- `Lateral_kI`, `Lateral_kD`
- `MaxLateralSpeed` (default: 0.5 m/s)
- `LateralTolerance_deg` (default: 2.0°)
- `LateralDeadband_deg` (default: 3.0°, prevents drift from tiny corrections)

### Model D: Color Blob Hunt

Located in `Tuning/Vision/ModelD/`:

Hunt mode, seek mode, range PID, and color detection thresholds.

### Camera Configuration

Located in `Tuning/Vision/Camera/`:
- `Height_m` - Camera height above floor
- `Angle_deg` - Camera tilt angle
- `LateralOffset_m` - Left/right offset from robot center
- `LongitudinalOffset_m` - Forward/back offset

**Note**: Camera changes affect distance calculations. Only adjust if you move the camera.

## Tuning Workflow

### Step 1: Prepare for Tuning

1. **Deploy code** with tuning enabled (it's enabled by default)
2. **Connect SmartDashboard or Shuffleboard**
3. **Place AprilTag** in a known location (e.g., on a wall)
4. **Position robot** ~1-2 meters away at an angle
5. **Enable robot** and be ready to disable quickly if needed

### Step 2: Baseline Test

1. **Run alignment command** (e.g., press Model A button in Shuffleboard)
2. **Observe behavior**:
   - Does it align? How fast?
   - Does it overshoot?
   - Does it oscillate?
   - Does it get stuck before reaching target?

3. **Record baseline performance**:
   - Time to align
   - Final error
   - Smoothness (1-10 rating)
   - Any issues (overshoot, oscillation, stuck, etc.)

### Step 3: Iterative Tuning

Use the **Ziegler-Nichols-inspired approach** for PID tuning:

#### For Rotation (or any axis):

**A. Start with P-only control:**
1. Set `kI = 0` and `kD = 0`
2. Set `kP` to a low value (e.g., 0.02)
3. Test alignment
4. **Gradually increase kP** until:
   - Robot responds quickly BUT
   - Just starts to overshoot or oscillate slightly
5. **Back off kP by 20-30%** for stability

**B. Add D gain if needed:**
1. If you see overshoot or oscillation:
   - Start with `kD = kP / 10`
   - Increase gradually until oscillation dampens
2. D gain helps with:
   - Reducing overshoot
   - Smoothing motion
   - Preventing oscillation

**C. Add I gain if needed (rarely):**
1. Only if you have **steady-state error** (robot stops before reaching target)
2. Start very small: `kI = 0.001`
3. Increase slowly until error eliminates
4. **Warning**: Too much I causes:
   - Integral windup
   - Overshoot
   - Slow, sluggish response

**D. Adjust speed limits:**
- If motion is too aggressive, reduce `MaxSpeed`
- If robot gets stuck near target, increase `MinSpeed` (Model A only)

**E. Adjust tolerance:**
- Tighter tolerance (lower value) = more precise but slower
- Looser tolerance (higher value) = faster but less accurate
- Recommended: 1.5-2.0° for rotation, 5-10 cm for distance

### Step 4: Record and Test

After each change:
1. **Test 3-5 times** from different starting positions
2. **Record metrics**:
   - Time to align
   - Success rate
   - Overshoot %
   - Oscillations
3. **Compare to baseline**

### Step 5: Multi-Axis Coordination (Model C)

For 3-axis alignment:
1. **Tune rotation first** (Model A)
2. **Tune rotation + range** (Model B)
3. **Finally add lateral** (Model C)

Watch for **axis coupling** (one axis affecting another). If coupling occurs:
- Reduce gains on the interfering axis
- Add deadbands to prevent tiny corrections
- Consider limiting max speeds

## Example Tuning Session

### Scenario: Model A rotation is too slow and gets stuck near target

**Problem**: Robot rotates slowly and stops 3° away from target

**Diagnosis**:
- `kP` too low (not enough power)
- `MinRotationSpeed` too low (can't overcome friction)

**Solution**:
1. Increase `Rotation_kP` from 0.05 to 0.07
   - Test: Faster, but still stops short
2. Increase `MinRotationSpeed` from 0.20 to 0.30
   - Test: Success! Reaches target
3. Check for overshoot
   - Test: Slight overshoot (~2°)
4. Add damping with `Rotation_kD` from 0.002 to 0.005
   - Test: Perfect! No overshoot, smooth approach

**Final values**:
- `Rotation_kP = 0.07`
- `Rotation_kD = 0.005`
- `MinRotationSpeed = 0.30`

**Record in notebook, update VisionConstants.java later**

## Common Issues and Solutions

### Issue: Robot oscillates back and forth

**Causes**:
- `kP` too high
- `kD` too low or zero

**Solutions**:
1. Reduce `kP` by 20-30%
2. Add or increase `kD` (try `kD = kP / 10` as starting point)
3. Increase tolerance slightly to avoid hunting behavior

### Issue: Robot overshoots target

**Causes**:
- `kP` too high
- `kD` too low

**Solutions**:
1. Reduce `kP`
2. Increase `kD`
3. Reduce `MaxSpeed`

### Issue: Robot stops before reaching target (steady-state error)

**Causes**:
- `kP` too low
- `MinSpeed` too low (Model A)
- Friction/dead zone in motors

**Solutions**:
1. Increase `kP`
2. Increase `MinSpeed` (Model A only)
3. Add small `kI` (start with 0.001)

### Issue: Robot responds too aggressively/jerky

**Causes**:
- `kP` too high
- `MaxSpeed` too high
- `kD` too high

**Solutions**:
1. Reduce `kP`
2. Reduce `MaxSpeed`
3. If kD is high (>0.05), reduce it

### Issue: Robot takes forever to align

**Causes**:
- `kP` too low
- `MaxSpeed` too low
- Tolerance too tight

**Solutions**:
1. Increase `kP`
2. Increase `MaxSpeed`
3. Relax tolerance slightly

### Issue: Alignment works sometimes but not others

**Causes**:
- Vision detection intermittent (lighting, distance, angle)
- Target lost during alignment

**Solutions**:
- Check Limelight web interface for vision quality
- Ensure AprilTag is well-lit and in camera view
- Reduce max speeds to avoid camera motion blur
- Check `Vision/HasTarget` in telemetry

## Saving Your Tuned Values

Once you've found good PID values:

### Option 1: Manual Update

1. **Open `VisionConstants.java`**
2. **Find the appropriate model section** (ModelA, ModelB, ModelC, etc.)
3. **Update the constants**:
   ```java
   public static final class ModelA {
       public static final double ROTATION_KP = 0.07;  // Updated from 0.05
       public static final double ROTATION_KD = 0.005; // Updated from 0.002
       public static final double MIN_ROTATION_SPEED_RADPS = 0.30; // Updated from 0.20
       // ...
   }
   ```
4. **Update TunableVisionConstants.java** to match (changes default values):
   ```java
   public static final TunableNumber ROTATION_KP =
       new TunableNumber("Vision/ModelA/Rotation_kP", 0.07); // Updated
   ```
5. **Commit changes** to git with a descriptive message

### Option 2: Export from Dashboard

SmartDashboard can save NetworkTables to XML:
1. **File → Save NetworkTables**
2. **Compare values** in XML to your constants
3. **Manually update** code as in Option 1

## Using D-Pad for Coarse Tuning

You mentioned using the D-pad before. Here's how to bind D-pad buttons to increment/decrement values:

### Example: Add to `RobotContainer.java`

```java
// D-pad up: Increase rotation kP by 0.005
driverController.povUp().onTrue(Commands.runOnce(() -> {
    double current = TunableVisionConstants.ModelA.ROTATION_KP.get();
    TunableVisionConstants.ModelA.ROTATION_KP.set(current + 0.005);
    System.out.println("Rotation kP: " + TunableVisionConstants.ModelA.ROTATION_KP.get());
}));

// D-pad down: Decrease rotation kP by 0.005
driverController.povDown().onTrue(Commands.runOnce(() -> {
    double current = TunableVisionConstants.ModelA.ROTATION_KP.get();
    TunableVisionConstants.ModelA.ROTATION_KP.set(current - 0.005);
    System.out.println("Rotation kP: " + TunableVisionConstants.ModelA.ROTATION_KP.get());
}));

// D-pad left: Decrease rotation kD by 0.001
driverController.povLeft().onTrue(Commands.runOnce(() -> {
    double current = TunableVisionConstants.ModelA.ROTATION_KD.get();
    TunableVisionConstants.ModelA.ROTATION_KD.set(current - 0.001);
    System.out.println("Rotation kD: " + TunableVisionConstants.ModelA.ROTATION_KD.get());
}));

// D-pad right: Increase rotation kD by 0.001
driverController.povRight().onTrue(Commands.runOnce(() -> {
    double current = TunableVisionConstants.ModelA.ROTATION_KD.get();
    TunableVisionConstants.ModelA.ROTATION_KD.set(current + 0.001);
    System.out.println("Rotation kD: " + TunableVisionConstants.ModelA.ROTATION_KD.get());
}));
```

This allows you to tune without a laptop, using just the controller!

## Disabling Tuning for Competition

Before competition, disable tuning to ensure consistent behavior:

### Option 1: In `Robot.java` `robotInit()`:

```java
@Override
public void robotInit() {
    // Disable tuning mode - all TunableNumbers return default values
    TunableNumber.setTuningEnabled(false);

    // ... rest of init
}
```

### Option 2: Conditional based on FMS connection:

```java
@Override
public void robotPeriodic() {
    // Disable tuning when connected to FMS (official matches)
    if (DriverStation.isFMSAttached()) {
        TunableNumber.setTuningEnabled(false);
    } else {
        TunableNumber.setTuningEnabled(true);
    }
}
```

## Integration with Log Analysis

Combine live tuning with the log analysis tool for best results:

1. **Tune PID values** using SmartDashboard/Shuffleboard
2. **Record a log** of alignment attempts
3. **Analyze log** with `analyze_alignment.py`:
   ```bash
   python analyze_alignment.py test.wpilog --model A --plot
   ```
4. **Review metrics**: alignment time, overshoot, oscillations
5. **Iterate**: Adjust values based on quantified data
6. **Repeat** until satisfied

## Tuning Tips

1. **One axis at a time**: Don't tune rotation, range, and lateral simultaneously
2. **Small changes**: Adjust by 10-20% at a time, not 2x or 10x
3. **Test multiple times**: One good run doesn't mean it's tuned
4. **Vary conditions**: Test from different distances, angles, lighting
5. **Record everything**: Keep a tuning log (spreadsheet or notebook)
6. **Start conservative**: Low gains are safer than high gains
7. **Watch the robot**: Don't just look at numbers, observe actual motion
8. **Use both tools**: Dashboard for quick changes, analysis for quantitative validation

## Troubleshooting Tuning System

### Values don't update in SmartDashboard

**Causes**:
- Robot not connected
- NetworkTables not publishing
- Wrong table/key

**Solutions**:
1. Check connection (ping 10.TE.AM.2)
2. Verify `Tuning` table exists in SmartDashboard
3. Check that values appear after code deploy
4. Restart SmartDashboard/Shuffleboard

### Changes don't affect robot behavior

**Causes**:
- Command not using TunableVisionConstants
- Tuning disabled (`TunableNumber.setTuningEnabled(false)`)
- Values cached in command initialization

**Solutions**:
1. Verify command calls `updatePIDGains()` in `execute()`
2. Check `TunableNumber.isTuningEnabled()` returns true
3. Restart the command (stop and start again)

### Shuffleboard layout doesn't load

**Causes**:
- JSON syntax error
- File path wrong

**Solutions**:
1. Use File → Load Layout and browse to `shuffleboard_vision_tuning.json`
2. Check JSON is valid (use online validator)
3. Manually configure layout if needed

## Next Steps

1. **Deploy code** with tuning system
2. **Connect dashboard** (SmartDashboard or Shuffleboard)
3. **Start with Model A** (simplest - rotation only)
4. **Follow tuning workflow** step by step
5. **Record successful values**
6. **Update code** with final constants
7. **Test in match conditions** with tuning disabled

## Additional Resources

- **WPILib PID Tuning**: https://docs.wpilib.org/en/stable/docs/software/advanced-controls/introduction/tuning-pid-controller.html
- **Limelight PID Tuning**: https://docs.limelightvision.io/docs/docs-limelight/tutorials/tutorial-aiming
- **NetworkTables Guide**: https://docs.wpilib.org/en/stable/docs/software/networktables/
- **Shuffleboard Guide**: https://docs.wpilib.org/en/stable/docs/software/dashboards/shuffleboard/

Good luck tuning! Remember: PID tuning is an iterative process. Don't expect perfection on the first try. Use data, be methodical, and you'll find great values.
