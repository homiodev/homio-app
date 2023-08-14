package org.homio.addon.camera.onvif.impl;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ReferenceCountUtil;
import org.homio.addon.camera.onvif.brand.BaseOnvifCameraBrandHandler;
import org.homio.addon.camera.service.OnvifCameraService;

/**
 * responsible for handling commands for generic onvif thing types.
 */
@CameraBrandHandler("Onvif")
public class OnvifBrandHandler extends BaseOnvifCameraBrandHandler {

  public OnvifBrandHandler(OnvifCameraService service) {
    super(service);
  }

    @Override
    public void onCameraConnected() {

    }

    @Override
  public boolean isSupportOnvifEvents() {
    return true;
  }

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) {
    ReferenceCountUtil.release(msg);
  }
}
