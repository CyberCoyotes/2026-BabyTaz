package frc.robot.subsystems.led;

import com.ctre.phoenix.ErrorCode;
import com.ctre.phoenix.led.*;
import edu.wpi.first.wpilibj.DataLogManager;
import edu.wpi.first.wpilibj.Timer;

@SuppressWarnings({"unused", "removal"}) // Phoenix 5 CANdle API marked for removal but still required

public class LEDHardware {
    // Hardware state tracking
    private final CANdle candle;
    private Animation currentAnimation;
    private LEDConfig config;
    private boolean isConfigured = false;
    private int configRetryCount = 0;
    private static final int MAX_CONFIG_RETRIES = 5; // Increased from 3 to 5
    private double lastUpdateTime = 0;
    
    // Diagnostic data structure (simplified from previous LEDIOInputs)
    public static class Status {
        public double busVoltage = 0.0;
        public double current = 0.0;
        public double temperature = 0.0;
        public boolean isConnected = false;
        public double lastUpdateTime = 0.0;
        public boolean isConfigured = false;
        public int configAttempts = 0;
    }
    
    public LEDHardware() {
        // Using the constant directly since we know we're using CANdle
        // Try "rio" explicitly instead of empty string
        candle = new CANdle(LEDConfig.Constants.CANDLE_ID, "rio");
        config = LEDConfig.defaultConfig();
        DataLogManager.log("LEDHardware: Initializing CANdle with ID " + LEDConfig.Constants.CANDLE_ID + " on 'rio' CAN bus");

        // Give the CANdle time to boot up before configuration
        Timer.delay(0.25);
    }

    public void configure(LEDConfig config) {
        this.config = config;
        configRetryCount = 0;

        // Try factory default first to clear any bad configuration
        DataLogManager.log("LEDHardware: Applying factory default...");
        try {
            var factoryDefaultError = candle.configFactoryDefault(1000); // Increased timeout
            if (factoryDefaultError.value == 0) {
                DataLogManager.log("LEDHardware: Factory default successful");
            } else {
                DataLogManager.log("LEDHardware: Factory default returned error: " + factoryDefaultError + " - continuing anyway");
            }
        } catch (Exception e) {
            DataLogManager.log("LEDHardware: Factory default threw exception: " + e.getMessage() + " - continuing anyway");
        }
        Timer.delay(0.2);

        attemptConfiguration();
    }

    private void attemptConfiguration() {
        try {
            CANdleConfiguration candleConfig = new CANdleConfiguration();
            candleConfig.stripType = config.stripType;
            candleConfig.brightnessScalar = config.brightness;
            candleConfig.statusLedOffWhenActive = config.statusLedOffWhenActive;
            candleConfig.vBatOutputMode = config.vBatOutputMode;
            candleConfig.disableWhenLOS = config.disableWhenLOS;

            DataLogManager.log("LEDHardware: Attempting configuration (attempt " + (configRetryCount + 1) + ") with brightness=" + config.brightness +
                             ", stripType=" + config.stripType);

            // Use long timeout for more reliable configuration
            var error = candle.configAllSettings(candleConfig, 1000);

            DataLogManager.log("LEDHardware: configAllSettings returned: " + error + " (value=" + error.value + ")");

            if (error.value != 0) {
                handleConfigError(error);
                return;
            }

            isConfigured = true;
            configRetryCount = 0;
            DataLogManager.log("LEDHardware: ✓ Successfully configured");
        } catch (Exception e) {
            DataLogManager.log("LEDHardware: Configuration failed with exception: " + e.getMessage());
            e.printStackTrace();
            handleConfigError(null);
        }
    }

    private void handleConfigError(ErrorCode error) {
        configRetryCount++;
        isConfigured = false;

        String errorMsg = (error != null ? error.toString() : "Unknown error");

        if (configRetryCount < MAX_CONFIG_RETRIES) {
            DataLogManager.log("LEDHardware: Configuration attempt " + configRetryCount +
                             " failed. Error: " + errorMsg + " - retrying...");
            Timer.delay(0.3); // Increased delay between retries
            attemptConfiguration();
        } else {
            DataLogManager.log("LEDHardware: FAILED - Configuration failed after " + MAX_CONFIG_RETRIES +
                             " attempts. Error: " + errorMsg);
            DataLogManager.log("LEDHardware: Check that CANdle with ID " + LEDConfig.Constants.CANDLE_ID +
                             " is connected to 'rio' CAN bus and powered on");
        }
    }

    public void setRGB(int r, int g, int b) {
        if (!isConfigured) {
            DataLogManager.log("LEDHardware: Attempted to set RGB before successful configuration");
            return;
        }

        try {
            currentAnimation = null;
            candle.setLEDs(r, g, b);
            lastUpdateTime = Timer.getFPGATimestamp();
        } catch (Exception e) {
            DataLogManager.log("LEDHardware: Failed to set RGB values: " + e.getMessage());
        }
    }

    public void setAnimation(Animation animation) {
        if (!isConfigured) {
            DataLogManager.log("LEDHardware: Attempted to set animation before successful configuration");
            return;
        }

        try {
            currentAnimation = animation;
            candle.animate(animation);
            lastUpdateTime = Timer.getFPGATimestamp();
        } catch (Exception e) {
            DataLogManager.log("LEDHardware: Failed to set animation: " + e.getMessage());
        }
    }

    public Status getStatus() {
        Status status = new Status();
        
        try {
            status.busVoltage = candle.getBusVoltage();
            status.current = candle.getCurrent();
            status.temperature = candle.getTemperature();
            status.isConnected = !candle.hasResetOccurred();
            status.lastUpdateTime = lastUpdateTime;
            status.isConfigured = isConfigured;
            status.configAttempts = configRetryCount;
        } catch (Exception e) {
            DataLogManager.log("LEDHardware: Failed to get status: " + e.getMessage());
            status.isConnected = false;
        }
        
        return status;
    }
}