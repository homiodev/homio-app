package org.touchhome.bundle.zigbee.converter.warningdevice;

import java.util.Map;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toMap;

/**
 * A type of squawk for a warning device.
 * <p>
 * Squawk types represented by this class can also be represented by ESH commands; for this, a
 * rather simple format is used, by configuring the properties of the type using 'key=value' pairs that are
 * separated by whitespace.
 * <p>
 * Example for such a command: 'type=squawk squawkMode=ARMED useStrobe=true squawkLevel=HIGH'.
 */
public class SquawkType {

    private static final int DEFAULT_SQUAWK_MODE = SquawkMode.ARMED.getValue();
    private static final int DEFAULT_SQUAWK_LEVEL = SoundLevel.HIGH.getValue();

    private final boolean useStrobe;
    private final int squawkMode;
    private final int squawkLevel;

    public SquawkType(boolean useStrobe, int squawkMode, int squawkLevel) {
        this.useStrobe = useStrobe;
        this.squawkMode = squawkMode;
        this.squawkLevel = squawkLevel;
    }

    /**
     * @param command An ESH command representing a warning
     * @return The {@link SquawkType} represented by the ESH command, or null if the command does not represent a
     * {@link SquawkType}.
     */
    public static SquawkType parse(String command) {
        Map<String, String> parameters = stream(command.split("\\s+")).filter(s -> s.contains("="))
                .collect(toMap(s -> s.split("=")[0], s -> s.split("=")[1]));

        if ("squawk".equals(parameters.get("type"))) {
            return new SquawkType(Boolean.valueOf(parameters.getOrDefault("useStrobe", "true")),
                    getSquawkMode(parameters.get("squawkMode")), getSquawkLevel(parameters.get("squawkLevel")));
        } else {
            return null;
        }
    }

    private static int getSquawkMode(String squawkModeString) {
        if (squawkModeString == null) {
            return DEFAULT_SQUAWK_MODE;
        }

        try {
            return SquawkMode.valueOf(squawkModeString).getValue();
        } catch (IllegalArgumentException e) {
            // ignore - try to parse the warningModeString as number
        }

        try {
            return Integer.parseInt(squawkModeString);
        } catch (NumberFormatException e) {
            return DEFAULT_SQUAWK_MODE;
        }
    }

    private static int getSquawkLevel(String squawkLevelString) {
        if (squawkLevelString == null) {
            return DEFAULT_SQUAWK_LEVEL;
        }

        try {
            return SoundLevel.valueOf(squawkLevelString).getValue();
        } catch (IllegalArgumentException e) {
            // ignore - try to parse the squawkLevelString as number
        }

        try {
            return Integer.parseInt(squawkLevelString);
        } catch (NumberFormatException e) {
            return DEFAULT_SQUAWK_LEVEL;
        }
    }

    /**
     * @return whether to use the optical strobe signal
     */
    public boolean isUseStrobe() {
        return useStrobe;
    }

    /**
     * @return the squawk mode to use
     */
    public int getSquawkMode() {
        return squawkMode;
    }

    /**
     * @return the squawk level to use
     */
    public int getSquawkLevel() {
        return squawkLevel;
    }

    /**
     * @return Generates the ESH command representing this warning
     */
    public String serializeToCommand() {
        return String.format("type=squawk useStrobe=%s squawkMode=%s squawkLevel=%s", useStrobe, squawkMode,
                squawkLevel);
    }

}
