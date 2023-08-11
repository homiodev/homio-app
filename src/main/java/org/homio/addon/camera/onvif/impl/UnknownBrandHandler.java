package org.homio.addon.camera.onvif.impl;

import org.homio.addon.camera.onvif.brand.BaseOnvifCameraBrandHandler;
import org.homio.addon.camera.service.OnvifCameraService;

public class UnknownBrandHandler extends BaseOnvifCameraBrandHandler {

  public UnknownBrandHandler(OnvifCameraService service) {
    super(service);
  }

  @Override
  public void cameraConnected() {

  }
}
