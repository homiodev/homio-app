package org.homio.addon.camera.onvif.impl;

import lombok.NoArgsConstructor;
import org.homio.addon.camera.onvif.brand.BaseOnvifCameraBrandHandler;
import org.homio.addon.camera.service.IpCameraService;

@NoArgsConstructor
@CameraBrandHandler("Unknown")
public class UnknownBrandHandler extends BaseOnvifCameraBrandHandler {

    public UnknownBrandHandler(IpCameraService service) {
        super(service);
    }

    @Override
    public void onCameraConnected() {

    }
}
