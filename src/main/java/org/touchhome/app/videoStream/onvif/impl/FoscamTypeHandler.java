package org.touchhome.app.videoStream.onvif.impl;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ReferenceCountUtil;
import org.touchhome.app.videoStream.entity.OnvifCameraEntity;
import org.touchhome.app.videoStream.onvif.util.CameraTypeHandler;
import org.touchhome.app.videoStream.ui.UICameraAction;
import org.touchhome.app.videoStream.ui.UICameraActionGetter;
import org.touchhome.bundle.api.measure.DecimalType;
import org.touchhome.bundle.api.measure.OnOffType;
import org.touchhome.bundle.api.measure.State;

import java.util.ArrayList;

import static org.touchhome.app.videoStream.onvif.util.IpCameraBindingConstants.*;

/**
 * responsible for handling commands, which are sent to one of the channels.
 */
public class FoscamTypeHandler extends CameraTypeHandler {
    private static final String CG = "/cgi-bin/CGIProxy.fcgi?cmd=";

    public FoscamTypeHandler(OnvifCameraEntity onvifCameraEntity) {
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
            ////////////// Motion Alarm //////////////
            if (content.contains("<motionDetectAlarm>")) {
                if (content.contains("<motionDetectAlarm>0</motionDetectAlarm>")) {
                    attributes.put(CHANNEL_ENABLE_MOTION_ALARM, OnOffType.OFF);
                } else if (content.contains("<motionDetectAlarm>1</motionDetectAlarm>")) { // Enabled but no alarm
                    attributes.put(CHANNEL_ENABLE_MOTION_ALARM, OnOffType.ON);
                    onvifCameraHandler.noMotionDetected(CHANNEL_MOTION_ALARM);
                } else if (content.contains("<motionDetectAlarm>2</motionDetectAlarm>")) {// Enabled, alarm on
                    attributes.put(CHANNEL_ENABLE_MOTION_ALARM, OnOffType.ON);
                    onvifCameraHandler.motionDetected(CHANNEL_MOTION_ALARM);
                }
            }

            ////////////// Sound Alarm //////////////
            if (content.contains("<soundAlarm>0</soundAlarm>")) {
                attributes.put(CHANNEL_ENABLE_AUDIO_ALARM, OnOffType.OFF);
                attributes.put(CHANNEL_AUDIO_ALARM, OnOffType.OFF);
            }
            if (content.contains("<soundAlarm>1</soundAlarm>")) {
                attributes.put(CHANNEL_ENABLE_AUDIO_ALARM, OnOffType.ON);
                onvifCameraHandler.noAudioDetected();
            }
            if (content.contains("<soundAlarm>2</soundAlarm>")) {
                attributes.put(CHANNEL_ENABLE_AUDIO_ALARM, OnOffType.ON);
                onvifCameraHandler.audioDetected();
            }

            ////////////// Sound Threshold //////////////
            if (content.contains("<sensitivity>0</sensitivity>")) {
                attributes.put(CHANNEL_THRESHOLD_AUDIO_ALARM, DecimalType.ZERO);
            }
            if (content.contains("<sensitivity>1</sensitivity>")) {
                attributes.put(CHANNEL_THRESHOLD_AUDIO_ALARM, new DecimalType(50));
            }
            if (content.contains("<sensitivity>2</sensitivity>")) {
                attributes.put(CHANNEL_THRESHOLD_AUDIO_ALARM, DecimalType.HUNDRED);
            }

            //////////////// Infrared LED /////////////////////
            if (content.contains("<infraLedState>0</infraLedState>")) {
                attributes.put(CHANNEL_ENABLE_LED, OnOffType.OFF);
            }
            if (content.contains("<infraLedState>1</infraLedState>")) {
                attributes.put(CHANNEL_ENABLE_LED, OnOffType.ON);
            }

            if (content.contains("</CGI_Result>")) {
                ctx.close();
                onvifCameraHandler.getLog().debug("End of FOSCAM handler reached, so closing the channel to the camera now");
            }
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    @UICameraAction(name = CHANNEL_ENABLE_LED, icon = "far fa-lightbulb")
    public void enableLED(boolean on) {
        // Disable the auto mode first
        onvifCameraHandler.sendHttpGET(CG + "setInfraLedConfig&mode=1&usr=" + username + "&pwd=" + password);
        attributes.put(CHANNEL_AUTO_LED, OnOffType.OFF);
        if (on) {
            onvifCameraHandler.sendHttpGET(CG + "openInfraLed&usr=" + username + "&pwd=" + password);
        } else {
            onvifCameraHandler.sendHttpGET(CG + "closeInfraLed&usr=" + username + "&pwd=" + password);
        }
    }

    @UICameraAction(name = CHANNEL_AUTO_LED, icon = "fas fa-lightbulb")
    public void autoLED(boolean on) {
        if (on) {
            attributes.put(CHANNEL_ENABLE_LED, null/*UnDefType.UNDEF*/);
            onvifCameraHandler.sendHttpGET(CG + "setInfraLedConfig&mode=0&usr=" + username + "&pwd=" + password);
        } else {
            onvifCameraHandler.sendHttpGET(CG + "setInfraLedConfig&mode=1&usr=" + username + "&pwd=" + password);
        }
    }

    @UICameraActionGetter(CHANNEL_THRESHOLD_AUDIO_ALARM)
    public State getThresholdAudioAlarm() {
        return getState(CHANNEL_THRESHOLD_AUDIO_ALARM);
    }

    @UICameraAction(name = CHANNEL_THRESHOLD_AUDIO_ALARM, icon = "fas fa-volume-up")
    public void setThresholdAudioAlarm(int threshold) {
        if (threshold == 0) {
            onvifCameraHandler.sendHttpGET(CG + "setAudioAlarmConfig&isEnable=0&usr="
                    + username + "&pwd=" + password);
        } else if (threshold <= 33) {
            onvifCameraHandler.sendHttpGET(CG + "setAudioAlarmConfig&isEnable=1&sensitivity=0&usr="
                    + username + "&pwd=" + password);
        } else if (threshold <= 66) {
            onvifCameraHandler.sendHttpGET(CG + "setAudioAlarmConfig&isEnable=1&sensitivity=1&usr="
                    + username + "&pwd=" + password);
        } else {
            onvifCameraHandler.sendHttpGET(CG + "setAudioAlarmConfig&isEnable=1&sensitivity=2&usr="
                    + username + "&pwd=" + password);
        }
    }

    @UICameraActionGetter(CHANNEL_ENABLE_AUDIO_ALARM)
    public State getEnableAudioAlarm() {
        return getState(CHANNEL_ENABLE_AUDIO_ALARM);
    }

    @UICameraAction(name = CHANNEL_ENABLE_AUDIO_ALARM, icon = "fas fa-volume-mute")
    public void setEnableAudioAlarm(boolean on) {
        if (on) {
            if (onvifCameraHandler.getCameraEntity().getCustomAudioAlarmUrl().isEmpty()) {
                onvifCameraHandler.sendHttpGET(CG + "setAudioAlarmConfig&isEnable=1&usr="
                        + username + "&pwd=" + password);
            } else {
                onvifCameraHandler.sendHttpGET(onvifCameraHandler.getCameraEntity().getCustomAudioAlarmUrl());
            }
        } else {
            onvifCameraHandler.sendHttpGET(CG + "setAudioAlarmConfig&isEnable=0&usr="
                    + username + "&pwd=" + password);
        }
    }

    @UICameraActionGetter(CHANNEL_ENABLE_MOTION_ALARM)
    public State getEnableMotionAlarm() {
        return getState(CHANNEL_ENABLE_MOTION_ALARM);
    }

    @UICameraAction(name = CHANNEL_ENABLE_MOTION_ALARM, icon = "fas fa-running")
    public void setEnableMotionAlarm(boolean on) {
        if (on) {
            if (onvifCameraHandler.getCameraEntity().getCustomMotionAlarmUrl().isEmpty()) {
                onvifCameraHandler.sendHttpGET(CG + "setMotionDetectConfig&isEnable=1&usr="
                        + username + "&pwd=" + password);
                onvifCameraHandler.sendHttpGET(CG + "setMotionDetectConfig1&isEnable=1&usr="
                        + username + "&pwd=" + password);
            } else {
                onvifCameraHandler.sendHttpGET(onvifCameraHandler.getCameraEntity().getCustomMotionAlarmUrl());
            }
        } else {
            onvifCameraHandler.sendHttpGET(CG + "setMotionDetectConfig&isEnable=0&usr="
                    + username + "&pwd=" + password);
            onvifCameraHandler.sendHttpGET(CG + "setMotionDetectConfig1&isEnable=0&usr="
                    + username + "&pwd=" + password);
        }
    }

    public ArrayList<String> getLowPriorityRequests() {
        ArrayList<String> lowPriorityRequests = new ArrayList<>(2);
        lowPriorityRequests.add(CG + "getDevState&usr=" + username + "&pwd=" + password);
        lowPriorityRequests.add(CG + "getAudioAlarmConfig&usr=" + username + "&pwd=" + password);
        return lowPriorityRequests;
    }
}
