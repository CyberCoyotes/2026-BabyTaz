package frc.robot.subsystems.led;

import com.ctre.phoenix6.controls.*;
import com.ctre.phoenix6.signals.RGBWColor;
import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.DataLogManager;

public class LEDSubsystem extends SubsystemBase {
    private final LEDHardware hardware;
    private LEDState currentState = LEDState.OFF;
    private double lastStateChangeTime = 0;
    private static final double MIN_STATE_CHANGE_INTERVAL = 0.1; // seconds

    // LED range constants
    private final int stripStart = LEDConfig.Constants.STRIP_START_INDEX;
    private final int stripEnd = LEDConfig.Constants.STRIP_END_INDEX;

    // Elastic Dashboard NetworkTable
    private final NetworkTable elasticTable;

    public LEDSubsystem() {
        this.hardware = new LEDHardware();
        this.elasticTable = NetworkTableInstance.getDefault().getTable("Elastic").getSubTable("LED");

        DataLogManager.log("LEDSubsystem: Initializing...");
        hardware.configure(LEDConfig.defaultConfig());
        // Smoke test: set all LEDs to white to verify strip is working
        hardware.setColor(255, 255, 255, 0, LEDConfig.Constants.STRIP_END_INDEX);
        DataLogManager.log("LEDSubsystem: Initialization complete");
    }

    @Override
    public void periodic() {
        updateTelemetry();
    }

    public void setState(LEDState state) {
        double currentTime = Timer.getFPGATimestamp();
        if (state != currentState && (currentTime - lastStateChangeTime) > MIN_STATE_CHANGE_INTERVAL) {
            currentState = state;
            lastStateChangeTime = currentTime;
            applyState();
        }
    }

    private void applyState() {
        switch (currentState) {
            case AUTONOMOUS -> hardware.setControl(
                new RainbowAnimation(stripStart, stripEnd).withSlot(0)
            );
            case ERROR -> hardware.setControl(
                new StrobeAnimation(stripStart, stripEnd).withSlot(0)
                    .withColor(new RGBWColor(currentState.r, currentState.g, currentState.b, 0))
            );
            case INTAKING -> hardware.setControl(
                new LarsonAnimation(stripStart, stripEnd).withSlot(0)
                    .withColor(new RGBWColor(currentState.r, currentState.g, currentState.b, 0))
            );
            case SCORING -> hardware.setControl(
                new ColorFlowAnimation(stripStart, stripEnd).withSlot(0)
                    .withColor(new RGBWColor(currentState.r, currentState.g, currentState.b, 0))
            );
            default -> hardware.setColor(
                currentState.r, currentState.g, currentState.b,
                stripStart, stripEnd
            );
        }
    }

    public LEDState getState() {
        return currentState;
    }

    public void setVisionLEDState(boolean targetVisible) {
        setState(targetVisible ? LEDState.TARGET_VISIBLE : LEDState.OFF);
    }

    private void updateTelemetry() {
        elasticTable.getEntry("State").setString(currentState.toString());
    }
}
