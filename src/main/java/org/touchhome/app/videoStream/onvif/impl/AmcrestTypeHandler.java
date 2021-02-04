package org.touchhome.app.videoStream.onvif.impl;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ReferenceCountUtil;
import org.touchhome.app.videoStream.entity.OnvifCameraEntity;
import org.touchhome.app.videoStream.onvif.util.CameraTypeHandler;
import org.touchhome.app.videoStream.onvif.util.Helper;
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
public class AmcrestTypeHandler extends CameraTypeHandler {
    private String requestUrl = "Empty";

    public AmcrestTypeHandler(OnvifCameraEntity onvifCameraEntity) {
        super(onvifCameraEntity);
    }

    @UICameraAction(name = CHANNEL_TEXT_OVERLAY, icon = "fas fa-paragraph")
    public void textOverlay(String value) {
        String text = Helper.encodeSpecialChars(value);
        if (text.isEmpty()) {
            onvifCameraHandler.sendHttpGET(CM + "setConfig&VideoWidget[0].CustomTitle[1].EncodeBlend=false");
        } else {
            onvifCameraHandler.sendHttpGET(CM + "setConfig&VideoWidget[0].CustomTitle[1].EncodeBlend=true&VideoWidget[0].CustomTitle[1].Text="
                    + text);
        }
    }

    @UICameraAction(name = CHANNEL_ENABLE_LED, icon = "far fa-lightbulb")
    public void enableLed(boolean on) {
        attributes.put(CHANNEL_AUTO_LED, OnOffType.OFF);
        if (on) {
            onvifCameraHandler.sendHttpGET(CM + "setConfig&Lighting[0][0].Mode=Manual");
        } else {
            onvifCameraHandler.sendHttpGET(CM + "setConfig&Lighting[0][0].Mode=Off");

        }
    }

    @UICameraAction(name = CHANNEL_AUTO_LED, icon = "fas fa-lightbulb")
    public void autoLed(boolean on) {
        if (on) {
            attributes.put(CHANNEL_ENABLE_LED, null);
            onvifCameraHandler.sendHttpGET(CM + "setConfig&Lighting[0][0].Mode=Auto");
        }
    }

    @UICameraActionGetter(CHANNEL_THRESHOLD_AUDIO_ALARM)
    public State getThresholdAudioAlarm() {
        return getState(CHANNEL_THRESHOLD_AUDIO_ALARM);
    }

    @UICameraAction(name = CHANNEL_THRESHOLD_AUDIO_ALARM, icon = "fas fa-volume-up")
    public void setThresholdAudioAlarm(int threshold) {
        if (threshold == 0) {
            onvifCameraHandler.sendHttpGET(CM + "setConfig&AudioDetect[0].MutationThreold=1");
        } else {
            onvifCameraHandler.sendHttpGET(CM + "setConfig&AudioDetect[0].MutationThreold=" + threshold);
        }
    }

    @UICameraActionGetter(CHANNEL_ENABLE_AUDIO_ALARM)
    public State getEnableAudioAlarm() {
        return getState(CHANNEL_ENABLE_AUDIO_ALARM);
    }

    @UICameraAction(name = CHANNEL_ENABLE_AUDIO_ALARM, icon = "fas fa-volume-mute")
    public void setEnableAudioAlarm(boolean on) {
        if (on) {
            onvifCameraHandler.sendHttpGET(CM + "setConfig&AudioDetect[0].MutationDetect=true&AudioDetect[0].EventHandler.Dejitter=1");
        } else {
            onvifCameraHandler.sendHttpGET(CM + "setConfig&AudioDetect[0].MutationDetect=false");
        }
    }

    @UICameraActionGetter(CHANNEL_ENABLE_LINE_CROSSING_ALARM)
    public State getEnableLineCrossingAlarm() {
        return getState(CHANNEL_ENABLE_LINE_CROSSING_ALARM);
    }

    @UICameraAction(name = CHANNEL_ENABLE_LINE_CROSSING_ALARM, icon = "fas fa-grip-lines-vertical")
    public void setEnableLineCrossingAlarm(boolean on) {
        if (on) {
            onvifCameraHandler.sendHttpGET(CM + "setConfig&VideoAnalyseRule[0][1].Enable=true");
        } else {
            onvifCameraHandler.sendHttpGET(CM + "setConfig&VideoAnalyseRule[0][1].Enable=false");
        }
    }

    @UICameraActionGetter(CHANNEL_ENABLE_MOTION_ALARM)
    public State getEnableMotionAlarm() {
        return getState(CHANNEL_ENABLE_MOTION_ALARM);
    }

    @UICameraAction(name = CHANNEL_ENABLE_MOTION_ALARM, icon = "fas fa-running")
    public void setEnableMotionAlarm(boolean on) {
        if (on) {
            onvifCameraHandler.sendHttpGET(CM + "setConfig&MotionDetect[0].Enable=true&MotionDetect[0].EventHandler.Dejitter=1");
        } else {
            onvifCameraHandler.sendHttpGET(CM + "setConfig&MotionDetect[0].Enable=false");
        }
    }

    @UICameraAction(name = CHANNEL_ACTIVATE_ALARM_OUTPUT, icon = "fas fa-bell")
    public void activateAlarmOutput(boolean on) {
        if (on) {
            onvifCameraHandler.sendHttpGET(CM + "setConfig&AlarmOut[0].Mode=1");
        } else {
            onvifCameraHandler.sendHttpGET(CM + "setConfig&AlarmOut[0].Mode=0");
        }
    }

    @UICameraAction(name = CHANNEL_ACTIVATE_ALARM_OUTPUT2, icon = "fas fa-bell")
    public void activateAlarmOutput2(boolean on) {
        if (on) {
            onvifCameraHandler.sendHttpGET(CM + "setConfig&AlarmOut[1].Mode=1");
        } else {
            onvifCameraHandler.sendHttpGET(CM + "setConfig&AlarmOut[1].Mode=0");
        }
    }

    @UICameraActionGetter(CHANNEL_ENABLE_PRIVACY_MODE)
    public State getEnablePrivacyMode() {
        return getState(CHANNEL_ENABLE_PRIVACY_MODE);
    }

    @UICameraAction(name = CHANNEL_ENABLE_PRIVACY_MODE, icon = "fas fa-user-secret")
    public void setEnablePrivacyMode(boolean on) {
        onvifCameraHandler.sendHttpGET(CM + "setConfig&LeLensMask[0].Enable=" + on);
    }

    public void setURL(String url) {
        requestUrl = url;
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
            if (content.contains("Error: No Events")) {
                if ("/cgi-bin/eventManager.cgi?action=getEventIndexes&code=VideoMotion".equals(requestUrl)) {
                    onvifCameraHandler.noMotionDetected(CHANNEL_MOTION_ALARM);
                } else if ("/cgi-bin/eventManager.cgi?action=getEventIndexes&code=AudioMutation".equals(requestUrl)) {
                    onvifCameraHandler.noAudioDetected();
                }
            } else if (content.contains("channels[0]=0")) {
                if ("/cgi-bin/eventManager.cgi?action=getEventIndexes&code=VideoMotion".equals(requestUrl)) {
                    onvifCameraHandler.motionDetected(CHANNEL_MOTION_ALARM);
                } else if ("/cgi-bin/eventManager.cgi?action=getEventIndexes&code=AudioMutation".equals(requestUrl)) {
                    onvifCameraHandler.audioDetected();
                }
            }

            if (content.contains("table.MotionDetect[0].Enable=false")) {
                attributes.put(CHANNEL_ENABLE_MOTION_ALARM, OnOffType.OFF);
            } else if (content.contains("table.MotionDetect[0].Enable=true")) {
                attributes.put(CHANNEL_ENABLE_MOTION_ALARM, OnOffType.ON);
            }
            // determine if the audio alarm is turned on or off.
            if (content.contains("table.AudioDetect[0].MutationDetect=true")) {
                attributes.put(CHANNEL_ENABLE_AUDIO_ALARM, OnOffType.ON);
            } else if (content.contains("table.AudioDetect[0].MutationDetect=false")) {
                attributes.put(CHANNEL_ENABLE_AUDIO_ALARM, OnOffType.OFF);
            }
            // Handle AudioMutationThreshold alarm
            if (content.contains("table.AudioDetect[0].MutationThreold=")) {
                String value = onvifCameraHandler.returnValueFromString(content, "table.AudioDetect[0].MutationThreold=");
                attributes.put(CHANNEL_THRESHOLD_AUDIO_ALARM, new DecimalType(value));
            }
            // Privacy Mode on/off
            if (content.contains("Code=LensMaskOpen;") || content.contains("table.LeLensMask[0].Enable=true")) {
                attributes.put(CHANNEL_ENABLE_PRIVACY_MODE, OnOffType.ON);
            } else if (content.contains("Code=LensMaskClose;")
                    || content.contains("table.LeLensMask[0].Enable=false")) {
                attributes.put(CHANNEL_ENABLE_PRIVACY_MODE, OnOffType.OFF);
            }
        } finally {
            ReferenceCountUtil.release(msg);
            ctx.close();
        }
    }

    public ArrayList<String> getLowPriorityRequests() {
        ArrayList<String> lowPriorityRequests = new ArrayList<>(4);
        lowPriorityRequests.add("/cgi-bin/configManager.cgi?action=getConfig&name=AudioDetect[0]");
        lowPriorityRequests.add("/cgi-bin/configManager.cgi?action=getConfig&name=LeLensMask[0]");
        lowPriorityRequests.add("/cgi-bin/configManager.cgi?action=getConfig&name=MotionDetect[0]");
        lowPriorityRequests.add("/cgi-bin/configManager.cgi?action=getConfig&name=CrossLineDetection[0]");
        return lowPriorityRequests;
    }
}
