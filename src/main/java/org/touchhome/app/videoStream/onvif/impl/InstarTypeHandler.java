package org.touchhome.app.videoStream.onvif.impl;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ReferenceCountUtil;
import org.touchhome.app.videoStream.entity.OnvifCameraEntity;
import org.touchhome.app.videoStream.onvif.util.CameraTypeHandler;
import org.touchhome.app.videoStream.onvif.util.Helper;
import org.touchhome.app.videoStream.ui.UICameraAction;
import org.touchhome.app.videoStream.handler.OnvifCameraHandler;
import org.touchhome.bundle.api.measure.DecimalType;
import org.touchhome.bundle.api.measure.OnOffType;
import org.touchhome.bundle.api.measure.StringType;

import java.util.ArrayList;

import static org.touchhome.app.videoStream.onvif.util.IpCameraBindingConstants.*;

/**
 * responsible for handling commands, which are sent to one of the channels.
 */
public class InstarTypeHandler extends CameraTypeHandler {
    private String requestUrl = "Empty";

    public InstarTypeHandler(OnvifCameraEntity onvifCameraEntity) {
        super(onvifCameraEntity);
    }

    public InstarTypeHandler(OnvifCameraHandler onvifCameraHandler) {
        super(onvifCameraHandler);
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
            String value1;
            String content = msg.toString();
            onvifCameraHandler.getLog().trace("HTTP Result back from camera is \t:{}:", content);
            switch (requestUrl) {
                case "/param.cgi?cmd=getinfrared":
                    if (content.contains("var infraredstat=\"auto")) {
                        attributes.put(CHANNEL_AUTO_LED, OnOffType.ON);
                    } else {
                        attributes.put(CHANNEL_AUTO_LED, OnOffType.OFF);
                    }
                    break;
                case "/param.cgi?cmd=getoverlayattr&-region=1":// Text Overlays
                    if (content.contains("var show_1=\"0\"")) {
                        attributes.put(CHANNEL_TEXT_OVERLAY, StringType.EMPTY);
                    } else {
                        value1 = Helper.searchString(content, "var name_1=\"");
                        if (!value1.isEmpty()) {
                            attributes.put(CHANNEL_TEXT_OVERLAY, new StringType(value1));
                        }
                    }
                    break;
                case "/cgi-bin/hi3510/param.cgi?cmd=getmdattr":// Motion Alarm
                    // Motion Alarm
                    if (content.contains("var m1_enable=\"1\"")) {
                        attributes.put(CHANNEL_ENABLE_MOTION_ALARM, OnOffType.ON);
                    } else {
                        attributes.put(CHANNEL_ENABLE_MOTION_ALARM, OnOffType.OFF);
                    }
                    break;
                case "/cgi-bin/hi3510/param.cgi?cmd=getaudioalarmattr":// Audio Alarm
                    if (content.contains("var aa_enable=\"1\"")) {
                        attributes.put(CHANNEL_ENABLE_AUDIO_ALARM, OnOffType.ON);
                        value1 = Helper.searchString(content, "var aa_value=\"");
                        if (!value1.isEmpty()) {
                            attributes.put(CHANNEL_THRESHOLD_AUDIO_ALARM, new DecimalType(value1));
                        }
                    } else {
                        attributes.put(CHANNEL_ENABLE_AUDIO_ALARM, OnOffType.OFF);
                    }
                    break;
                case "param.cgi?cmd=getpirattr":// PIR Alarm
                    if (content.contains("var pir_enable=\"1\"")) {
                        attributes.put(CHANNEL_ENABLE_PIR_ALARM, OnOffType.ON);
                    } else {
                        attributes.put(CHANNEL_ENABLE_PIR_ALARM, OnOffType.OFF);
                    }
                    // Reset the Alarm, need to find better place to put this.
                    onvifCameraHandler.noMotionDetected(CHANNEL_PIR_ALARM);
                    break;
                case "/param.cgi?cmd=getioattr":// External Alarm Input
                    if (content.contains("var io_enable=\"1\"")) {
                        attributes.put(CHANNEL_ENABLE_EXTERNAL_ALARM_INPUT, OnOffType.ON);
                    } else {
                        attributes.put(CHANNEL_ENABLE_EXTERNAL_ALARM_INPUT, OnOffType.OFF);
                    }
                    break;
            }
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    @UICameraAction(name = CHANNEL_THRESHOLD_AUDIO_ALARM, icon = "fas fa-volume-up")
    public void thresholdAudioAlarm(int threshold) {
        if (threshold == 0) {
            onvifCameraHandler.sendHttpGET("/cgi-bin/hi3510/param.cgi?cmd=setaudioalarmattr&-aa_enable=0");
        } else {
            onvifCameraHandler.sendHttpGET("/cgi-bin/hi3510/param.cgi?cmd=setaudioalarmattr&-aa_enable=1");
            onvifCameraHandler.sendHttpGET("/cgi-bin/hi3510/param.cgi?cmd=setaudioalarmattr&-aa_enable=1&-aa_value=" + threshold);
        }
    }

    @UICameraAction(name = CHANNEL_ENABLE_AUDIO_ALARM, icon = "fas fa-volume-mute")
    public void enableAudioAlarm(boolean on) {
        onvifCameraHandler.sendHttpGET("/cgi-bin/hi3510/param.cgi?cmd=setaudioalarmattr&-aa_enable=" + boolToInt(on));
    }

    @UICameraAction(name = CHANNEL_ENABLE_MOTION_ALARM, icon = "fas fa-running")
    public void enableMotionAlarm(boolean on) {
        int val = boolToInt(on);
        onvifCameraHandler.sendHttpGET("/cgi-bin/hi3510/param.cgi?cmd=setmdattr&-enable=" + val +
                "&-name=1&cmd=setmdattr&-enable=" + val + "&-name=2&cmd=setmdattr&-enable=" + val + "&-name=3&cmd=setmdattr&-enable=" + val + "&-name=4");
    }

    @UICameraAction(name = CHANNEL_TEXT_OVERLAY, icon = "fas fa-paragraph")
    public void textOverlay(String value) {
        String text = Helper.encodeSpecialChars(value);
        if (text.isEmpty()) {
            onvifCameraHandler.sendHttpGET("/param.cgi?cmd=setoverlayattr&-region=1&-show=0");
        } else {
            onvifCameraHandler.sendHttpGET("/param.cgi?cmd=setoverlayattr&-region=1&-show=1&-name=" + text);
        }
    }

    @UICameraAction(name = CHANNEL_AUTO_LED, icon = "fas fa-lightbulb")
    public void autoLED(boolean on) {
        if (on) {
            onvifCameraHandler.sendHttpGET("/param.cgi?cmd=setinfrared&-infraredstat=auto");
        } else {
            onvifCameraHandler.sendHttpGET("/param.cgi?cmd=setinfrared&-infraredstat=close");
        }
    }

    @UICameraAction(name = CHANNEL_ENABLE_PIR_ALARM, icon = "fas fa-compress-alt")
    public void enablePirAlarm(boolean on) {
        onvifCameraHandler.sendHttpGET("/param.cgi?cmd=setpirattr&-pir_enable=" + boolToInt(on));
    }


    @UICameraAction(name = CHANNEL_ENABLE_EXTERNAL_ALARM_INPUT, icon = "fas fa-external-link-square-alt")
    public void enableExternalAlarmInput(boolean on) {
        onvifCameraHandler.sendHttpGET("/param.cgi?cmd=setioattr&-io_enable=" + boolToInt(on));
    }

    public void alarmTriggered(String alarm) {
        onvifCameraHandler.getLog().debug("Alarm has been triggered:{}", alarm);
        switch (alarm) {
            case "/instar?&active=1":// The motion area boxes 1-4
            case "/instar?&active=2":
            case "/instar?&active=3":
            case "/instar?&active=4":
                onvifCameraHandler.motionDetected(CHANNEL_MOTION_ALARM);
                break;
            case "/instar?&active=5":// PIR
                onvifCameraHandler.motionDetected(CHANNEL_PIR_ALARM);
                break;
            case "/instar?&active=6":// Audio Alarm
                onvifCameraHandler.audioDetected();
                break;
            case "/instar?&active=7":// Motion Area 1
            case "/instar?&active=8":// Motion Area 2
            case "/instar?&active=9":// Motion Area 3
            case "/instar?&active=10":// Motion Area 4
                onvifCameraHandler.motionDetected(CHANNEL_MOTION_ALARM);
                break;
        }
    }

    public ArrayList<String> getLowPriorityRequests() {
        ArrayList<String> lowPriorityRequests = new ArrayList<>(2);
        lowPriorityRequests.add("/cgi-bin/hi3510/param.cgi?cmd=getaudioalarmattr");
        lowPriorityRequests.add("/cgi-bin/hi3510/param.cgi?cmd=getmdattr");
        lowPriorityRequests.add("/param.cgi?cmd=getinfrared");
        lowPriorityRequests.add("/param.cgi?cmd=getoverlayattr&-region=1");
        lowPriorityRequests.add("/param.cgi?cmd=getpirattr");
        lowPriorityRequests.add("/param.cgi?cmd=getioattr"); // ext alarm input on/off
        return lowPriorityRequests;
    }
}
