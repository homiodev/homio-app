package org.homio.addon.camera.onvif.impl;

import static org.homio.addon.camera.VideoConstants.CHANNEL_ACTIVATE_ALARM_OUTPUT;
import static org.homio.addon.camera.VideoConstants.CHANNEL_ACTIVATE_ALARM_OUTPUT2;
import static org.homio.addon.camera.VideoConstants.CHANNEL_DOORBELL;
import static org.homio.addon.camera.VideoConstants.CHANNEL_EXTERNAL_LIGHT;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.camera.VideoConstants.Events;
import org.homio.addon.camera.onvif.brand.BaseOnvifCameraBrandHandler;
import org.homio.addon.camera.service.OnvifCameraService;
import org.homio.addon.camera.ui.UIVideoAction;
import org.homio.api.state.OnOffType;
import org.jetbrains.annotations.Nullable;

/**
 * responsible for handling commands, which are sent to one of the channels.
 */
@Log4j2
@CameraBrandHandler("DoorBird")
public class DoorBirdBrandHandler extends BaseOnvifCameraBrandHandler {

    public DoorBirdBrandHandler(OnvifCameraService service) {
        super(service);
    }

    // This handles the incoming http replies back from the camera.
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg == null || ctx == null) {
            return;
        }
        try {
            String content = msg.toString();
            log.debug("[{}]: HTTP Result back from camera is \t:{}:", entityID, content);
            if (content.contains("doorbell:H")) {
                setAttribute(CHANNEL_DOORBELL, OnOffType.ON);
            }
            if (content.contains("doorbell:L")) {
                setAttribute(CHANNEL_DOORBELL, OnOffType.OFF);
            }
            if (content.contains("motionsensor:L")) {
                service.motionDetected(false, Events.MotionAlarm);
            }
            if (content.contains("motionsensor:H")) {
                service.motionDetected(true, Events.MotionAlarm);
            }
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void pollCameraRunnable() {
        if (service.streamIsStopped("/bha-api/monitor.cgi?ring=doorbell,motionsensor")) {
            log.info("[{}]: The alarm stream was not running for camera {}, re-starting it now",
                    entityID, getEntity().getIp());
            service.sendHttpGET("/bha-api/monitor.cgi?ring=doorbell,motionsensor");
        }
    }

    @Override
    public @Nullable String getMjpegUri() {
        return "/bha-api/video.cgi";
    }

    @Override
    public void onCameraConnected() {
        // do nothing
    }

    @Override
    public @Nullable String getSnapshotUri() {
        return "/bha-api/image.cgi";
    }

    @Override
    public String getUrlToKeepOpenForIdleStateEvent() {
        return "/bha-api/monitor.cgi?ring=doorbell,motionsensor";
    }

    @UIVideoAction(name = CHANNEL_EXTERNAL_LIGHT, order = 200, icon = "fas fa-sun")
    public void externalLight(boolean on) {
        if (on) {
            service.sendHttpGET("/bha-api/light-on.cgi");
        }
    }

    @UIVideoAction(name = CHANNEL_ACTIVATE_ALARM_OUTPUT2, order = 47, icon = "fas fa-bell")
    public void activateAlarmOutput2(boolean on) {
        if (on) {
            service.sendHttpGET("/bha-api/open-door.cgi?r=2");
        }
    }

    @UIVideoAction(name = CHANNEL_ACTIVATE_ALARM_OUTPUT, order = 45, icon = "fas fa-bell")
    public void activateAlarmOutput(boolean on) {
        if (on) {
            service.sendHttpGET("/bha-api/open-door.cgi");
        }
    }
}
