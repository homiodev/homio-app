package org.homio.addon.imou;

import static org.homio.addon.imou.service.ImouDeviceService.CONFIG_DEVICE_SERVICE;

import java.util.function.Supplier;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.Icon;
import org.homio.api.model.device.ConfigDeviceEndpoint;
import org.homio.api.model.endpoint.BaseDeviceEndpoint;
import org.homio.api.state.State;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Getter
@Log4j2
public class ImouDeviceEndpoint extends BaseDeviceEndpoint<ImouDeviceEntity> {

    public Object data;
    private @Nullable Supplier<State> reader;
    private @Nullable @Setter Runnable initializer;

    public ImouDeviceEndpoint(
        @NotNull String endpointEntityID,
        @NotNull EndpointType endpointType,
        @NotNull ImouDeviceEntity device) {
        super("IMOU", device.context());
        ConfigDeviceEndpoint configEndpoint = CONFIG_DEVICE_SERVICE.getDeviceEndpoints().get(endpointEntityID);

        setIcon(new Icon(
            "fa fa-fw fa-" + (configEndpoint == null ? "tablet-screen-button" : configEndpoint.getIcon()),
            configEndpoint == null ? "#3894B5" : configEndpoint.getIconColor()));

        init(
            CONFIG_DEVICE_SERVICE,
            endpointEntityID,
            device,
            false,
            false,
            endpointEntityID,
            endpointType);
    }

    public void setReader(Supplier<State> reader) {
        this.reader = reader;
        if (reader != null) {
            this.setReadable(true);
        }
    }

    @Override
    public void readValue() {
        if(!isReadable()) {
            return;
        }
        if (reader != null) {
            try {
                setValue(reader.get(), true);
            } catch (Exception ignore) {}
        } else {
            super.readValue();
        }
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
