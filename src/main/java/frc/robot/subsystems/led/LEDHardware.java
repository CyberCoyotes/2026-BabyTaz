package frc.robot.subsystems.led;

import com.ctre.phoenix6.configs.CANdleConfiguration;
import com.ctre.phoenix6.controls.EmptyAnimation;
import com.ctre.phoenix6.controls.SolidColor;
import com.ctre.phoenix6.hardware.CANdle;
import com.ctre.phoenix6.signals.RGBWColor;
import edu.wpi.first.wpilibj.DataLogManager;

public class LEDHardware {
    private final CANdle candle;

    public LEDHardware() {
        candle = new CANdle(LEDConfig.Constants.CANDLE_ID, "rio");
        DataLogManager.log("LEDHardware: Initialized CANdle on ID " + LEDConfig.Constants.CANDLE_ID);
    }

    public void configure(LEDConfig config) {
        var cfg = new CANdleConfiguration();
        cfg.LED.StripType = config.stripType;
        cfg.LED.BrightnessScalar = config.brightness;
        cfg.CANdleFeatures.StatusLedWhenActive = config.statusLedWhenActive;

        candle.getConfigurator().apply(cfg);

        // Clear all animation slots
        for (int i = 0; i < 8; i++) {
            candle.setControl(new EmptyAnimation(i));
        }

        DataLogManager.log("LEDHardware: Configuration applied");
    }

    public void setColor(int r, int g, int b, int startIndex, int endIndex) {
        candle.setControl(
            new SolidColor(startIndex, endIndex)
                .withColor(new RGBWColor(r, g, b, 0))
        );
    }

    public void setControl(com.ctre.phoenix6.controls.ControlRequest control) {
        candle.setControl(control);
    }

    public CANdle getCANdle() {
        return candle;
    }
}
