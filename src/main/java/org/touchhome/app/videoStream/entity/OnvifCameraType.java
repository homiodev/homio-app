package org.touchhome.app.videoStream.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.touchhome.app.videoStream.onvif.util.CameraTypeHandler;
import org.touchhome.app.videoStream.onvif.impl.*;
import org.touchhome.bundle.api.Lang;
import org.touchhome.bundle.api.model.HasDescription;

import static org.touchhome.app.videoStream.onvif.util.IpCameraBindingConstants.AMCREST_HANDLER;
import static org.touchhome.app.videoStream.onvif.util.IpCameraBindingConstants.INSTAR_HANDLER;

@Getter
@RequiredArgsConstructor
public enum OnvifCameraType implements HasDescription {
    onvif(HttpOnlyTypeHandler.class, null),
    amcrest(AmcrestTypeHandler.class, AMCREST_HANDLER),
    dahua(DahuaTypeHandler.class, null),
    doorbird(DoorBirdTypeHandler.class, null),
    foscam(FoscamTypeHandler.class, null),
    hikvision(HikvisionTypeHandler.class, null),
    instar(InstarTypeHandler.class, INSTAR_HANDLER);

    private final Class<? extends CameraTypeHandler> cameraHandlerClass;
    private final String handlerName;

    @Override
    public String getDescription() {
        return Lang.getServerMessage("description." + name());
    }
}
