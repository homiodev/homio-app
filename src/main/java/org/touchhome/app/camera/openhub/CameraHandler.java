package org.touchhome.app.camera.openhub;

import io.netty.channel.ChannelDuplexHandler;

public abstract class CameraHandler extends ChannelDuplexHandler {
    // protected abstract List<CameraAction> getCameraActions();

    public int boolToInt(boolean on) {
        return on ? 1 : 0;
    }
}
