package org.homio.addon.imou;

import static org.homio.addon.imou.service.ImouDeviceService.CONFIG_DEVICE_SERVICE;

import java.util.List;
import java.util.function.Supplier;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.Icon;
import org.homio.api.model.OptionModel;
import org.homio.api.model.device.ConfigDeviceEndpoint;
import org.homio.api.model.endpoint.BaseDeviceEndpoint;
import org.homio.api.state.DecimalType;
import org.homio.api.state.OnOffType;
import org.homio.api.state.State;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Getter
@Log4j2
public class ImouDeviceEndpoint extends BaseDeviceEndpoint<ImouDeviceEntity> {

    public ImouDeviceEndpoint(
        @NotNull String endpointEntityID,
        boolean readable,
        boolean writable,
        @NotNull EndpointType endpointType,
        @Nullable Icon icon,
        @NotNull ImouDeviceEntity device) {
        super("IMOU", device.getEntityContext());
        ConfigDeviceEndpoint configEndpoint = CONFIG_DEVICE_SERVICE.getDeviceEndpoints().get(endpointEntityID);

        setIcon(icon == null ? new Icon(
            "fa fa-fw " + (configEndpoint == null ? "fa-tablet-screen-button" : configEndpoint.getIcon()),
            configEndpoint == null ? "#3894B5" : configEndpoint.getIconColor()) : icon);

        init(
            CONFIG_DEVICE_SERVICE,
            endpointEntityID,
            device,
            readable,
            writable,
            endpointEntityID,
            endpointType);
    }

    private @Nullable @Setter Supplier<State> reader;
    private @Nullable @Setter Runnable initializer;

    @Override
    public void readValue() {
        if(!isReadable()) {
            return;
        }
        if (reader != null) {
            setValue(reader.get(), true);
        }
        super.readValue();
    }

    @Override
    public void writeValue(@NotNull State state) {
        Object targetValue;
        State targetState;
        switch (getEndpointType()) {
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
        //getDevice().getService().send(Map.of(dp, targetValue));
    }

    @Override
    public ActionResponseModel onExternalUpdated() {
        return null; // getDevice().getService().send(Map.of(dp, getValue().rawValue()));
    }

    @Override
    public String getVariableGroupID() {
        return "imou-" + getDeviceID();
    }
}
