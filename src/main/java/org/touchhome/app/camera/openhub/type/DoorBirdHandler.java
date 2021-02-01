package org.touchhome.app.camera.openhub.type;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ReferenceCountUtil;
import org.touchhome.app.camera.openhub.CameraHandler;
import org.touchhome.app.camera.openhub.UICameraAction;
import org.touchhome.app.camera.openhub.handler.CameraAction;
import org.touchhome.app.camera.openhub.handler.IpCameraHandler;
import org.touchhome.bundle.api.measure.OnOffType;
import org.touchhome.bundle.api.ui.field.UIFieldType;

import java.util.ArrayList;
import java.util.List;

import static org.touchhome.app.camera.openhub.IpCameraBindingConstants.*;

/**
 * responsible for handling commands, which are sent to one of the channels.
 */
public class DoorBirdHandler extends CameraHandler {
    private IpCameraHandler ipCameraHandler;

    public DoorBirdHandler(IpCameraHandler handler) {
        ipCameraHandler = handler;
    }

    // This handles the incoming http replies back from the camera.
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg == null || ctx == null) {
            return;
        }
        try {
            String content = msg.toString();
            ipCameraHandler.getLog().trace("HTTP Result back from camera is \t:{}:", content);
            if (content.contains("doorbell:H")) {
                ipCameraHandler.setChannelState(CHANNEL_DOORBELL, OnOffType.ON);
            }
            if (content.contains("doorbell:L")) {
                ipCameraHandler.setChannelState(CHANNEL_DOORBELL, OnOffType.OFF);
            }
            if (content.contains("motionsensor:L")) {
                ipCameraHandler.noMotionDetected(CHANNEL_MOTION_ALARM);
            }
            if (content.contains("motionsensor:H")) {
                ipCameraHandler.motionDetected(CHANNEL_MOTION_ALARM);
            }
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    // If a camera does not need to poll a request as often as snapshots, it can be
    // added here. Binding steps through the list.
    public ArrayList<String> getLowPriorityRequests() {
        return new ArrayList<>(1);
    }

    @UICameraAction(name = CHANNEL_EXTERNAL_LIGHT)
    public void externalLight(boolean on) {
        if (on) {
            ipCameraHandler.sendHttpGET("/bha-api/light-on.cgi");
        }
    }

    @UICameraAction(name = CHANNEL_ACTIVATE_ALARM_OUTPUT2)
    public void activateAlarmOutput2(boolean on) {
        if (on) {
            ipCameraHandler.sendHttpGET("/bha-api/open-door.cgi?r=2");
        }
    }

    @UICameraAction(name = CHANNEL_ACTIVATE_ALARM_OUTPUT)
    public void activateAlarmOutput(boolean on) {
        if (on) {
            ipCameraHandler.sendHttpGET("/bha-api/open-door.cgi");
        }
    }

    /*@Override
    protected List<CameraAction> getCameraActions() {
        return CameraAction.builder()
                .add(CHANNEL_EXTERNAL_LIGHT, UIFieldType.Boolean, param -> {
                    if (param.getBoolean("value")) {
                        ipCameraHandler.sendHttpGET("/bha-api/light-on.cgi");
                    }
                })
                .add(CHANNEL_ACTIVATE_ALARM_OUTPUT2, UIFieldType.Boolean, param -> {
                    if (param.getBoolean("value")) {
                        ipCameraHandler.sendHttpGET("/bha-api/open-door.cgi?r=2");
                    }
                })
                .add(CHANNEL_ACTIVATE_ALARM_OUTPUT, UIFieldType.Boolean, param -> {
                    if (param.getBoolean("value")) {
                        ipCameraHandler.sendHttpGET("/bha-api/open-door.cgi");
                    }
                })
                .get();
    }*/
}
