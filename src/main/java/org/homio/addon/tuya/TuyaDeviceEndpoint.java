package org.homio.addon.tuya;

import static java.util.Objects.requireNonNull;
import static org.homio.addon.tuya.service.TuyaDeviceService.CONFIG_DEVICE_SERVICE;
import static org.homio.api.util.CommonUtils.splitNameToReadableFormat;

import java.awt.Color;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.tuya.internal.util.SchemaDp;
import org.homio.addon.z2m.service.Z2MDeviceService;
import org.homio.api.EntityContext;
import org.homio.api.EntityContextVar.VariableMetaBuilder;
import org.homio.api.EntityContextVar.VariableType;
import org.homio.api.model.Icon;
import org.homio.api.model.device.ConfigDeviceEndpoint;
import org.homio.api.model.endpoint.BaseDeviceEndpoint;
import org.homio.api.state.DecimalType;
import org.homio.api.state.OnOffType;
import org.homio.api.state.State;
import org.homio.api.state.StringType;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.ui.field.action.v1.item.UIInfoItemBuilder.InfoType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("CommentedOutCode")
@Log4j2
public final class TuyaDeviceEndpoint extends BaseDeviceEndpoint<TuyaDeviceEntity> {

    private static final List<String> COLOUR_CHANNEL_CODES = List.of("colour_data");
    private static final List<String> DIMMER_CHANNEL_CODES = List.of("bright_value", "bright_value_1", "bright_value_2",
            "temp_value");

    @Getter
    private final int dp;
    @Getter
    private final int dp2;
    private final @NotNull SchemaDp schemaDp;
    private final @NotNull Function<Object, Boolean> processEndpointStatusHandler;
    private TuyaEndpointType tuyaEndpointType;
    @Getter
    private boolean oldColorMode = false; // for color endpoint only

    public TuyaDeviceEndpoint(
        @NotNull SchemaDp schemaDp,
        @NotNull EntityContext entityContext,
        @NotNull TuyaDeviceEntity device,
        @Nullable ConfigDeviceEndpoint configEndpoint) {
        super(new Icon(
            "fa fa-fw " + (configEndpoint == null ? "fa-tablet-screen-button" : configEndpoint.getIcon()),
            configEndpoint == null ? "#3894B5" : configEndpoint.getIconColor())
        );
        this.schemaDp = schemaDp;
        this.dp = schemaDp.dp;
        this.dp2 = schemaDp.dp2;

        init(
            schemaDp.code,
            device,
            entityContext,
            null,
            true,
            true,
            schemaDp.code,
            CONFIG_DEVICE_SERVICE.getEndpointOrder(schemaDp.code),
            calcEndpointType());

        if (CONFIG_DEVICE_SERVICE.isEndpointHasVariable(schemaDp.code)) {
            getOrCreateVariable();
        }
        this.processEndpointStatusHandler = buildProcessEndpointStatusHandler();
    }

    private static int hexColorToBrightness(String hexColor) {
        Color color = Color.decode(hexColor);
        // Calculate brightness using the average of RGB components
        return (int) ((color.getRed() + color.getGreen() + color.getBlue()) / 7.65);
    }

    /**
     * Convert a Tuya color string in hexadecimal notation to hex string
     *
     * @param hexColor the input string
     * @return the corresponding state
     */
    private static String hexColorDecode(String hexColor) {
        if (hexColor.length() == 12) {
            // 2 bytes H: 0-360, 2 bytes each S,B, 0-1000
            float h = Integer.parseInt(hexColor.substring(0, 4), 16);
            float s = Integer.parseInt(hexColor.substring(4, 8), 16) / 10F;
            float b = Integer.parseInt(hexColor.substring(8, 12), 16) / 10F;
            if (h == 360) {
                h = 0;
            }
            int rgb = Color.HSBtoRGB(h, s, b);
            return String.format("#%06X", (rgb & 0xFFFFFF));
        } else if (hexColor.length() == 14) {
            // 1 byte each RGB: 0-255, 2 byte H: 0-360, 1 byte each SB: 0-255
            int r = Integer.parseInt(hexColor.substring(0, 2), 16);
            int g = Integer.parseInt(hexColor.substring(2, 4), 16);
            int b = Integer.parseInt(hexColor.substring(4, 6), 16);

            return String.format("#%02X%02X%02X", r, g, b);
        } else {
            throw new IllegalArgumentException("Unknown color format");
        }
    }

    @Override
    public @NotNull String getName(boolean shortFormat) {
        String l1Name = getEndpointEntityID();
        String name = splitNameToReadableFormat(l1Name);
        return shortFormat ? name : "${tuyae.%s~%s}".formatted(l1Name, name);
    }

    @Override
    public @Nullable String getDescription() {
        return "${tuyad.%s~%s}".formatted(schemaDp.code, schemaDp.code);
    }

    @Override
    public @NotNull UIInputBuilder createUIInputBuilder() {
        UIInputBuilder uiInputBuilder = entityContext.ui().inputBuilder();
        TuyaDeviceEntity device = getDevice();
        State value = getValue();

        if (isWritable()) {
            switch (tuyaEndpointType) {
                case bool -> {
                    uiInputBuilder.addCheckbox(getEntityID(), getValue().boolValue(), (entityContext, params) -> {
                        setValue(OnOffType.of(params.getBoolean("value")));
                        device.getService().send(Map.of(dp, getValue().boolValue()));
                        return null;
                    });
                    return uiInputBuilder;
                }
                case number -> {
                    uiInputBuilder.addSlider(getEntityID(), value.floatValue(0), (float) schemaDp.min, (float) schemaDp.max,
                            (entityContext, params) -> {
                                setValue(new DecimalType(params.getInt("value")));
                                device.getService().send(Map.of(dp, value.intValue()));
                                return null;
                            });
                    return uiInputBuilder;
                }
                case string -> {
                    // not implemented
                    if(!value.stringValue().equals("N/A")) {
                        uiInputBuilder.addTextInput(getEntityID(), value.stringValue(), false);
                        //commandRequest.put(configuration.dp, command.toString());
                        log.error("[{}]: Tuya write handler not implemented for endpoint: {}. Type: {}",
                                getDeviceEntityID(), getEndpointEntityID(), tuyaEndpointType);
                    }
                }
                case color -> {
                    if (dp2 != 0) {
                        uiInputBuilder.addCheckbox(getEntityID(), value.boolValue(), (entityContext, params) -> {
                            setValue(OnOffType.of(params.getBoolean("value")));
                            device.getService().send(Map.of(dp2, value.boolValue()));
                            return null;
                        });
                    }
                    uiInputBuilder.addColorPicker(getEntityID(), value.stringValue(), (entityContext, params) -> {
                        Map<Integer, Object> commandRequest = new HashMap<>();
                        setValue(new StringType(params.getString("value")));
                        commandRequest.put(dp, hexColorEncode(value.stringValue()));
                        /* ChannelConfiguration workModeConfig = channelIdToConfiguration.get("work_mode");
                        if (workModeConfig != null) {
                            commandRequest.put(workModeConfig.dp, "colour");
                        } */
                        if (dp2 != 0) {
                            commandRequest.put(dp2, hexColorToBrightness(value.stringValue()) > 0.0);
                        }
                        device.getService().send(commandRequest);
                        return null;
                    });
                    /* if (command instanceof PercentType) {
                        State oldState = channelStateCache.get(channelUID.getId());
                        if (!(oldState instanceof HSBType)) {
                            logger.debug("Discarding command '{}' to channel '{}', cannot determine old state", command,
                                    channelUID);
                            return;
                        }
                        HSBType newState = new HSBType(((HSBType) oldState).getHue(), ((HSBType) oldState).getSaturation(),
                                (PercentType) command);
                        commandRequest.put(configuration.dp, ConversionUtil.hexColorEncode(newState, oldColorMode));
                        ChannelConfiguration workModeConfig = channelIdToConfiguration.get("work_mode");
                        if (workModeConfig != null) {
                            commandRequest.put(workModeConfig.dp, "colour");
                        }
                        if (configuration.dp2 != 0) {
                            commandRequest.put(configuration.dp2, ((PercentType) command).doubleValue() > 0.0);
                        }
                    }*/
                }
                case dimmer -> {
                    if (dp2 != 0) {
                        uiInputBuilder.addCheckbox(getEntityID(), value.boolValue(), (entityContext, params) -> {
                            setValue(OnOffType.of(params.getBoolean("value")));
                            device.getService().send(Map.of(dp2, value.boolValue()));
                            return null;
                        });
                    }
                    uiInputBuilder.addSlider(getEntityID(), value.floatValue(0), 0F, 100F,
                            (entityContext, params) -> {
                                Map<Integer, Object> commandRequest = new HashMap<>();
                                int brightness = (int) Math.round(params.getInt("value") * schemaDp.max / 100.0);
                                setValue(new DecimalType(brightness));
                                if (brightness >= schemaDp.min) {
                                    commandRequest.put(dp, value);
                                }
                                if (dp2 != 0) {
                                    commandRequest.put(dp2, brightness >= schemaDp.min);
                                }
                                /* ChannelConfiguration workModeConfig = channelIdToConfiguration.get("work_mode");
                                if (workModeConfig != null) {
                                    commandRequest.put(workModeConfig.dp, "white");
                                }*/
                                device.getService().send(commandRequest);
                                return null;
                            });
                    return uiInputBuilder;
                }
            }
        }
        if (getUnit() != null) {
            uiInputBuilder.addInfo("%s <small class=\"text-muted\">%s</small>"
                .formatted(value.stringValue(), getUnit()), InfoType.HTML);
        }
        assembleUIAction(uiInputBuilder);
        return uiInputBuilder;
    }

    public boolean processEndpointStatus(Object rawValue) {
        return processEndpointStatusHandler.apply(rawValue);
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

    @Override
    public @NotNull List<String> getSelectValues() {
        return schemaDp.range == null ? List.of() : schemaDp.range;
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
        switch (tuyaEndpointType) {
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

    @Override
    protected @NotNull List<String> getVariableEnumValues() {
        return requireNonNull(schemaDp.range);
    }

    private EndpointType calcEndpointType() {
        if (COLOUR_CHANNEL_CODES.contains(schemaDp.code)) {
            tuyaEndpointType = TuyaEndpointType.color;
            return EndpointType.string;
        } else if (DIMMER_CHANNEL_CODES.contains(schemaDp.code)) {
            tuyaEndpointType = TuyaEndpointType.dimmer;
            return EndpointType.number;
            // has min-max
        } else if ("bool".equals(schemaDp.type)) {
            tuyaEndpointType = TuyaEndpointType.bool;
            return EndpointType.bool;
        } else if ("enum".equals(schemaDp.type)) {
            tuyaEndpointType = TuyaEndpointType.string;
            return EndpointType.select;
        } else if ("string".equals(schemaDp.type)) {
            tuyaEndpointType = TuyaEndpointType.string;
            return EndpointType.string;
        } else if ("value".equals(schemaDp.type)) {
            tuyaEndpointType = TuyaEndpointType.number;
            return EndpointType.number;
        } else {
            tuyaEndpointType = TuyaEndpointType.string;
            return EndpointType.string;
        }
    }

    private String hexColorEncode(String hexColor) {
        // Convert the hex color to RGB components using java.awt.Color
        Color color = Color.decode(hexColor);
        int red = color.getRed();
        int green = color.getGreen();
        int blue = color.getBlue();

        if (!oldColorMode) {
            int hue = 0;
            int saturation = 0;
            int brightness = (int) (Math.max(red, Math.max(green, blue)) / 2.55);

            return String.format("%04x%04x%04x", hue, saturation, brightness);
        } else {
            // Old color mode
            int hue = 0; // Hue is not directly available in pure RGB representation
            int saturation = 0; // Saturation is not directly available in pure RGB representation

            // Calculate brightness using the average of RGB components
            int brightness = (int) ((red + green + blue) / 7.65); // 7.65 = 255 * 2.55 / 100

            return String.format("%02x%02x%02x%04x%02x%02x", red, green, blue, hue, saturation, brightness);
        }
    }

    /* public static String hexColorEncode(HSBType hsb, boolean oldColorMode) {
        if (!oldColorMode) {
            return String.format("%04x%04x%04x", hsb.getHue().intValue(),
                    (int) (hsb.getSaturation().doubleValue() * 10), (int) (hsb.getBrightness().doubleValue() * 10));
        } else {
            return String.format("%02x%02x%02x%04x%02x%02x", (int) (hsb.getRed().doubleValue() * 2.55),
                    (int) (hsb.getGreen().doubleValue() * 2.55), (int) (hsb.getBlue().doubleValue() * 2.55),
                    hsb.getHue().intValue(), (int) (hsb.getSaturation().doubleValue() * 2.55),
                    (int) (hsb.getBrightness().doubleValue() * 2.55));
        }
    }*/
    private Function<Object, Boolean> buildProcessEndpointStatusHandler() {
        switch (tuyaEndpointType) {
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
                        oldColorMode = ((String) rawValue).length() == 14;
                        setValue(new StringType(hexColorDecode((String) rawValue)));
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
    public boolean isVisible() {
        return !Z2MDeviceService.CONFIG_DEVICE_SERVICE.isHideEndpoint(schemaDp.code);
    }

    public enum TuyaEndpointType {
        bool, number, string, color, dimmer
    }
}
