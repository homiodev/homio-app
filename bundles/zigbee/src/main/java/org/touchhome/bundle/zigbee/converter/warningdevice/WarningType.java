package org.touchhome.bundle.zigbee.converter.warningdevice;

import java.time.Duration;
import java.util.Map;

import static java.util.Arrays.stream;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;

/**
 * A type of warning for a warning device.
 * <p>
 * Warning types represented by this class can also be represented by ESH commands; for this, a
 * rather simple format is used, by configuring the properties of the type using 'key=value' pairs that are
 * separated by whitespace.
 * <p>
 * Example for such a command: 'type=warning useStrobe=true warningMode=BURGLAR sirenLevel=HIGH duration=PT15M'.
 * <p>
 * Note that the duration is specified using the ISO-8601 duration format (see {@link Duration#parse(CharSequence)} for
 * more details).
 */
public class WarningType {

    private static final int DEFAULT_WARNING_MODE = WarningMode.BURGLAR.getValue();
    private static final int DEFAULT_SIREN_LEVEL = SoundLevel.HIGH.getValue();

    private final boolean useStrobe;
    private final int warningMode;
    private final int sirenLevel;
    private final Duration duration;

    public WarningType(boolean useStrobe, int warningMode, int sirenLevel, Duration duration) {
        requireNonNull(duration);

        this.useStrobe = useStrobe;
        this.warningMode = warningMode;
        this.sirenLevel = sirenLevel;
        this.duration = duration;
    }

    /**
     * @param command An ESH command representing a warning
     * @return The {@link WarningType} represented by the ESH command, or null if the command does not represent a
     * {@link WarningType}.
     */
    public static WarningType parse(String command) {
        Map<String, String> parameters = stream(command.split("\\s+")).filter(s -> s.contains("="))
                .collect(toMap(s -> s.split("=")[0], s -> s.split("=")[1]));

        if ("warning".equals(parameters.get("type"))) {
            return new WarningType(Boolean.valueOf(parameters.getOrDefault("useStrobe", "true")),
                    getWarningMode(parameters.get("warningMode")), getSirenLevel(parameters.get("sirenLevel")),
                    Duration.parse(parameters.getOrDefault("duration", Duration.ofSeconds(15).toString())));
        } else {
            return null;
        }
    }

    private static int getWarningMode(String warningModeString) {
        if (warningModeString == null) {
            return DEFAULT_WARNING_MODE;
        }

        try {
            return WarningMode.valueOf(warningModeString).getValue();
        } catch (IllegalArgumentException e) {
            // ignore - try to parse the warningModeString as number
        }

        try {
            return Integer.parseInt(warningModeString);
        } catch (NumberFormatException e) {
            return DEFAULT_WARNING_MODE;
        }
    }

    private static int getSirenLevel(String sirenLevelString) {
        if (sirenLevelString == null) {
            return DEFAULT_SIREN_LEVEL;
        }

        try {
            return SoundLevel.valueOf(sirenLevelString).getValue();
        } catch (IllegalArgumentException e) {
            // ignore - try to parse the sirenLevelString as number
        }

        try {
            return Integer.parseInt(sirenLevelString);
        } catch (NumberFormatException e) {
            return DEFAULT_SIREN_LEVEL;
        }
    }

    /**
     * @return whether to use the optical strobe signal
     */
    public boolean isUseStrobe() {
        return useStrobe;
    }

    /**
     * @return the warning mode to use
     */
    public int getWarningMode() {
        return warningMode;
    }

    /**
     * @return the siren level to use
     */
    public int getSirenLevel() {
        return sirenLevel;
    }

    /**
     * @return the duration of the warning
     */
    public Duration getDuration() {
        return duration;
    }

    /**
     * @return Generates the ESH command representing this warning
     */
    public String serializeToCommand() {
        return String.format("type=warning useStrobe=%s warningMode=%s sirenLevel=%s duration=%s", useStrobe,
                warningMode, sirenLevel, duration);
    }

}
