package org.homio.addon.camera.service;

import static org.homio.api.util.CommonUtils.splitNameToReadableFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.camera.entity.BaseVideoEntity;
import org.homio.api.EntityContextVar.VariableMetaBuilder;
import org.homio.api.EntityContextVar.VariableType;
import org.homio.api.model.Icon;
import org.homio.api.model.device.ConfigDeviceEndpoint;
import org.homio.api.model.endpoint.BaseDeviceEndpoint;
import org.homio.api.state.State;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.ui.field.action.v1.item.UIInfoItemBuilder.InfoType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("CommentedOutCode")
@Log4j2
public class VideoDeviceEndpoint extends BaseDeviceEndpoint<BaseVideoEntity> {

    private final @NotNull WriteHandler writeHandler;
    @Setter
    @Getter
    private Set<String> variableEnumValues;

    public VideoDeviceEndpoint(
        @NotNull BaseVideoEntity device,
        @NotNull String endpointEntityID,
        EndpointType endpointType,
        boolean writable) {
        ConfigDeviceEndpoint configEndpoint = BaseVideoService.CONFIG_DEVICE_SERVICE.getDeviceEndpoints().get(endpointEntityID);

        setIcon(new Icon(
            "fa fa-fw " + (configEndpoint == null ? "fa-camera" : configEndpoint.getIcon()),
            configEndpoint == null ? "#3894B5" : configEndpoint.getIconColor())
        );

        init(
            BaseVideoService.CONFIG_DEVICE_SERVICE,
            endpointEntityID,
            device,
            device.getService().getEntityContext(),
            configEndpoint == null ? null : configEndpoint.getUnit(),
            true,
            writable,
            endpointEntityID,
            endpointType);
        this.writeHandler = createExternalWriteHandler();
    }

    @Override
    public @NotNull String getName(boolean shortFormat) {
        String l1Name = getEndpointEntityID();
        String name = splitNameToReadableFormat(l1Name);
        return shortFormat ? name : "${video.%s~%s}".formatted(l1Name, name);
    }

    @Override
    public @Nullable String getDescription() {
        return "${video.%s~%s}".formatted("schemaDp.getCode()", "schemaDp.getCode()");
    }

    @Override
    public void writeValue(@NotNull State state) {
        /*Object targetValue;
        State targetState;
        switch (tuyaEndpointType) {
            case bool -> {
                targetState = OnOffType.of(state.boolValue());
                targetValue = targetState.boolValue();
            }
            case number, dimmer -> {
                targetState = state instanceof DecimalType ? (DecimalType) state : new DecimalType(state.intValue());
                targetValue = targetState.intValue();
            }
            default -> {
                targetState = state;
                targetValue = state.toString();
            }
        }
        setValue(targetState, true);
        getDevice().getService().send(Map.of(dp, targetValue));*/
    }

    @Override
    public @NotNull UIInputBuilder createActionBuilder() {
        UIInputBuilder uiInputBuilder = entityContext.ui().inputBuilder();
        State value = getValue();

        /*if (isWritable()) {
            switch (tuyaEndpointType) {
                case bool -> {
                    uiInputBuilder.addCheckbox(getEntityID(), getValue().boolValue(), (entityContext, params) -> {
                        setValue(OnOffType.of(params.getBoolean("value")), false);
                        return device.getService().send(Map.of(dp, getValue().boolValue()));
                    }).setDisabled(!device.getStatus().isOnline());
                    return uiInputBuilder;
                }
                case number -> {
                    uiInputBuilder.addSlider(getEntityID(), value.floatValue(0), schemaDp.getMin(), schemaDp.getMax(),
                        (entityContext, params) -> {
                            setValue(new DecimalType(params.getInt("value")), false);
                            return device.getService().send(Map.of(dp, value.intValue()));
                        }).setDisabled(!device.getStatus().isOnline());
                    return uiInputBuilder;
                }
                case select -> {
                    uiInputBuilder
                        .addSelectBox(getEntityID(), (entityContext, params) -> {
                            setValue(new StringType(params.getString("value")), false);
                            return device.getService().send(Map.of(dp, value.stringValue()));
                        })
                        .addOptions(OptionModel.list(getSelectValues()))
                        .setPlaceholder("-----------")
                        .setSelected(getValue().toString())
                        .setDisabled(!device.getStatus().isOnline());
                    return uiInputBuilder;
                }
                case string -> {
                    // not implemented
                    if (!value.stringValue().equals("N/A")) {
                        uiInputBuilder.addTextInput(getEntityID(), value.stringValue(), false)
                                      .setApplyButton(true);
                        return uiInputBuilder;
                    }
                }
            }
        }*/
        if (getUnit() != null) {
            uiInputBuilder.addInfo("%s <small class=\"text-muted\">%s</small>"
                .formatted(value.stringValue(), getUnit()), InfoType.HTML);
        } else {
            assembleUIAction(uiInputBuilder);
        }
        return uiInputBuilder;
    }

    public boolean writeValue(Object rawValue, boolean externalUpdate) {
        return writeHandler.write(rawValue, externalUpdate);
    }

    @Override
    protected Consumer<VariableMetaBuilder> getVariableMetaBuilder() {
        return builder -> {
            builder.setDescription(getVariableDescription()).setReadOnly(!isWritable()).setColor(getIcon().getColor());
            List<String> attributes = new ArrayList<>();
            /*if (schemaDp.getMax() > 0) {
                attributes.add("min:" + schemaDp.getMin());
                attributes.add("max:" + schemaDp.getMax());
            }
            if (!schemaDp.getRange().isEmpty()) {
                attributes.add("range:" + String.join(";", schemaDp.getRange()));
            }*/
            builder.setAttributes(attributes);
        };
    }

    @Override
    public @NotNull Set<String> getSelectValues() {
        return Set.of();//schemaDp.getRange();
    }

    private String getVariableDescription() {
        List<String> description = new ArrayList<>();
        description.add(getDescription());
        /*if (!schemaDp.getRange().isEmpty()) {
            description.add("(range:%s)".formatted(String.join(";", schemaDp.getRange())));
        }
        if (schemaDp.getMax() > 0) {
            description.add("(min-max:%S...%s)".formatted(schemaDp.getMin(), schemaDp.getMax()));
        }*/
        return String.join(" ", description);
    }

    @Override
    protected @NotNull VariableType getVariableType() {
        /*switch (tuyaEndpointType) {
            case bool -> {
                return VariableType.Bool;
            }
            case dimmer, number -> {
                return VariableType.Float;
            }
            case select -> {
                return VariableType.Enum;
            }
            case color -> {
                return VariableType.Color;
            }
            default -> {
                return VariableType.Any;
            }
        }*/
        return VariableType.Any;
    }

    private WriteHandler createExternalWriteHandler() {
        return null;
        /*switch (tuyaEndpointType) {
            case bool -> {
                return (rawValue, eu) -> {
                    if (Boolean.class.isAssignableFrom(rawValue.getClass())) {
                        setValue(OnOffType.of((boolean) rawValue), eu);
                        return true;
                    }
                    return false;
                };
            }
            case number -> {
                return (rawValue, eu) -> {
                    if (Number.class.isAssignableFrom(rawValue.getClass())) {
                        setValue(new DecimalType((Number) rawValue), eu);
                        return true;
                    }
                    return false;
                };
            }
            case color -> {
                return (rawValue, eu) -> {
                    if (rawValue instanceof String) {
                        oldColorMode = ((String) rawValue).length() == 14;
                        setValue(new StringType(hexColorDecode((String) rawValue)), eu);
                        return true;
                    }
                    return false;
                };
            }
            case dimmer -> {
                // brightness
                return (rawValue, eu) -> {
                    if (Double.class.isAssignableFrom(rawValue.getClass())) {
                        double value = (double) rawValue;
                        if (value <= 0) {
                            setValue(DecimalType.ZERO, eu);
                        } else if (value >= schemaDp.getMax()) {
                            setValue(DecimalType.HUNDRED, eu);
                        } else {
                            setValue(new DecimalType(new BigDecimal(100.0 * value / (schemaDp.getMax() - 0))), eu);
                        }
                        return true;
                    }
                    return false;
                };
            }
            default -> {
                // select, string
                return (rawValue, eu) -> {
                    if (rawValue instanceof String) {
                        setValue(new StringType((String) rawValue), eu);
                        return true;
                    }
                    return false;
                };
            }
        }*/
    }

    @Override
    public @NotNull Set<String> getHiddenEndpoints() {
        return Set.of();
    }

    private interface WriteHandler {

        boolean write(Object value, boolean externalUpdate);
    }
}
