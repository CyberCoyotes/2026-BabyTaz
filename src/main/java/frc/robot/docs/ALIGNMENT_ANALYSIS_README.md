# AprilTag Alignment Analysis Guide

This guide explains how to use the `analyze_alignment.py` tool to quantify and improve your AprilTag alignment performance using AdvantageKit log data.

## Quick Start

### 1. Install Dependencies

```bash
pip install advantagescope-log-parser matplotlib numpy pandas
```

### 2. Get Your Log Files

Retrieve `.wpilog` files from your robot following the instructions in `src/main/java/frc/robot/documentation/log-setup.md`:

- **USB**: Connect to roboRIO and download from `/home/lvuser/logs/`
- **SCP**: `scp lvuser@10.TE.AM.2:/home/lvuser/logs/*.wpilog ./logs/`
- **AdvantageScope**: Use File → Download Logs

### 3. Run Analysis

```bash
# Analyze the main AlignToAprilTagCommand
python analyze_alignment.py logs/FRC_20250130_143022.wpilog --plot

# Analyze Model A (rotation only)
python analyze_alignment.py logs/FRC_20250130_143022.wpilog --model A --plot

# Analyze Model C (3-axis perpendicular) and save plots
python analyze_alignment.py logs/FRC_20250130_143022.wpilog --model C --plot --save-plots ./analysis_results/

# Export metrics to JSON for further analysis
python analyze_alignment.py logs/FRC_20250130_143022.wpilog --export metrics.json
```

## What This Tool Measures

The analysis tool provides comprehensive metrics to quantify your alignment performance:

### 1. **Alignment Speed**

- **Time to Aligned**: Time from command start to first fully aligned state
- **Settling Time**: Time to reach and stay within tolerance (0.5s settling window)
- **Duration**: Total command execution time

**Use Case**: Compare different PID tunings to find which converges fastest.

### 2. **Alignment Accuracy**

- **Final Errors**: Error at command completion for each axis:
  - Forward error (meters)
  - Lateral error (meters)
  - Rotation error (degrees)
- **Peak Errors**: Maximum error during alignment on each axis

**Use Case**: Verify you're meeting your tolerance requirements (e.g., ±5cm lateral, ±10cm forward, ±2° rotation).

### 3. **Overshoot Analysis**

- Percentage overshoot on each axis
- Identifies if controller is too aggressive

**Use Case**: If you see >20% overshoot, reduce kP or add kD to dampen response.

### 4. **Oscillation Detection**

- Counts zero-crossings on each error signal
- High oscillation count indicates instability

**Use Case**: If you see many oscillations (>5), system may be underdamped. Increase kD or reduce kP.

### 5. **Control Effort**

- Average absolute speed on each axis
- Shows how aggressively the robot is moving

**Use Case**: Compare energy efficiency between tunings. Smoother motion = lower average speeds.

### 6. **Target Tracking**

- Number of times target was lost during alignment
- Success rate across multiple attempts

**Use Case**: Frequent target losses indicate vision issues, excessive robot motion, or lighting problems.

## Understanding the Output

### Console Output Example

```
======================================================================
Session 1 Metrics
======================================================================
Duration: 2.145 s
Success: ✓

--- Timing ---
Time to Aligned: 1.823 s
Settling Time: 1.950 s

--- Final Errors ---
Forward: 0.0234 m (2.34 cm)
Lateral: 0.0089 m (0.89 cm)
Rotation: 0.45 deg

--- Peak Errors ---
Forward: 0.8234 m
Lateral: 0.1245 m
Rotation: 12.34 deg

--- Overshoot ---
Forward: 8.5%
Lateral: 3.2%
Rotation: 15.3%

--- Oscillations (zero crossings) ---
Forward: 2
Lateral: 1
Rotation: 3

--- Average Control Effort ---
Forward Speed: 0.2345 m/s
Lateral Speed: 0.0789 m/s
Rotation Speed: 0.4521 rad/s

--- Target Tracking ---
Target Lost Count: 0
======================================================================
```

### Plot Interpretation

The tool generates three subplots:

**1. Error Plot (top)**
- Shows error vs time for all three axes
- Good alignment: smooth decay to zero
- Problems to look for:
  - **Oscillations**: wavy pattern around zero
  - **Slow convergence**: takes >3s to reach zero
  - **Overshoot**: error crosses zero and goes negative

**2. Speed Plot (middle)**
- Shows commanded speeds for all axes
- Good control: smooth reduction as error decreases
- Problems to look for:
  - **Chattering**: rapid speed changes (too much D gain)
  - **Saturation**: constant max speed (integral windup or tracking too slow)

**3. Status Plot (bottom)**
- Blue: Has target (vision sees AprilTag)
- Green: Fully aligned (all axes within tolerance)
- Problems to look for:
  - **Target losses**: blue drops out (vision issues)
  - **Delayed alignment**: green appears very late (tolerances too tight or slow convergence)

## Model Selection Guide

Choose the appropriate model based on what you're testing:

| Model | Command | Axes Controlled | Use When |
|-------|---------|-----------------|----------|
| `main` | AlignToAprilTagCommand | 3-axis (forward, lateral, rotation) | Testing your primary alignment command |
| `A` | RotationalAlignCommand | 1-axis (rotation only) | Tuning rotation PID in isolation |
| `B` | RotationalRangeAlignCommand | 2-axis (rotation + forward) | Testing rotation + distance without strafe |
| `C` | PerpendicularAlignCommand | 3-axis (full perpendicular) | Testing Model C with lateral deadband |

## Workflow for PID Tuning

### Step 1: Baseline Measurement

```bash
# Run alignment with current PID values
# Record a log file
python analyze_alignment.py baseline.wpilog --model main --plot --export baseline.json
```

**Analyze**:
- What's the alignment time? (Target: <2s)
- What's the final error? (Target: <5cm lateral, <10cm forward, <2° rotation)
- Is there overshoot? (Target: <15%)
- Are there oscillations? (Target: <3 crossings)

### Step 2: Tune One Axis at a Time

Start with **rotation** (easiest to tune):

1. Use Model A to isolate rotation
2. Adjust rotation PID constants in `VisionConstants.java`
3. Test and record logs
4. Compare metrics

```bash
python analyze_alignment.py test1.wpilog --model A --export test1.json
python analyze_alignment.py test2.wpilog --model A --export test2.json
# Compare alignment times, overshoot, oscillations
```

**Tuning Guidelines**:
- **Too slow convergence**: Increase kP
- **Overshoot >20%**: Decrease kP or add kD
- **Oscillations >5**: Add kD or reduce kP
- **Steady-state error**: Add small kI (start with 0.01)

Repeat for **forward/backward** (Model B) and **lateral** (Model C).

### Step 3: Test Full 3-Axis Performance

Once individual axes are tuned, test the complete system:

```bash
python analyze_alignment.py full_test.wpilog --model main --plot --export full_test.json
```

**Watch for**:
- Axis coupling (one axis affecting another)
- Overall alignment time
- Success rate across multiple attempts

### Step 4: Compare Configurations

Create a comparison script:

```python
import json
import numpy as np

configs = ['baseline.json', 'tuning_v1.json', 'tuning_v2.json']

for config in configs:
    with open(config) as f:
        data = json.load(f)

    times = [s['alignment_time'] for s in data['sessions'] if s['alignment_time']]
    success = [s['success'] for s in data['sessions']]

    print(f"\n{config}:")
    print(f"  Success Rate: {np.mean(success)*100:.1f}%")
    print(f"  Avg Alignment Time: {np.mean(times):.3f}s ± {np.std(times):.3f}s")
```

## Advanced Analysis Examples

### Example 1: Batch Analysis

Analyze multiple log files at once:

```bash
#!/bin/bash
for log in logs/*.wpilog; do
    echo "Processing $log..."
    python analyze_alignment.py "$log" --model main --export "results/$(basename $log .wpilog).json"
done
```

### Example 2: Compare Models

Test all four models on the same log:

```bash
python analyze_alignment.py test.wpilog --model A --export model_A.json
python analyze_alignment.py test.wpilog --model B --export model_B.json
python analyze_alignment.py test.wpilog --model C --export model_C.json
python analyze_alignment.py test.wpilog --model main --export model_main.json
```

### Example 3: Statistical Analysis

Collect 10+ runs and analyze distribution:

```python
import json
import numpy as np
import matplotlib.pyplot as plt

# Load all test runs
times = []
for i in range(1, 11):
    with open(f'run_{i}.json') as f:
        data = json.load(f)
        for session in data['sessions']:
            if session['alignment_time']:
                times.append(session['alignment_time'])

# Plot distribution
plt.hist(times, bins=20, edgecolor='black')
plt.axvline(np.mean(times), color='r', linestyle='--', label=f'Mean: {np.mean(times):.2f}s')
plt.axvline(np.median(times), color='g', linestyle='--', label=f'Median: {np.median(times):.2f}s')
plt.xlabel('Alignment Time (s)')
plt.ylabel('Count')
plt.title('Alignment Time Distribution (N=10 runs)')
plt.legend()
plt.savefig('alignment_time_distribution.png', dpi=150)
print(f"Mean: {np.mean(times):.3f}s")
print(f"Std Dev: {np.std(times):.3f}s")
print(f"95th percentile: {np.percentile(times, 95):.3f}s")
```

## Troubleshooting

### "No alignment sessions found in log file"

**Causes**:
1. Wrong model selected (data logged to different NetworkTables prefix)
2. Log file doesn't contain alignment data
3. Command never transitioned to STARTED/ALIGNING state

**Solutions**:
- Verify you selected the correct `--model` flag
- Check log file in AdvantageScope to confirm alignment data exists
- Ensure command is actually running during log capture

### "ERROR: advantagescope module not found"

**Solution**:
```bash
pip install advantagescope-log-parser
```

If that fails, try:
```bash
pip install wpilib
```

### Missing data fields

Some models don't log all axes (e.g., Model A only logs rotation). The tool will interpolate missing data as zeros.

## Current PID Values (Reference)

From `VisionConstants.java`:

### AlignToAprilTagCommand (main)
- **Forward**: kP=1.0, kI=0.0, kD=0.0, tol=0.10m
- **Lateral**: kP=0.3, kI=0.0, kD=0.0, tol=0.05m
- **Rotation**: kP=0.08, kI=0.0, kD=0.0, tol=2.0°

### Model A (Rotation Only)
- **Rotation**: kP=0.05, kI=0.0, kD=0.002, tol=1.5°

### Model B (Rotation + Range)
- **Rotation**: kP=0.035, kI=0.0, kD=0.0, tol=1.5°
- **Range**: kP=1.0, kI=0.0, kD=0.05, tol=0.05m

### Model C (3-Axis Perpendicular)
- **Rotation**: kP=0.05, kI=0.0, kD=0.0, tol=2.0°
- **Range**: kP=1.2, kI=0.0, kD=0.1, tol=0.05m
- **Lateral**: kP=0.04, kI=0.0, kD=0.0, tol=2.0°, deadband=3.0°

## Tips for Better Results

1. **Consistent Test Conditions**
   - Start from same initial position/angle
   - Use same AprilTag ID
   - Same lighting conditions
   - Level playing field

2. **Record Metadata**
   - Note PID values in log filename: `rotation_kP0.05_kD0.002.wpilog`
   - Document test conditions
   - Label baseline vs experiments

3. **Multiple Runs**
   - Always test 5+ runs per configuration
   - Look for consistency (low std deviation)
   - Outliers indicate instability

4. **Safety First**
   - Start with conservative (low) gains
   - Increase gradually
   - Watch for aggressive motion that could damage robot

## Next Steps

After analyzing your logs:

1. **Identify bottlenecks**: Which axis is slowest? Which has most error?
2. **Prioritize tuning**: Focus on the axis with worst performance first
3. **Iterate**: Small changes, test, measure, repeat
4. **Document**: Keep a tuning log with PID values and results
5. **Validate**: Test final configuration in match-like scenarios

## Additional Resources

- **AdvantageScope**: https://github.com/Mechanical-Advantage/AdvantageScope
- **PID Tuning Guide**: https://docs.wpilib.org/en/stable/docs/software/advanced-controls/introduction/tuning-pid-controller.html
- **Limelight Docs**: https://docs.limelightvision.io/
- **Log Setup Guide**: `src/main/java/frc/robot/documentation/log-setup.md`

## Questions?

If you encounter issues or have suggestions for additional metrics, please document them for future improvements to this tool.
