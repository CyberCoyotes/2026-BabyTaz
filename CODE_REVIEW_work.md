# Code Review: `work` Branch

Date: 2026-02-11  
Scope: repository-level and branch-level review of production Java sources under `src/main/java`.

## Review Methods
- Inspected key robot lifecycle, container wiring, autonomous routines, and telemetry classes.
- Searched for constructor usages and duplicate routine wiring.
- Attempted local build (blocked by environment proxy while downloading Gradle distribution).

## Findings

### 1) High: `VisionSubsystem` receives `null` LEDs due constructor initialization order
**File:** `src/main/java/frc/robot/auto/AutoRoutines.java`  
The constructor builds `VisionSubsystem` with `m_leds` before `m_leds` is initialized. This means LED feedback is silently disabled in that subsystem instance.

```java
m_vision = new VisionSubsystem("limelight", m_leds);
m_leds = new LEDSubsystem();
```

**Risk:** incorrect runtime behavior and confusing diagnostics (vision works, LED state feedback does not).  
**Recommendation:** initialize `m_leds` first, then pass it into `VisionSubsystem`.

---

### 2) High: extra drivetrain instance created as a field side effect
**File:** `src/main/java/frc/robot/auto/AutoRoutines.java`  
`m_drivetrain` is created at field declaration and then replaced in the constructor:

```java
private CommandSwerveDrivetrain m_drivetrain = TunerConstants.createDrivetrain();
...
m_drivetrain = drivetrain;
```

**Risk:** unexpected hardware object creation, duplicate subsystem instances, and side effects during robot startup.  
**Recommendation:** remove field-time instantiation and make `m_drivetrain` final, assigned only from constructor dependency injection.

---

### 3) Medium: duplicate autonomous routine/path identifiers
**File:** `src/main/java/frc/robot/auto/AutoRoutines.java`  
Both `scoreCenter()` and `scoreCenterL1()` use the same routine and trajectory names (`"ScoreCenter"`).

**Risk:** routine selection ambiguity and maintenance confusion; selecting one may be indistinguishable from the other in dashboards/logs.  
**Recommendation:** assign distinct routine names and trajectory ids, or consolidate into one method with parameters.

---

### 4) Low: unresolved TODO left in production container
**File:** `src/main/java/frc/robot/RobotContainer.java`  
There is a TODO comment (`Do a code review of this repo please`) inside the production container class.

**Risk:** low runtime risk, but increases noise and reduces codebase cleanliness.  
**Recommendation:** remove the TODO and track review tasks in issue tracker / PR checklist.

## Notes
- The active robot wiring currently uses `frc.robot.AutoRoutines` (top-level package), not `frc.robot.auto.AutoRoutines`. That means findings #1-#3 are currently latent unless code paths are switched to the `frc.robot.auto` package implementation.
- Because those issues are in tree and likely to be reused, they should still be cleaned up to avoid future regressions.
