package org.touchhome.app.camera.entity;

import org.touchhome.app.camera.util.FFMPEGDependencyExecutableInstaller;
import org.touchhome.bundle.api.entity.DeviceBaseEntity;
import org.touchhome.bundle.api.entity.dependency.RequireExecutableDependency;
import org.touchhome.bundle.api.ui.UISidebarMenu;
import org.touchhome.bundle.api.ui.field.UIField;

@RequireExecutableDependency(name = "ffmpeg", installer = FFMPEGDependencyExecutableInstaller.class)
@UISidebarMenu(icon = "fas fa-video", order = 1, parent = UISidebarMenu.TopSidebarMenu.MEDIA, bg = "#5950A7", allowCreateNewItems = true)
public abstract class BaseCameraEntity<T> extends DeviceBaseEntity<IpCameraEntity> {

    @UIField(order = 15, inlineEdit = true)
    public boolean isStart() {
        return getJsonData("start", false);
    }

    public void setStart(boolean start) {
        setJsonData("start", start);
    }
}
