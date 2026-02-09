package frc.robot.subsystems.led;

import com.ctre.phoenix6.signals.StripTypeValue;
import com.ctre.phoenix6.signals.StatusLedWhenActiveValue;

public class LEDConfig {
    public static final class Constants {
        public static final int CANDLE_ID = 30; // CAN ID of the LED controller
        public static final int ONBOARD_LED_COUNT = 8; // CANdle has 8 onboard LEDs
        public static final int STRIP_LED_COUNT = 20; // 12V WS2811: 30 LEDs / 3 per IC = 10 addressable pixels
        public static final int STRIP_START_INDEX = ONBOARD_LED_COUNT; // Strip starts after onboard
        public static final int STRIP_END_INDEX = ONBOARD_LED_COUNT + STRIP_LED_COUNT - 1;
        public static final double DEFAULT_BRIGHTNESS = 1.0; // Full brightness for testing
    }

    public StripTypeValue stripType;
    public double brightness;
    public StatusLedWhenActiveValue statusLedWhenActive;

    public static LEDConfig defaultConfig() {
        LEDConfig config = new LEDConfig();
        config.stripType = StripTypeValue.GRB;
        config.brightness = Constants.DEFAULT_BRIGHTNESS;
        config.statusLedWhenActive = StatusLedWhenActiveValue.Disabled;
        return config;
    }
}
