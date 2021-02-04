package org.touchhome.app.videoStream.onvif.impl;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ReferenceCountUtil;
import org.touchhome.app.videoStream.entity.OnvifCameraEntity;
import org.touchhome.app.videoStream.onvif.util.CameraTypeHandler;
import org.touchhome.app.videoStream.ui.UICameraAction;
import org.touchhome.bundle.api.measure.OnOffType;

import java.util.ArrayList;

import static org.touchhome.app.videoStream.onvif.util.IpCameraBindingConstants.*;

/**
 * responsible for handling commands, which are sent to one of the channels.
 */
public class DoorBirdTypeHandler extends CameraTypeHandler {

    public DoorBirdTypeHandler(OnvifCameraEntity onvifCameraEntity) {
        super(onvifCameraEntity);
    }

    // This handles the incoming http replies back from the camera.
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg == null || ctx == null) {
            return;
        }
        try {
            String content = msg.toString();
            onvifCameraHandler.getLog().trace("HTTP Result back from camera is \t:{}:", content);
            if (content.contains("doorbell:H")) {
                attributes.put(CHANNEL_DOORBELL, OnOffType.ON);
            }
            if (content.contains("doorbell:L")) {
                attributes.put(CHANNEL_DOORBELL, OnOffType.OFF);
            }
            if (content.contains("motionsensor:L")) {
                onvifCameraHandler.noMotionDetected(CHANNEL_MOTION_ALARM);
            }
            if (content.contains("motionsensor:H")) {
                onvifCameraHandler.motionDetected(CHANNEL_MOTION_ALARM);
            }
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    public ArrayList<String> getLowPriorityRequests() {
        return null;
    }

    @UICameraAction(name = CHANNEL_EXTERNAL_LIGHT, icon = "fas fa-sun")
    public void externalLight(boolean on) {
        if (on) {
            onvifCameraHandler.sendHttpGET("/bha-api/light-on.cgi");
        }
    }

    @UICameraAction(name = CHANNEL_ACTIVATE_ALARM_OUTPUT2, icon = "fas fa-bell")
    public void activateAlarmOutput2(boolean on) {
        if (on) {
            onvifCameraHandler.sendHttpGET("/bha-api/open-door.cgi?r=2");
        }
    }

    @UICameraAction(name = CHANNEL_ACTIVATE_ALARM_OUTPUT, icon = "fas fa-bell")
    public void activateAlarmOutput(boolean on) {
        if (on) {
            onvifCameraHandler.sendHttpGET("/bha-api/open-door.cgi");
        }
    }
}
