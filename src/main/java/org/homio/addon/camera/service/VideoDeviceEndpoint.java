package org.homio.addon.camera.service;

import java.util.Set;
import java.util.function.Consumer;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.camera.entity.BaseVideoEntity;
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
public class VideoDeviceEndpoint extends BaseDeviceEndpoint<BaseVideoEntity> {

    private final @NotNull WriteHandler writeHandler;
    private @Nullable @Setter Consumer<State> updateHandler;

    public VideoDeviceEndpoint(
        @NotNull BaseVideoEntity device,
        @NotNull String endpointEntityID,
        float min, float max) {
        this(true, device, endpointEntityID, EndpointType.number, min, max, null);
    }

    public VideoDeviceEndpoint(
        @NotNull BaseVideoEntity device,
        @NotNull String endpointEntityID,
        Set<String> range) {
        this(true, device, endpointEntityID, EndpointType.select, null, null, range);
    }

    public VideoDeviceEndpoint(
        @NotNull BaseVideoEntity device,
        @NotNull String endpointEntityID,
        EndpointType endpointType) {
        this(false, device, endpointEntityID, endpointType, null, null, null);
    }

    private VideoDeviceEndpoint(
        boolean writable,
        @NotNull BaseVideoEntity device,
        @NotNull String endpointEntityID,
        @NotNull EndpointType endpointType,
        @Nullable Float min,
        @Nullable Float max,
        @Nullable Set<String> range) {
        super("VIDEO");
        ConfigDeviceEndpoint configEndpoint = BaseVideoService.CONFIG_DEVICE_SERVICE.getDeviceEndpoints().get(endpointEntityID);

        this.min = min;
        this.max = max;
        this.range = range;
        this.icon = new Icon(
            "fa fa-fw " + (configEndpoint == null ? "fa-camera" : configEndpoint.getIcon()),
            configEndpoint == null ? "#3894B5" : configEndpoint.getIconColor());

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

        if (device.getJsonData().has(endpointEntityID)) {
            State value = endpointType.getReader().apply(device.getJsonData(), endpointEntityID);
            setValue(value, false);
        }

        this.writeHandler = createExternalWriteHandler();
    }

    @Override
    public void writeValue(@NotNull State state) {
        State targetState;
        Object targetValue;
        switch (endpointType) {
            case bool -> {
                targetState = state instanceof OnOffType ? state : OnOffType.of(state.boolValue());
                targetValue = targetState.boolValue();
            }
            case number -> {
                targetState = state instanceof DecimalType ? state : new DecimalType(state.intValue());
                targetValue = state.floatValue();
            }
            default -> {
                targetState = state;
                targetValue = state.stringValue();
            }
        }
        setValue(targetState, true);
        device.setJsonData(endpointEntityID, targetValue);
        entityContext.save(device);
        if (updateHandler != null) {
            updateHandler.accept(targetState);
        }
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

    private WriteHandler createExternalWriteHandler() {
        switch (endpointType) {
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
        }
    }

    @Override
    public @NotNull Set<String> getHiddenEndpoints() {
        return Set.of();
    }

    private interface WriteHandler {

        boolean write(Object value, boolean externalUpdate);
    }
}
