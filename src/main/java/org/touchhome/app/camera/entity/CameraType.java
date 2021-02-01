package org.touchhome.app.camera.entity;

import org.touchhome.bundle.api.Lang;
import org.touchhome.bundle.api.model.HasDescription;

public enum CameraType implements HasDescription {
    generic, onvif, amcrest, dahua, doorbird, foscam, hikvision, instar;

    @Override
    public String getDescription() {
        return Lang.getServerMessage("description." + name());
    }
}
