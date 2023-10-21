package org.homio.addon.camera.service;

import java.util.List;
import java.util.function.Function;
import lombok.Setter;
import org.homio.addon.camera.entity.BaseCameraEntity;
import org.homio.api.Context;
import org.homio.api.model.Icon;
import org.homio.api.model.OptionModel;
import org.homio.api.model.device.ConfigDeviceEndpoint;
import org.homio.api.model.endpoint.BaseDeviceEndpoint;
import org.homio.api.state.DecimalType;
import org.homio.api.state.OnOffType;
import org.homio.api.state.State;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CameraDeviceEndpoint extends BaseDeviceEndpoint<BaseCameraEntity<?, ?>> {

    private @Setter @NotNull Function<State, State> valueConverter = state -> state;

    public CameraDeviceEndpoint(
        @NotNull BaseCameraEntity<?, ?> device,
        @NotNull Context context,
        @NotNull String endpointEntityID,
        Float min, Float max, boolean writable) {
        this(writable, device, context, endpointEntityID, EndpointType.number, min, max, null, false);
        setValue(DecimalType.ZERO, false);
    }

    public CameraDeviceEndpoint(
        @NotNull BaseCameraEntity<?, ?> device,
        @NotNull Context context,
        @NotNull String endpointEntityID,
        @NotNull List<OptionModel> range,
        boolean writable) {
        this(writable, device, context, endpointEntityID, EndpointType.select, null, null, range, false);
    }

    public CameraDeviceEndpoint(
        @NotNull BaseCameraEntity<?, ?> device,
        @NotNull Context context,
        @NotNull String endpointEntityID,
        EndpointType endpointType,
        boolean writable) {
        this(writable, device, context, endpointEntityID, endpointType, null, null, null, false);
        this.setValue(OnOffType.OFF, false);
    }

    private CameraDeviceEndpoint(
        boolean writable,
        @NotNull BaseCameraEntity<?, ?> device,
        @NotNull Context context,
        @NotNull String endpointEntityID,
        @NotNull EndpointType endpointType,
        @Nullable Float min,
        @Nullable Float max,
        @Nullable List<OptionModel> range,
        boolean dbValueStorable) {
        super("VIDEO", context);
        ConfigDeviceEndpoint configEndpoint = BaseCameraService.CONFIG_DEVICE_SERVICE.getDeviceEndpoints().get(endpointEntityID);

        setDbValueStorable(dbValueStorable);
        setMin(min);
        setMax(max);
        setRange(range);
        setUnit(configEndpoint == null ? null : configEndpoint.getUnit());
        setIcon(new Icon(
            "fa " + (configEndpoint == null ? "fa-camera" : configEndpoint.getIcon()),
            configEndpoint == null ? "#3894B5" : configEndpoint.getIconColor()));

        init(
            BaseCameraService.CONFIG_DEVICE_SERVICE,
            endpointEntityID,
            device,
            true,
            writable,
            endpointEntityID,
            endpointType);

        getOrCreateVariable();

        if (dbValueStorable && device.getJsonData().has(endpointEntityID)) {
            State value = endpointType.getReader().apply(device.getJsonData(), endpointEntityID);
            setValue(value, false);
        }
    }

    public void setValue(@Nullable State value) {
        setValue(value, true);
    }

    public void setValue(@Nullable State value, boolean externalUpdate) {
        super.setValue(valueConverter.apply(value), externalUpdate);
    }

    @Override
    public String getVariableGroupID() {
        return "video-" + getDeviceID();
    }
}
