package org.homio.addon.homekit.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Dimmer commands are handled differently by different devices.
 * Some devices expect only the brightness updates, some others expect brightness as well as "On/Off" commands.
 * This enum describes different modes of dimmer handling in the context of HomeKit binding.
 * <p>
 * Following modes are supported:
 * DIMMER_MODE_NORMAL - no filtering. The commands will be sent to a device as received from HomeKit.
 * DIMMER_MODE_FILTER_ON - ON events are filtered out. Only OFF and brightness information is sent
 * DIMMER_MODE_FILTER_BRIGHTNESS_100 - only Brightness=100% is filtered out. Everything else is unchanged. This allows
 * custom logic for soft launch in devices.
 * DIMMER_MODE_FILTER_ON_EXCEPT_BRIGHTNESS_100 - ON events are filtered out in all cases except of Brightness = 100%.
 */

@Getter
@RequiredArgsConstructor
public enum HomekitDimmerMode {
    DIMMER_MODE_NORMAL("normal"),
    DIMMER_MODE_FILTER_ON("filterOn"),
    DIMMER_MODE_FILTER_BRIGHTNESS_100("filterBrightness100"),
    DIMMER_MODE_FILTER_ON_EXCEPT_BRIGHTNESS_100("filterOnExceptBrightness100");

    private static final Map<String, HomekitDimmerMode> TAG_MAP = Arrays.stream(HomekitDimmerMode.values())
            .collect(Collectors.toMap(type -> type.tag.toUpperCase(), type -> type));

    private final String tag;

    public static Optional<HomekitDimmerMode> valueOfTag(String tag) {
        return Optional.ofNullable(TAG_MAP.get(tag.toUpperCase()));
    }
}
