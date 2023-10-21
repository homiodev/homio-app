package org.homio.addon.camera.entity.storage;

import lombok.SneakyThrows;
import org.apache.commons.lang3.NotImplementedException;
import org.homio.api.Context;
import org.homio.api.entity.device.DeviceBaseEntity;
import org.homio.api.model.Icon;
import org.homio.api.ui.UISidebarChildren;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("unused")
@UISidebarChildren(icon = "app/client/assets/items/camera/memory-card.png", color = "#AACC00")
public class IpCameraSDCardStorageEntity extends VideoBaseStorageService<IpCameraSDCardStorageEntity> {

    @Override
    protected @NotNull String getDevicePrefix() {
        return "ipcsd";
    }

    @SneakyThrows
    @Override
    public void startRecord(String id, String output, String profile, DeviceBaseEntity deviceEntity, Context context) {
        throw new NotImplementedException();
    }

    @Override
    public void stopRecord(String id, String output, DeviceBaseEntity cameraEntity) {
        throw new NotImplementedException();
    }

    @Override
    public String getDefaultName() {
        return "IpCamera SD storage";
    }

    @Override
    public @Nullable Icon getEntityIcon() {
        return new Icon("fas fa-video", "#AACC00");
    }
}
