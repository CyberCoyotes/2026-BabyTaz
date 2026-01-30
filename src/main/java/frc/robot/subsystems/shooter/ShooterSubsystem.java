package frc.robot.subsystems.shooter;

import com.ctre.phoenix6.BaseStatusSignal;
import com.ctre.phoenix6.StatusSignal;
import com.ctre.phoenix6.configs.TalonFXConfiguration;
import com.ctre.phoenix6.controls.Follower;
import com.ctre.phoenix6.signals.MotorAlignmentValue; // Added for Phoenix 6 (2026) Follower constructor
import com.ctre.phoenix6.controls.DutyCycleOut;
import com.ctre.phoenix6.controls.VelocityVoltage;
import com.ctre.phoenix6.hardware.TalonFX;
import com.ctre.phoenix6.signals.InvertedValue;
import com.ctre.phoenix6.signals.NeutralModeValue;

import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.units.measure.AngularVelocity;
import edu.wpi.first.units.measure.Current;
import edu.wpi.first.units.measure.Temperature;
import edu.wpi.first.units.measure.Voltage;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.util.TunableNumber;

/**
 * Minimal shooter subsystem for prototype testing on BabyTaz.
 * Controls 3 TalonFX flywheel motors in a leader-follower configuration.
 *
 * Motor A (CAN 25) = Leader
 * Motor B (CAN 26) = Follower
 * Motor C (CAN 27) = Follower
 *
 * Uses VelocityVoltage with FOC for closed-loop RPM control.
 * Target RPM is tunable live from Elastic Dashboard.
 */
public class ShooterSubsystem extends SubsystemBase {

    // ====== CONSTANTS ======
    public static final int FLYWHEEL_A_ID = 25;
    public static final int FLYWHEEL_B_ID = 26;
    public static final int FLYWHEEL_C_ID = 27;
    public static final int INDEXER_ID = 22; // TODO: Assign actual CAN ID

    private static final double GEAR_RATIO = 1.5; // Motor rotations per flywheel rotation
    private static final double INDEXER_DEFAULT_OUTPUT = 0.25; // TODO Default duty cycle for indexer

    // ====== HARDWARE ======
    private final TalonFX flywheelA = new TalonFX(FLYWHEEL_A_ID);
    private final TalonFX flywheelB = new TalonFX(FLYWHEEL_B_ID);
    private final TalonFX flywheelC = new TalonFX(FLYWHEEL_C_ID);
    private final TalonFX indexer = new TalonFX(INDEXER_ID);

    // ====== CONTROL REQUESTS ======
    private final VelocityVoltage velocityRequest = new VelocityVoltage(0)
            .withEnableFOC(true)
            .withSlot(0);
    private final DutyCycleOut indexerDutyCycle = new DutyCycleOut(0);

    // ====== STATUS SIGNALS ======
    private final StatusSignal<AngularVelocity> velocityA;
    private final StatusSignal<AngularVelocity> velocityB;
    private final StatusSignal<AngularVelocity> velocityC;
    private final StatusSignal<Voltage> voltageA;
    private final StatusSignal<Current> currentA;
    private final StatusSignal<Current> currentB;
    private final StatusSignal<Current> currentC;
    private final StatusSignal<Temperature> tempA;
    private final StatusSignal<Temperature> tempB;
    private final StatusSignal<Temperature> tempC;
    private final StatusSignal<AngularVelocity> indexerVelocity;
    private final StatusSignal<Current> indexerCurrent;
    private final StatusSignal<Temperature> indexerTemp;

    // ====== TUNABLE PARAMETERS (editable from Elastic Dashboard) ======
    private final TunableNumber targetRPM = new TunableNumber("Shooter", "TargetRPM", 0.0, 0, 0, 2, 1);
    private final TunableNumber kP = new TunableNumber("Shooter", "kP", 0.1, 0, 0, 1, 1);
    private final TunableNumber kV = new TunableNumber("Shooter", "kV", 0.12, 0, 0, 1, 1);
    private final TunableNumber kS = new TunableNumber("Shooter", "kS", 0.0, 0, 0, 1, 1);
    private final TunableNumber indexerOutput = new TunableNumber("Shooter", "IndexerOutput", INDEXER_DEFAULT_OUTPUT, 0, 0, 2, 1);
    private final TunableNumber spinUpTolerance = new TunableNumber("Shooter", "SpinUpTolerance", 0.05, 0, 0, 2, 1); // 5% default
    private final TunableNumber spinUpTimeout = new TunableNumber("Shooter", "SpinUpTimeout", 3.0, 0, 0, 2, 1); // 3 second default

    // ====== TELEMETRY ======
    private final NetworkTable elasticTable;

    // ====== STATE ======
    private boolean running = false;
    private boolean indexerRunning = false;

    public ShooterSubsystem() {
        configureMotors();
        configureFollowers();
        configureIndexer();

        // Cache status signals
        velocityA = flywheelA.getRotorVelocity();
        velocityB = flywheelB.getRotorVelocity();
        velocityC = flywheelC.getRotorVelocity();
        voltageA = flywheelA.getMotorVoltage();
        currentA = flywheelA.getStatorCurrent();
        currentB = flywheelB.getStatorCurrent();
        currentC = flywheelC.getStatorCurrent();
        tempA = flywheelA.getDeviceTemp();
        tempB = flywheelB.getDeviceTemp();
        tempC = flywheelC.getDeviceTemp();
        indexerVelocity = indexer.getRotorVelocity();
        indexerCurrent = indexer.getStatorCurrent();
        indexerTemp = indexer.getDeviceTemp();

        // Set update frequencies
        BaseStatusSignal.setUpdateFrequencyForAll(100, velocityA, velocityB, velocityC, voltageA, indexerVelocity);
        BaseStatusSignal.setUpdateFrequencyForAll(50, currentA, currentB, currentC, indexerCurrent);
        BaseStatusSignal.setUpdateFrequencyForAll(4, tempA, tempB, tempC, indexerTemp);

        // Optimize CAN bus usage
        flywheelA.optimizeBusUtilization();
        flywheelB.optimizeBusUtilization();
        flywheelC.optimizeBusUtilization();
        indexer.optimizeBusUtilization();

        // Elastic Dashboard table
        elasticTable = NetworkTableInstance.getDefault().getTable("Elastic").getSubTable("Shooter");
    }

    private void configureMotors() {
        var config = new TalonFXConfiguration();

        config.MotorOutput.NeutralMode = NeutralModeValue.Coast;
        config.MotorOutput.Inverted = InvertedValue.Clockwise_Positive;

        config.CurrentLimits.SupplyCurrentLimit = 45.0; // TODO Verify limits
        config.CurrentLimits.SupplyCurrentLimitEnable = true;
        config.CurrentLimits.StatorCurrentLimit = 60.0; // TODO Verify limits
        config.CurrentLimits.StatorCurrentLimitEnable = true;

        // Velocity PID - Slot 0
        config.Slot0.kP = kP.getDefault();
        config.Slot0.kI = 0.0;
        config.Slot0.kD = 0.0;
        config.Slot0.kV = kV.getDefault();
        config.Slot0.kS = kS.getDefault();

        flywheelA.getConfigurator().apply(config);
        flywheelB.getConfigurator().apply(config);
        flywheelC.getConfigurator().apply(config);
    }

    private void configureFollowers() {
        // B and C follow A, same direction

        flywheelB.setControl(new Follower(FLYWHEEL_A_ID, MotorAlignmentValue.Aligned));
        flywheelC.setControl(new Follower(FLYWHEEL_A_ID, MotorAlignmentValue.Aligned));
    }

    private void configureIndexer() {
        var config = new TalonFXConfiguration();

        config.MotorOutput.NeutralMode = NeutralModeValue.Brake;
        // TODO: Verify indexer motor direction matches mechanical setup
        config.MotorOutput.Inverted = InvertedValue.CounterClockwise_Positive;

        config.CurrentLimits.SupplyCurrentLimit = 45.0; // TODO Verify limits
        config.CurrentLimits.SupplyCurrentLimitEnable = true;
        config.CurrentLimits.StatorCurrentLimit = 75.0; // TODO Verify limits
        config.CurrentLimits.StatorCurrentLimitEnable = true;

        indexer.getConfigurator().apply(config);
    }

    @Override
    public void periodic() {
        // Refresh all status signals in one batch
        BaseStatusSignal.refreshAll(
                velocityA, velocityB, velocityC,
                voltageA, currentA, currentB, currentC,
                tempA, tempB, tempC,
                indexerVelocity, indexerCurrent, indexerTemp);

        // Check for live PID changes from Elastic
        if (kP.hasChanged() || kV.hasChanged() || kS.hasChanged()) {
            applyPIDUpdates();
        }

        // If running, apply the current tunable target
        if (running) {
            double rps = rpmToMotorRPS(targetRPM.get());
            flywheelA.setControl(velocityRequest.withVelocity(rps));
        }

        // If indexer running, apply the current tunable output
        if (indexerRunning) {
            indexer.setControl(indexerDutyCycle.withOutput(indexerOutput.get()));
        }

        updateTelemetry();
    }

    // ====== PUBLIC API ======

    /** Spin flywheels at the current dashboard target RPM. */
    public void run() {
        running = true;
        double rps = rpmToMotorRPS(targetRPM.get());
        flywheelA.setControl(velocityRequest.withVelocity(rps));
    }

    /** Spin flywheels at a specific RPM (also updates the dashboard value). */
    public void runAtRPM(double rpm) {
        targetRPM.set(rpm);
        running = true;
        double rps = rpmToMotorRPS(rpm);
        flywheelA.setControl(velocityRequest.withVelocity(rps));
    }

    /** Stop all flywheels (coast). */
    public void stop() {
        running = false;
        flywheelA.stopMotor();
        // Followers will stop automatically, but be explicit
        // NOTE Be aware of this 2026 Follower constructor change!
        flywheelB.setControl(new Follower(FLYWHEEL_A_ID, MotorAlignmentValue.Aligned));
        flywheelC.setControl(new Follower(FLYWHEEL_A_ID, MotorAlignmentValue.Aligned));
    }

    // ====== INDEXER API ======

    /** Run indexer at the current dashboard output (duty cycle). */
    public void runIndexer() {
        indexerRunning = true;
        indexer.setControl(indexerDutyCycle.withOutput(indexerOutput.get()));
    }

    /** Run indexer at a specific duty cycle (-1.0 to 1.0). */
    public void runIndexerAtOutput(double output) {
        indexerRunning = true;
        indexer.setControl(indexerDutyCycle.withOutput(output));
    }

    /** Stop the indexer. */
    public void stopIndexer() {
        indexerRunning = false;
        indexer.stopMotor();
    }

    /** Get indexer velocity in RPM. */
    public double getIndexerRPM() {
        return indexerVelocity.getValueAsDouble() * 60.0; // RPS to RPM
    }

    /** Get indexer current draw in amps. */
    public double getIndexerCurrentAmps() {
        return indexerCurrent.getValueAsDouble();
    }

    /** Get indexer temperature in Celsius. */
    public double getIndexerTempCelsius() {
        return indexerTemp.getValueAsDouble();
    }

    public boolean isIndexerRunning() {
        return indexerRunning;
    }

    /** Get averaged flywheel RPM across all 3 motors. */
    public double getAverageRPM() {
        double avgRPS = (velocityA.getValueAsDouble()
                + velocityB.getValueAsDouble()
                + velocityC.getValueAsDouble()) / 3.0;
        return motorRPSToRPM(avgRPS);
    }

    /** Get RPM for a specific motor (0=A, 1=B, 2=C). */
    public double getMotorRPM(int index) {
        return switch (index) {
            case 0 -> motorRPSToRPM(velocityA.getValueAsDouble());
            case 1 -> motorRPSToRPM(velocityB.getValueAsDouble());
            case 2 -> motorRPSToRPM(velocityC.getValueAsDouble());
            default -> 0.0;
        };
    }

    /** Get total stator current draw across all 3 motors. */
    public double getTotalCurrentAmps() {
        return currentA.getValueAsDouble()
                + currentB.getValueAsDouble()
                + currentC.getValueAsDouble();
    }

    /** Get max temperature across all 3 motors. */
    public double getMaxTempCelsius() {
        return Math.max(tempA.getValueAsDouble(),
                Math.max(tempB.getValueAsDouble(), tempC.getValueAsDouble()));
    }

    /** Get the maximum RPM spread between motors (useful for detecting follower issues). */
    public double getVelocitySpread() {
        double a = motorRPSToRPM(velocityA.getValueAsDouble());
        double b = motorRPSToRPM(velocityB.getValueAsDouble());
        double c = motorRPSToRPM(velocityC.getValueAsDouble());
        double max = Math.max(a, Math.max(b, c));
        double min = Math.min(a, Math.min(b, c));
        return max - min;
    }

    /** Check if flywheels are within the tunable tolerance of the target RPM. */
    public boolean isAtTargetRPM() {
        double target = targetRPM.get();
        double tolerance = spinUpTolerance.get();
        if (Math.abs(target) < 50) {
            return Math.abs(getAverageRPM()) < 50;
        }
        return Math.abs(getAverageRPM() - target) < Math.abs(target) * tolerance;
    }

    public boolean isRunning() {
        return running;
    }

    public double getTargetRPM() {
        return targetRPM.get();
    }

    /** Check if shooter is ready to fire (flywheels at speed and running). */
    public boolean isReadyToFire() {
        return running && isAtTargetRPM();
    }

    /** Get the spin-up timeout in seconds. */
    public double getSpinUpTimeout() {
        return spinUpTimeout.get();
    }

    // ====== PRIVATE HELPERS ======

    private void applyPIDUpdates() {
        var config = new TalonFXConfiguration();
        // Re-read current config, then override PID
        flywheelA.getConfigurator().refresh(config);
        config.Slot0.kP = kP.get();
        config.Slot0.kV = kV.get();
        config.Slot0.kS = kS.get();
        flywheelA.getConfigurator().apply(config);
        flywheelB.getConfigurator().apply(config);
        flywheelC.getConfigurator().apply(config);
    }

    /** Convert flywheel RPM to motor RPS (accounting for gear ratio). */
    private double rpmToMotorRPS(double rpm) {
        return (rpm / 60.0) * GEAR_RATIO;
    }

    /** Convert motor RPS to flywheel RPM (accounting for gear ratio). */
    private double motorRPSToRPM(double rps) {
        return (rps * 60.0) / GEAR_RATIO;
    }

    private void updateTelemetry() {
        double avgRPM = getAverageRPM();
        double target = targetRPM.get();

        // Elastic Dashboard (live display)
        elasticTable.getEntry("Running").setBoolean(running);
        elasticTable.getEntry("TargetRPM").setDouble(target);
        elasticTable.getEntry("AverageRPM").setDouble(avgRPM);
        elasticTable.getEntry("RPMError").setDouble(target - avgRPM);
        elasticTable.getEntry("AtTarget").setBoolean(isAtTargetRPM());
        elasticTable.getEntry("ReadyToFire").setBoolean(isReadyToFire());
        elasticTable.getEntry("SpinUpTolerance").setDouble(spinUpTolerance.get());
        elasticTable.getEntry("SpinUpTimeout").setDouble(spinUpTimeout.get());
        elasticTable.getEntry("MotorA_RPM").setDouble(getMotorRPM(0));
        elasticTable.getEntry("MotorB_RPM").setDouble(getMotorRPM(1));
        elasticTable.getEntry("MotorC_RPM").setDouble(getMotorRPM(2));
        elasticTable.getEntry("VelocitySpread").setDouble(getVelocitySpread());
        elasticTable.getEntry("TotalAmps").setDouble(getTotalCurrentAmps());
        elasticTable.getEntry("MaxTempC").setDouble(getMaxTempCelsius());
        elasticTable.getEntry("IndexerRunning").setBoolean(indexerRunning);
        elasticTable.getEntry("IndexerOutput").setDouble(indexerOutput.get());
        elasticTable.getEntry("IndexerRPM").setDouble(getIndexerRPM());
        elasticTable.getEntry("IndexerAmps").setDouble(getIndexerCurrentAmps());
    }
}
