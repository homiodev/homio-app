package org.homio.addon.camera.onvif.impl;

import static org.homio.addon.camera.CameraConstants.AlarmEvents.MotionAlarm;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_ACTIVATE_ALARM_OUTPUT;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_ACTIVATE_ALARM_OUTPUT2;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_DOORBELL;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_EXTERNAL_LIGHT;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ReferenceCountUtil;
import lombok.NoArgsConstructor;
import org.homio.addon.camera.onvif.brand.BaseOnvifCameraBrandHandler;
import org.homio.addon.camera.service.OnvifCameraService;
import org.homio.api.state.OnOffType;
import org.jetbrains.annotations.Nullable;

/**
 * responsible for handling commands, which are sent to one of the channels.
 */
@NoArgsConstructor
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
                setAttribute(ENDPOINT_DOORBELL, OnOffType.ON);
            }
            if (content.contains("doorbell:L")) {
                setAttribute(ENDPOINT_DOORBELL, OnOffType.OFF);
            }
            if (content.contains("motionsensor:L")) {
                service.motionDetected(false, MotionAlarm);
            }
            if (content.contains("motionsensor:H")) {
                service.motionDetected(true, MotionAlarm);
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
        addEndpoints();
        // do nothing
    }

    private void addEndpoints() {
        service.addEndpointSwitch(ENDPOINT_EXTERNAL_LIGHT, state -> {
            if (state.boolValue()) {
                service.sendHttpGET("/bha-api/light-on.cgi");
            }
        });

        service.addEndpointSwitch(ENDPOINT_ACTIVATE_ALARM_OUTPUT2, state -> {
            if (state.boolValue()) {
                service.sendHttpGET("/bha-api/open-door.cgi?r=2");
            }
        });

        service.addEndpointSwitch(ENDPOINT_ACTIVATE_ALARM_OUTPUT, state -> {
            if (state.boolValue()) {
                service.sendHttpGET("/bha-api/open-door.cgi");
            }
        });

    }

    @Override
    public @Nullable String getSnapshotUri() {
        return "/bha-api/image.cgi";
    }

    @Override
    public String getUrlToKeepOpenForIdleStateEvent() {
        return "/bha-api/monitor.cgi?ring=doorbell,motionsensor";
    }
}
