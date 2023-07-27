package org.homio.addon.tuya;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.tuya.internal.util.ConversionUtil;
import org.homio.addon.tuya.internal.util.SchemaDp;
import org.homio.api.EntityContext;
import org.homio.api.EntityContextVar.VariableMetaBuilder;
import org.homio.api.EntityContextVar.VariableType;
import org.homio.api.model.BaseDeviceProperty;
import org.homio.api.model.Icon;
import org.homio.api.model.endpoint.DeviceEndpoint.EndpointType;
import org.homio.api.state.DecimalType;
import org.homio.api.state.OnOffType;
import org.homio.api.state.StringType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Log4j2
@Getter
public final class TuyaDeviceProperty extends BaseDeviceProperty {

    private static final List<String> COLOUR_CHANNEL_CODES = List.of("colour_data");
    private static final List<String> DIMMER_CHANNEL_CODES = List.of("bright_value", "bright_value_1", "bright_value_2",
        "temp_value");

    private final int dp;
    private final @NotNull TuyaDeviceEntity entity;
    private final @NotNull SchemaDp schemaDp;
    private final @NotNull Function<Object, Boolean> stateHandler;
    private TuyaPropertyType tuyaPropertyType;
    @Setter
    private int dp2 = -1;

    public TuyaDeviceProperty(@NotNull SchemaDp schemaDp, @NotNull EntityContext entityContext, @NotNull TuyaDeviceEntity entity) {
        super(new Icon("fa fa-fw fa-cross", "#FF00FF"));
        this.schemaDp = schemaDp;
        this.dp = schemaDp.id;
        this.entity = entity;
        this.entityID = schemaDp.code + "_" + schemaDp.id;

        init(schemaDp.code + "_" + schemaDp.id,
            entity.getEntityID(),
            entity,
            entityContext,
            null,
            true,
            true,
            schemaDp.code,
            Objects.requireNonNull(entity.getIeeeAddress()));

        if (schemaDp.range != null && !schemaDp.range.isEmpty()) {
            /*List<CommandOption> commandOptions = toCommandOptionList(
                Arrays.stream(configuration.range.split(",")).collect(Collectors.toList()));
            dynamicCommandDescriptionProvider.setCommandOptions(channel.getUID(), commandOptions);*/
        }

        this.stateHandler = buildStateHandler();
    }

    @Override
    protected EndpointType buildPropertyType() {
        if (COLOUR_CHANNEL_CODES.contains(schemaDp.code)) {
            tuyaPropertyType = TuyaPropertyType.color;
            return EndpointType.string;
        } else if (DIMMER_CHANNEL_CODES.contains(schemaDp.code)) {
            tuyaPropertyType = TuyaPropertyType.dimmer;
            return EndpointType.number;
            // has min-max
        } else if ("bool".equals(schemaDp.type)) {
            tuyaPropertyType = TuyaPropertyType.bool;
            return EndpointType.bool;
        } else if ("enum".equals(schemaDp.type)) {
            tuyaPropertyType = TuyaPropertyType.string;
            return EndpointType.select;
        } else if ("string".equals(schemaDp.type)) {
            tuyaPropertyType = TuyaPropertyType.string;
            return EndpointType.string;
        } else if ("value".equals(schemaDp.type)) {
            tuyaPropertyType = TuyaPropertyType.number;
            return EndpointType.number;
        } else {
            tuyaPropertyType = TuyaPropertyType.string;
            return EndpointType.string;
        }
    }

    private Function<Object, Boolean> buildStateHandler() {
        switch (tuyaPropertyType) {
            case bool -> {
                return rawValue -> {
                    if (Boolean.class.isAssignableFrom(rawValue.getClass())) {
                        setValue(OnOffType.of((boolean) rawValue));
                        return true;
                    }
                    return false;
                };
            }
            case number -> {
                return rawValue -> {
                    if (Double.class.isAssignableFrom(rawValue.getClass())) {
                        setValue(new DecimalType((Double) rawValue));
                        return true;
                    }
                    return false;
                };
            }
            case color -> {
                return rawValue -> {
                    if (rawValue instanceof String) {
                        setValue(new StringType(ConversionUtil.hexColorDecode((String) rawValue)));
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
                            setValue(DecimalType.ZERO);
                        } else if (value >= schemaDp.max) {
                            setValue(DecimalType.HUNDRED);
                        } else {
                            setValue(new DecimalType(new BigDecimal(100.0 * value / (schemaDp.max - 0))));
                        }
                        return true;
                    }
                    return false;
                };
            }
            default -> {
                return rawValue -> {
                    if (rawValue instanceof String) {
                        setValue(new StringType((String) rawValue));
                        return true;
                    }
                    return false;
                };
            }
        }
    }

    @Override
    public @NotNull String getName(boolean shortFormat) {
        return "name";
    }

    @Override
    public @Nullable String getDescription() {
        return "${tuya.%s~%s}".formatted(schemaDp.code, schemaDp.code);
    }

    public enum TuyaPropertyType {
        bool, number, string, color, dimmer
    }

    @Override
    protected Consumer<VariableMetaBuilder> getVariableMetaBuilder() {
        return builder -> {
            builder.setDescription(getVariableDescription()).setReadOnly(!isWritable()).setColor(getIcon().getColor());
            List<String> attributes = new ArrayList<>();
            if (schemaDp.max > 0) {
                attributes.add("min:" + schemaDp.min);
                attributes.add("max:" + schemaDp.max);
            }
            if (schemaDp.range != null) {
                attributes.add("range:" + String.join(";", schemaDp.range));
            }
            builder.setAttributes(attributes);
        };
    }

    private String getVariableDescription() {
        List<String> description = new ArrayList<>();
        description.add(getDescription());
        if (schemaDp.range != null) {
            description.add("(range:%s)".formatted(String.join(";", schemaDp.range)));
        }
        if (schemaDp.max > 0) {
            description.add("(min-max:%S...%s)".formatted(schemaDp.min, schemaDp.max));
        }
        return String.join(" ", description);
    }

    @Override
    protected @NotNull VariableType getVariableType() {
        switch (tuyaPropertyType) {
            case bool -> {
                return VariableType.Bool;
            }
            case number -> {
                return VariableType.Float;
            }
            default -> {
                return VariableType.Any;
            }
        }
    }
}
