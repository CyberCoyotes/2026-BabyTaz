// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot;

import com.ctre.phoenix6.SignalLogger;

import edu.wpi.first.math.util.Units;
// import org.littletonrobotics.junction.LogFileUtil;
// import org.littletonrobotics.junction.Logger;
// import org.littletonrobotics.junction.networktables.NT4Publisher;
// import org.littletonrobotics.junction.wpilog.WPILOGWriter;
import edu.wpi.first.wpilibj.TimedRobot;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.CommandScheduler;
import frc.robot.telemetry.MatchDataLogger;
import frc.robot.telemetry.PerformanceMonitor;
import frc.robot.telemetry.PowerMonitor;
import frc.robot.telemetry.AlertManager;

@SuppressWarnings("unused")

public class Robot extends TimedRobot {
    private Command m_autonomousCommand;

    private final RobotContainer m_robotContainer;

    private final boolean kUseLimelight = false;

    // Telemetry and monitoring systems
    // private final MatchDataLogger matchDataLogger = new MatchDataLogger();
    private final PerformanceMonitor performanceMonitor = new PerformanceMonitor();
    private final PowerMonitor powerMonitor = new PowerMonitor();
    private final AlertManager alertManager = new AlertManager();

    public Robot() {
        // Configure AdvantageKit logging
        /* 
        Logger.recordMetadata("ProjectName", "BabyTaz");

        if (isReal()) {
            // Log to USB stick and publish to NetworkTables
            Logger.addDataReceiver(new WPILOGWriter());
            Logger.addDataReceiver(new NT4Publisher());

            // Start CTRE Hoot signal logging (logs all Phoenix 6 device data)
            SignalLogger.setPath("/U/logs");
            SignalLogger.start();
        } else {
            // Replay mode or simulation - just publish to NT
            Logger.addDataReceiver(new NT4Publisher());
        }
*/
        // Logger.start();

        m_robotContainer = new RobotContainer();
    }

    @Override
    public void robotPeriodic() {
        // Update performance monitoring
        performanceMonitor.updateCycleTime();

        // Update power monitoring
        powerMonitor.update();

        CommandScheduler.getInstance().run();

        /*
         * This example of adding Limelight is very simple and may not be sufficient for on-field use.
         * Users typically need to provide a standard deviation that scales with the distance to target
         * and changes with number of tags available.
         *
         * This example is sufficient to show that vision integration is possible, though exact implementation
         * of how to use vision should be tuned per-robot and to the team's specification.
         */
        if (kUseLimelight) {
            var driveState = m_robotContainer.drivetrain.getState();
            double headingDeg = driveState.Pose.getRotation().getDegrees();
            double omegaRps = Units.radiansToRotations(driveState.Speeds.omegaRadiansPerSecond);

            LimelightHelpers.SetRobotOrientation("limelight", headingDeg, 0, 0, 0, 0, 0);
            var llMeasurement = LimelightHelpers.getBotPoseEstimate_wpiBlue_MegaTag2("limelight");
            if (llMeasurement != null && llMeasurement.tagCount > 0 && Math.abs(omegaRps) < 2.0) {
                m_robotContainer.drivetrain.addVisionMeasurement(llMeasurement.pose, llMeasurement.timestampSeconds);
            }
        }
    }

    @Override
    public void disabledInit() {}

    @Override
    public void disabledPeriodic() {}

    @Override
    public void disabledExit() {}

    @Override
    public void autonomousInit() {
        // Log match start
        // matchDataLogger.logMatchStart();

        m_autonomousCommand = m_robotContainer.getAutonomousCommand();

        if (m_autonomousCommand != null) {
            CommandScheduler.getInstance().schedule(m_autonomousCommand);
            alertManager.sendInfo("Autonomous started: " + m_autonomousCommand.getName());
        } else {
            alertManager.sendWarning("No autonomous command selected!");
        }
    }

    @Override
    public void autonomousPeriodic() {}

    @Override
    public void autonomousExit() {
        // matchDataLogger.logMatchEnd();
    }

    @Override
    public void teleopInit() {
        if (m_autonomousCommand != null) {
            CommandScheduler.getInstance().cancel(m_autonomousCommand);
        }
    }

    @Override
    public void teleopPeriodic() {}

    @Override
    public void teleopExit() {}

    @Override
    public void testInit() {
        CommandScheduler.getInstance().cancelAll();
    }

    @Override
    public void testPeriodic() {}

    @Override
    public void testExit() {}

    @Override
    public void simulationPeriodic() {}
}
