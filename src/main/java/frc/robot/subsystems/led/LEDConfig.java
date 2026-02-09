package frc.robot.subsystems.led;

import java.util.ArrayList;
import java.util.List;

import com.ctre.phoenix.led.CANdle.LEDStripType;
import com.ctre.phoenix.led.CANdle.VBatOutputMode;

@SuppressWarnings("unused")

public class LEDConfig {
    public static final class Constants {
        public static final int CANDLE_ID = 30; // CAN ID of the LED controller
        public static final int LED_COUNT = 30; 
        public static final double DEFAULT_BRIGHTNESS = 0.5; // Default brightness (0.0 to 1.0)
    }

    // Configuration properties
    public int ledCount;
    public double brightness;
    public LEDStripType stripType;
    public boolean statusLedOffWhenActive;
    public VBatOutputMode vBatOutputMode;
    public boolean disableWhenLOS;
    
    public static LEDConfig defaultConfig() {
        LEDConfig config = new LEDConfig();
        config.ledCount = Constants.LED_COUNT; // TODO Count the LEDs for a strip
        config.brightness = Constants.DEFAULT_BRIGHTNESS; // 0.0 to 1.0
        config.stripType = LEDStripType.GRB; // TODO Determine actual strip type
        config.statusLedOffWhenActive = true;
        config.vBatOutputMode = VBatOutputMode.Modulated; // TODO Determine actual mode
        config.disableWhenLOS = false;
        return config;
    }

    // Zone definition for LED segments
    public static class Zone {
        public final int startIndex;
        public final int length;
        public final String name;
        
        public Zone(int start, int length, String name) {
            this.startIndex = start;
            this.length = length;
            this.name = name;
        }
    }

    public List<Zone> zones = new ArrayList<>();
}

