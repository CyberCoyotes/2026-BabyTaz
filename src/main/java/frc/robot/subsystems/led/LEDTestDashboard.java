package frc.robot.subsystems.led;

import edu.wpi.first.networktables.BooleanEntry;
import edu.wpi.first.networktables.DoubleEntry;
import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.networktables.StringEntry;
import edu.wpi.first.wpilibj.smartdashboard.SendableChooser;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.SubsystemBase;

/**
 * LED Test Dashboard
 *
 * Provides an Elastic Dashboard selector for testing LED states and animations.
 *
 * NetworkTables layout (Elastic/LEDTest):
 * - ActiveMode (string): last applied test mode
 * - AnimationsEnabled (boolean): enable/disable animations
 * - AnimationSpeed (double): animation speed multiplier
 * - AnimationBrightness (double): animation brightness (0.0 to 1.0)
 * - HardwareBrightness (double): CANdle brightness scalar (0.0 to 1.0)
 *
 * Chooser (SmartDashboard):
 * - SmartDashboard/Elastic/LEDTest/ModeChooser
 */
public class LEDTestDashboard extends SubsystemBase {
    private final LEDSubsystem leds;
    private final SendableChooser<TestMode> modeChooser = new SendableChooser<>();

    private final NetworkTable elasticTable;
    private final StringEntry activeModeEntry;
    private final BooleanEntry animationsEnabledEntry;
    private final DoubleEntry animationSpeedEntry;
    private final DoubleEntry animationBrightnessEntry;
    private final DoubleEntry hardwareBrightnessEntry;

    private TestMode lastMode = null;
    private boolean lastAnimationsEnabled = true;
    private double lastAnimationSpeed = 0.7;
    private double lastAnimationBrightness = 1.0;
    private double lastHardwareBrightness = -1.0;

    public LEDTestDashboard(LEDSubsystem leds) {
        this.leds = leds;
        this.elasticTable = NetworkTableInstance.getDefault().getTable("Elastic").getSubTable("LEDTest");

        modeChooser.setDefaultOption("Off", TestMode.OFF);
        modeChooser.addOption("Enabled (Green)", TestMode.ENABLED);
        modeChooser.addOption("Disabled (Orange)", TestMode.DISABLED);
        modeChooser.addOption("Autonomous (Rainbow)", TestMode.AUTONOMOUS);
        modeChooser.addOption("Target Visible (Yellow)", TestMode.TARGET_VISIBLE);
        modeChooser.addOption("Target Locked (Green)", TestMode.TARGET_LOCKED);
        modeChooser.addOption("Intaking (Larson)", TestMode.INTAKING);
        modeChooser.addOption("Scoring (Color Flow)", TestMode.SCORING);
        modeChooser.addOption("No Target (Gray)", TestMode.NO_TARGET);
        modeChooser.addOption("Error (Strobe Red)", TestMode.ERROR);

        SmartDashboard.putData("Elastic/LEDTest/ModeChooser", modeChooser);

        activeModeEntry = elasticTable.getStringTopic("ActiveMode").getEntry("Off");
        animationsEnabledEntry = elasticTable.getBooleanTopic("AnimationsEnabled").getEntry(true);
        animationSpeedEntry = elasticTable.getDoubleTopic("AnimationSpeed").getEntry(0.7);
        animationBrightnessEntry = elasticTable.getDoubleTopic("AnimationBrightness").getEntry(1.0);
        hardwareBrightnessEntry = elasticTable.getDoubleTopic("HardwareBrightness").getEntry(0.5);

        activeModeEntry.set("Off");
        animationsEnabledEntry.set(true);
        animationSpeedEntry.set(0.7);
        animationBrightnessEntry.set(1.0);
        hardwareBrightnessEntry.set(0.5);
    }

    @Override
    public void periodic() {
        TestMode selected = modeChooser.getSelected();
        if (selected == null) {
            selected = TestMode.OFF;
        }

        if (selected != lastMode) {
            applyTestMode(selected);
            lastMode = selected;
            activeModeEntry.set(selected.label);
        }

        boolean animationsEnabled = animationsEnabledEntry.get();
        if (animationsEnabled != lastAnimationsEnabled) {
            leds.setAnimationEnabled(animationsEnabled);
            lastAnimationsEnabled = animationsEnabled;
        }

        double animationSpeed = animationSpeedEntry.get();
        if (Math.abs(animationSpeed - lastAnimationSpeed) > 1e-3) {
            leds.setAnimationSpeed(animationSpeed);
            lastAnimationSpeed = animationSpeed;
        }

        double animationBrightness = animationBrightnessEntry.get();
        if (Math.abs(animationBrightness - lastAnimationBrightness) > 1e-3) {
            leds.setBrightness(animationBrightness);
            lastAnimationBrightness = animationBrightness;
        }

        double hardwareBrightness = hardwareBrightnessEntry.get();
        if (Math.abs(hardwareBrightness - lastHardwareBrightness) > 1e-3) {
            leds.setHardwareBrightnessScalar(hardwareBrightness);
            lastHardwareBrightness = hardwareBrightness;
        }
    }

    private void applyTestMode(TestMode mode) {
        leds.setState(mode.state);
    }

    private enum TestMode {
        OFF("Off", LEDState.OFF),
        ENABLED("Enabled (Green)", LEDState.ENABLED),
        DISABLED("Disabled (Orange)", LEDState.DISABLED),
        AUTONOMOUS("Autonomous (Rainbow)", LEDState.AUTONOMOUS),
        TARGET_VISIBLE("Target Visible (Yellow)", LEDState.TARGET_VISIBLE),
        TARGET_LOCKED("Target Locked (Green)", LEDState.TARGET_LOCKED),
        INTAKING("Intaking (Larson)", LEDState.INTAKING),
        SCORING("Scoring (Color Flow)", LEDState.SCORING),
        NO_TARGET("No Target (Gray)", LEDState.NO_TARGET),
        ERROR("Error (Strobe Red)", LEDState.ERROR);

        private final String label;
        private final LEDState state;

        TestMode(String label, LEDState state) {
            this.label = label;
            this.state = state;
        }
    }
}
