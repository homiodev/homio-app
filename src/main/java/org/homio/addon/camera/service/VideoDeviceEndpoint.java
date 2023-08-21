package org.homio.addon.camera.service;

import lombok.Setter;
import org.homio.addon.camera.entity.BaseVideoEntity;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.Icon;
import org.homio.api.model.device.ConfigDeviceEndpoint;
import org.homio.api.model.endpoint.BaseDeviceEndpoint;
import org.homio.api.state.DecimalType;
import org.homio.api.state.OnOffType;
import org.homio.api.state.State;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.function.Consumer;

public class VideoDeviceEndpoint extends BaseDeviceEndpoint<BaseVideoEntity> {

    private @Nullable
    @Setter Consumer<State> updateHandler;

    public VideoDeviceEndpoint(
            @NotNull BaseVideoEntity device,
            @NotNull String endpointEntityID,
            float min, float max, boolean writable) {
        this(writable, device, endpointEntityID, EndpointType.number, min, max, null);
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
            EndpointType endpointType,
            boolean writable) {
        this(writable, device, endpointEntityID, endpointType, null, null, null);
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
    public @Nullable ActionResponseModel onExternalUpdated() {
        if (updateHandler == null) {
            throw new IllegalStateException("No update handler set for write handler: " + getEntityID());
        }
        updateHandler.accept(getValue());
        device.getService().updateLastSeen();
        return null;
    }

    @Override
    public @NotNull Set<String> getHiddenEndpoints() {
        return Set.of();
    }
}
