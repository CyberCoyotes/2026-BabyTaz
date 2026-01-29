package frc.robot.commands;

import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.Commands;
import frc.robot.subsystems.shooter.ShooterSubsystem;

/**
 * Factory class for shooter commands on BabyTaz prototype.
 * All commands are static factories — no instantiation needed.
 */
public class ShooterCommands {

    private ShooterCommands() {} // Prevent instantiation

    /**
     * Spin flywheels at whatever RPM is currently set on the Elastic dashboard.
     * Runs until cancelled (whileTrue binding).
     */
    public static Command runFromDashboard(ShooterSubsystem shooter) {
        return Commands.startEnd(
                shooter::run,
                shooter::stop,
                shooter)
                .withName("Shooter: Run (Dashboard RPM)");
    }

    /**
     * Spin flywheels and indexer together.
     * Flywheels spin up first, indexer waits until flywheels reach target OR timeout expires.
     * Runs until cancelled.
     */
    public static Command runWithIndexer(ShooterSubsystem shooter) {
        return Commands.sequence(
                // Start flywheels
                Commands.runOnce(shooter::run, shooter),
                // Wait until flywheels reach target OR timeout expires (whichever comes first)
                Commands.race(
                        Commands.waitUntil(shooter::isAtTargetRPM),
                        Commands.waitSeconds(shooter.getSpinUpTimeout())),
                // Run indexer while continuing to run flywheels
                Commands.run(shooter::runIndexer, shooter))
                .finallyDo(() -> {
                    shooter.stop();
                    shooter.stopIndexer();
                })
                .withName("Shooter: Run with Indexer");
    }

    /**
     * Spin flywheels at a specific RPM. Also updates the dashboard value.
     * Runs until cancelled.
     */
    public static Command runAtRPM(ShooterSubsystem shooter, double rpm) {
        return Commands.startEnd(
                () -> shooter.runAtRPM(rpm),
                shooter::stop,
                shooter)
                .withName("Shooter: Run @ " + (int) rpm + " RPM");
    }

    /**
     * Spin flywheels at a specific RPM with indexer.
     * Flywheels spin up first, indexer waits until flywheels reach target OR timeout expires.
     * Runs until cancelled.
     */
    public static Command runAtRPMWithIndexer(ShooterSubsystem shooter, double rpm) {
        return Commands.sequence(
                // Start flywheels at specified RPM
                Commands.runOnce(() -> shooter.runAtRPM(rpm), shooter),
                // Wait until flywheels reach target OR timeout expires (whichever comes first)
                Commands.race(
                        Commands.waitUntil(shooter::isAtTargetRPM),
                        Commands.waitSeconds(shooter.getSpinUpTimeout())),
                // Run indexer while continuing to run flywheels
                Commands.run(shooter::runIndexer, shooter))
                .finallyDo(() -> {
                    shooter.stop();
                    shooter.stopIndexer();
                })
                .withName("Shooter: Run @ " + (int) rpm + " RPM with Indexer");
    }

    /**
     * Run only the indexer at dashboard duty cycle.
     * Runs until cancelled.
     */
    public static Command runIndexerOnly(ShooterSubsystem shooter) {
        return Commands.startEnd(
                shooter::runIndexer,
                shooter::stopIndexer,
                shooter)
                .withName("Shooter: Indexer Only");
    }

    /**
     * Stop the shooter and indexer. Instant command.
     */
    public static Command stop(ShooterSubsystem shooter) {
        return Commands.runOnce(() -> {
            shooter.stop();
            shooter.stopIndexer();
        }, shooter)
                .withName("Shooter: Stop");
    }

    /**
     * Reverse the flywheels briefly for clearing jams.
     * Runs at -2000 RPM for the specified duration, then stops.
     */
    public static Command eject(ShooterSubsystem shooter, double seconds) {
        return Commands.sequence(
                Commands.runOnce(() -> shooter.runAtRPM(-2000), shooter),
                Commands.waitSeconds(seconds),
                Commands.runOnce(shooter::stop, shooter))
                .withName("Shooter: Eject");
    }

    /**
     * Reverse the flywheels and indexer for clearing jams.
     * Runs flywheels at -2000 RPM and indexer at -0.5 for the specified duration.
     */
    public static Command ejectWithIndexer(ShooterSubsystem shooter, double seconds) {
        return Commands.sequence(
                Commands.runOnce(() -> {
                    shooter.runAtRPM(-2000);
                    shooter.runIndexerAtOutput(-0.5);
                }, shooter),
                Commands.waitSeconds(seconds),
                Commands.runOnce(() -> {
                    shooter.stop();
                    shooter.stopIndexer();
                }, shooter))
                .withName("Shooter: Eject with Indexer");
    }
}
