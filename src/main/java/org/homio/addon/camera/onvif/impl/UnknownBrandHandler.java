package org.homio.addon.camera.onvif.impl;

import lombok.NoArgsConstructor;
import org.homio.addon.camera.onvif.brand.BaseOnvifCameraBrandHandler;
import org.homio.addon.camera.service.OnvifCameraService;

@NoArgsConstructor
public class UnknownBrandHandler extends BaseOnvifCameraBrandHandler {

    public UnknownBrandHandler(OnvifCameraService service) {
        super(service);
    }

    @Override
    public void onCameraConnected() {

    }
}
