package org.homio.addon.camera.onvif.impl;

import lombok.NoArgsConstructor;
import org.homio.addon.camera.onvif.brand.BaseOnvifCameraBrandHandler;
import org.homio.addon.camera.service.IpCameraService;

/**
 * responsible for handling commands for generic onvif thing types.
 */
@NoArgsConstructor
@CameraBrandHandler("Onvif")
public class OnvifBrandHandler extends BaseOnvifCameraBrandHandler {

    public OnvifBrandHandler(IpCameraService service) {
        super(service);
    }

    @Override
    public void onCameraConnected() {

    }

    @Override
    public boolean isSupportOnvifEvents() {
        return true;
    }
}
