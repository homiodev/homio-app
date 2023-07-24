package org.homio.addon.tuya;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.tuya.internal.util.ConversionUtil;
import org.homio.addon.tuya.internal.util.SchemaDp;
import org.homio.api.model.DeviceProperty;
import org.homio.api.model.Icon;
import org.homio.api.state.DecimalType;
import org.homio.api.state.OnOffType;
import org.homio.api.state.State;
import org.homio.api.state.StringType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Log4j2
@Getter
public final class TuyaDeviceProperty implements DeviceProperty {

    private static final List<String> COLOUR_CHANNEL_CODES = List.of("colour_data");
    private static final List<String> DIMMER_CHANNEL_CODES = List.of("bright_value", "bright_value_1", "bright_value_2",
        "temp_value");

    private final int dp;
    private final SchemaDp schemaDp;
    private final PropertyType propertyType;
    private final TuyaPropertyType tuyaPropertyType;
    private final @NotNull Function<Object, Boolean> stateHandler;
    @Setter
    private State state;
    @Setter
    private int dp2 = -1;

    public TuyaDeviceProperty(SchemaDp schemaDp) {
        this.schemaDp = schemaDp;
        this.dp = schemaDp.id;

        if (schemaDp.range != null && !schemaDp.range.isEmpty()) {
            /*List<CommandOption> commandOptions = toCommandOptionList(
                Arrays.stream(configuration.range.split(",")).collect(Collectors.toList()));
            dynamicCommandDescriptionProvider.setCommandOptions(channel.getUID(), commandOptions);*/
        }

        if (COLOUR_CHANNEL_CODES.contains(schemaDp.code)) {
            propertyType = PropertyType.string;
            tuyaPropertyType = TuyaPropertyType.color;
        } else if (DIMMER_CHANNEL_CODES.contains(schemaDp.code)) {
            propertyType = PropertyType.number;
            tuyaPropertyType = TuyaPropertyType.dimmer;
            // has min-max
        } else if ("bool".equals(schemaDp.type)) {
            propertyType = PropertyType.bool;
            tuyaPropertyType = TuyaPropertyType.bool;
        } else if ("enum".equals(schemaDp.type)) {
            propertyType = PropertyType.string;
            tuyaPropertyType = TuyaPropertyType.string;
            List<String> range = Objects.requireNonNullElse(schemaDp.range, List.of());
            //configuration.put("range", String.join(",", range));
        } else if ("string".equals(schemaDp.type)) {
            propertyType = PropertyType.string;
            tuyaPropertyType = TuyaPropertyType.string;
        } else if ("value".equals(schemaDp.type)) {
            propertyType = PropertyType.number;
            tuyaPropertyType = TuyaPropertyType.number;
            /*configuration.put("min", schemaDp.min);
            configuration.put("max", schemaDp.max);*/
        } else {
            propertyType = PropertyType.string;
            tuyaPropertyType = TuyaPropertyType.string;
            // e.g. type "raw", add empty channel
            System.out.println("Unknown");
            /*return Map.entry("", ChannelBuilder.create(channelUID).build());*/
        }
        this.stateHandler = buildStateHandler();
    }

    private Function<Object, Boolean> buildStateHandler() {
        switch (tuyaPropertyType) {
            case bool -> {
                return rawValue -> {
                    if (Boolean.class.isAssignableFrom(rawValue.getClass())) {
                        setState(OnOffType.of((boolean) rawValue));
                        return true;
                    }
                    return false;
                };
            }
            case number -> {
                return rawValue -> {
                    if (Double.class.isAssignableFrom(rawValue.getClass())) {
                        setState(new DecimalType((Double) rawValue));
                        return true;
                    }
                    return false;
                };
            }
            case color -> {
                return rawValue -> {
                    if (rawValue instanceof String) {
                        setState(new StringType(ConversionUtil.hexColorDecode((String) rawValue)));
                        return true;
                    }
                    return false;
                };
            }
            case dimmer -> {
                // brightness
                return rawValue -> {
                    if (Double.class.isAssignableFrom(rawValue.getClass())) {
                        double value = (double) rawValue;
                        if (value <= 0) {
                            setState(DecimalType.ZERO);
                        } else if (value >= getMax()) {
                            setState(DecimalType.HUNDRED);
                        } else {
                            setState(new DecimalType(new BigDecimal(100.0 * value / (getMax() - 0))));
                        }
                        return true;
                    }
                    return false;
                };
            }
            default -> {
                return rawValue -> {
                    if (rawValue instanceof String) {
                        setState(new StringType((String) rawValue));
                        return true;
                    }
                    return false;
                };
            }
        }
    }

    @Override
    public @NotNull String getKey() {
        return "key";
    }

    @Override
    public @NotNull String getName(boolean shortFormat) {
        return "name";
    }

    @Override
    public @Nullable String getDescription() {
        return null;
    }

    @Override
    public @NotNull Icon getIcon() {
        return new Icon();
    }

    @Override
    public @Nullable String getUnit() {
        return null;
    }

    @Override
    public @NotNull String getVariableID() {
        return "var";
    }

    @Override
    public @NotNull String getEntityID() {
        return "eid";
    }

    @Override
    public @NotNull String getIeeeAddress() {
        return schemaDp.code;
    }

    @Override
    public @NotNull State getLastValue() {
        return State.of(0);
    }

    @Override
    public @NotNull Duration getTimeSinceLastEvent() {
        return Duration.ofSeconds(1);
    }

    @Override
    public boolean isWritable() {
        return false;
    }

    @Override
    public boolean isReadable() {
        return false;
    }

    @Override
    public void addChangeListener(@Nullable String id, @Nullable Consumer<State> changeListener) {

    }

    @Override
    public void removeChangeListener(@Nullable String id) {

    }

    @Override
    public @NotNull PropertyType getPropertyType() {
        return PropertyType.string;
    }

    public double getMax() {
        return schemaDp.max;
    }

    public enum TuyaPropertyType {
        bool, number, string, color, dimmer
    }
}
