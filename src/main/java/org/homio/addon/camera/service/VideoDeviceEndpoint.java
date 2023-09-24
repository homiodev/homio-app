package org.homio.addon.camera.service;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.Setter;
import org.homio.addon.camera.entity.BaseVideoEntity;
import org.homio.api.EntityContext;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.Icon;
import org.homio.api.model.device.ConfigDeviceEndpoint;
import org.homio.api.model.endpoint.BaseDeviceEndpoint;
import org.homio.api.state.DecimalType;
import org.homio.api.state.OnOffType;
import org.homio.api.state.State;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class VideoDeviceEndpoint extends BaseDeviceEndpoint<BaseVideoEntity<?, ?>> {

    private @Nullable
    @Setter Consumer<State> updateHandler;
    private final boolean dbValueStorable;
    private @Setter @NotNull Function<State, State> valueConverter = state -> state;

    public VideoDeviceEndpoint(
        @NotNull BaseVideoEntity<?, ?> device,
        @NotNull EntityContext entityContext,
        @NotNull String endpointEntityID,
        Float min, Float max, boolean writable) {
        this(writable, device, entityContext, endpointEntityID, EndpointType.number, min, max, null, false);
        setValue(DecimalType.ZERO, false);
    }

    public VideoDeviceEndpoint(
        @NotNull BaseVideoEntity<?, ?> device,
        @NotNull EntityContext entityContext,
        @NotNull String endpointEntityID,
        Set<String> range, boolean writable) {
        this(writable, device, entityContext, endpointEntityID, EndpointType.select, null, null, range, false);
    }

    public VideoDeviceEndpoint(
        @NotNull BaseVideoEntity<?, ?> device,
        @NotNull EntityContext entityContext,
        @NotNull String endpointEntityID,
        EndpointType endpointType,
        boolean writable) {
        this(writable, device, entityContext, endpointEntityID, endpointType, null, null, null, false);
        this.setValue(OnOffType.OFF, false);
    }

    private VideoDeviceEndpoint(
        boolean writable,
        @NotNull BaseVideoEntity<?, ?> device,
        @NotNull EntityContext entityContext,
        @NotNull String endpointEntityID,
        @NotNull EndpointType endpointType,
        @Nullable Float min,
        @Nullable Float max,
        @Nullable Set<String> range,
        boolean dbValueStorable) {
        super("VIDEO", entityContext);
        ConfigDeviceEndpoint configEndpoint = BaseVideoService.CONFIG_DEVICE_SERVICE.getDeviceEndpoints().get(endpointEntityID);

        this.dbValueStorable = dbValueStorable;
        setMin(min);
        setMax(max);
        setRange(range);
        setIcon(new Icon(
            "fa " + (configEndpoint == null ? "fa-camera" : configEndpoint.getIcon()),
            configEndpoint == null ? "#3894B5" : configEndpoint.getIconColor()));

        init(
            BaseVideoService.CONFIG_DEVICE_SERVICE,
            endpointEntityID,
            device,
            configEndpoint == null ? null : configEndpoint.getUnit(),
            true,
            writable,
            endpointEntityID,
            endpointType);

        if (dbValueStorable && device.getJsonData().has(endpointEntityID)) {
            State value = endpointType.getReader().apply(device.getJsonData(), endpointEntityID);
            setValue(value, false);
        }
    }

    @Override
    public void writeValue(@NotNull State state) {
        State targetState;
        Object targetValue;
        switch (getEndpointType()) {
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
        if (dbValueStorable) {
            getDevice().setJsonData(getEndpointEntityID(), targetValue);
            getEntityContext().save(getDevice());
        }
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
        getDevice().getService().updateLastSeen();
        return null;
    }

    public void setValue(@Nullable State value, boolean externalUpdate) {
        super.setValue(valueConverter.apply(value), externalUpdate);
    }
}
