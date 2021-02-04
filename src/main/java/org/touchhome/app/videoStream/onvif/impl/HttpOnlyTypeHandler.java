package org.touchhome.app.videoStream.onvif.impl;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ReferenceCountUtil;
import org.touchhome.app.videoStream.entity.OnvifCameraEntity;
import org.touchhome.app.videoStream.onvif.util.CameraTypeHandler;
import org.touchhome.app.videoStream.ui.UICameraAction;

import java.util.ArrayList;

import static org.touchhome.app.videoStream.onvif.util.IpCameraBindingConstants.CHANNEL_THRESHOLD_AUDIO_ALARM;

/**
 * responsible for handling commands for generic and onvif thing types.
 */
public class HttpOnlyTypeHandler extends CameraTypeHandler {

    public HttpOnlyTypeHandler(OnvifCameraEntity onvifCameraEntity) {
        super(onvifCameraEntity);
    }

    // This handles the incoming http replies back from the camera.
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ReferenceCountUtil.release(msg);
    }

    public ArrayList<String> getLowPriorityRequests() {
        return null;
    }

    @UICameraAction(name = CHANNEL_THRESHOLD_AUDIO_ALARM, icon = "fas fa-volume-up")
    public void thresholdAudioAlarm(int threshold) {
        onvifCameraHandler.audioThreshold = threshold;
        onvifCameraHandler.audioAlarmEnabled = threshold != 0;
        onvifCameraHandler.startRtspAlarms();
    }
}
