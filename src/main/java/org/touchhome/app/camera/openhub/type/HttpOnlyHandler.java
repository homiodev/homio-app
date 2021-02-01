package org.touchhome.app.camera.openhub.type;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ReferenceCountUtil;
import org.touchhome.app.camera.openhub.CameraHandler;
import org.touchhome.app.camera.openhub.IpCameraBindingConstants.FFmpegFormat;
import org.touchhome.app.camera.openhub.UICameraAction;
import org.touchhome.app.camera.openhub.handler.IpCameraHandler;

import java.util.ArrayList;

import static org.touchhome.app.camera.openhub.IpCameraBindingConstants.CHANNEL_THRESHOLD_AUDIO_ALARM;

/**
 * responsible for handling commands for generic and onvif thing types.
 */
public class HttpOnlyHandler extends CameraHandler {

    private IpCameraHandler ipCameraHandler;

    public HttpOnlyHandler(IpCameraHandler handler) {
        ipCameraHandler = handler;
    }

    // This handles the incoming http replies back from the camera.
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ReferenceCountUtil.release(msg);
    }

    // If a camera does not need to poll a request as often as snapshots, it can be
    // added here. Binding steps through the list and sends 1 every 8 seconds.
    public ArrayList<String> getLowPriorityRequests() {
        return new ArrayList<>(0);
    }

    @UICameraAction(name = CHANNEL_THRESHOLD_AUDIO_ALARM)
    public void thresholdAudioAlarm(int threshold) {
        ipCameraHandler.audioThreshold = threshold;
        ipCameraHandler.audioAlarmEnabled = threshold != 0;
        ipCameraHandler.setupFfmpegFormat(FFmpegFormat.RTSP_ALARMS);
    }

  /*  @Override
    protected List<CameraAction> getCameraActions() {
        return CameraAction.builder()
                .add(CHANNEL_THRESHOLD_AUDIO_ALARM, UIFieldType.Slider, param -> {
                    ipCameraHandler.audioThreshold = param.getInt("value");
                    ipCameraHandler.audioAlarmEnabled = ipCameraHandler.audioThreshold != 0;
                    ipCameraHandler.setupFfmpegFormat(FFmpegFormat.RTSP_ALARMS);
                })
                .get();
    }*/
}
