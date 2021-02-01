package org.touchhome.app.camera.openhub.type;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ReferenceCountUtil;
import org.touchhome.app.camera.openhub.CameraHandler;
import org.touchhome.app.camera.openhub.Helper;
import org.touchhome.app.camera.openhub.UICameraAction;
import org.touchhome.app.camera.openhub.handler.IpCameraHandler;
import org.touchhome.bundle.api.measure.DecimalType;
import org.touchhome.bundle.api.measure.OnOffType;
import org.touchhome.bundle.api.measure.StringType;

import java.util.ArrayList;

import static org.touchhome.app.camera.openhub.IpCameraBindingConstants.*;

/**
 * responsible for handling commands, which are sent to one of the channels.
 */
public class InstarHandler extends CameraHandler {
    private IpCameraHandler ipCameraHandler;
    private String requestUrl = "Empty";

    public InstarHandler(IpCameraHandler thingHandler) {
        ipCameraHandler = thingHandler;
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
            ipCameraHandler.getLog().trace("HTTP Result back from camera is \t:{}:", content);
            switch (requestUrl) {
                case "/param.cgi?cmd=getinfrared":
                    if (content.contains("var infraredstat=\"auto")) {
                        ipCameraHandler.setChannelState(CHANNEL_AUTO_LED, OnOffType.ON);
                    } else {
                        ipCameraHandler.setChannelState(CHANNEL_AUTO_LED, OnOffType.OFF);
                    }
                    break;
                case "/param.cgi?cmd=getoverlayattr&-region=1":// Text Overlays
                    if (content.contains("var show_1=\"0\"")) {
                        ipCameraHandler.setChannelState(CHANNEL_TEXT_OVERLAY, StringType.EMPTY);
                    } else {
                        value1 = Helper.searchString(content, "var name_1=\"");
                        if (!value1.isEmpty()) {
                            ipCameraHandler.setChannelState(CHANNEL_TEXT_OVERLAY, new StringType(value1));
                        }
                    }
                    break;
                case "/cgi-bin/hi3510/param.cgi?cmd=getmdattr":// Motion Alarm
                    // Motion Alarm
                    if (content.contains("var m1_enable=\"1\"")) {
                        ipCameraHandler.setChannelState(CHANNEL_ENABLE_MOTION_ALARM, OnOffType.ON);
                    } else {
                        ipCameraHandler.setChannelState(CHANNEL_ENABLE_MOTION_ALARM, OnOffType.OFF);
                    }
                    break;
                case "/cgi-bin/hi3510/param.cgi?cmd=getaudioalarmattr":// Audio Alarm
                    if (content.contains("var aa_enable=\"1\"")) {
                        ipCameraHandler.setChannelState(CHANNEL_ENABLE_AUDIO_ALARM, OnOffType.ON);
                        value1 = Helper.searchString(content, "var aa_value=\"");
                        if (!value1.isEmpty()) {
                            ipCameraHandler.setChannelState(CHANNEL_THRESHOLD_AUDIO_ALARM, new DecimalType(value1));
                        }
                    } else {
                        ipCameraHandler.setChannelState(CHANNEL_ENABLE_AUDIO_ALARM, OnOffType.OFF);
                    }
                    break;
                case "param.cgi?cmd=getpirattr":// PIR Alarm
                    if (content.contains("var pir_enable=\"1\"")) {
                        ipCameraHandler.setChannelState(CHANNEL_ENABLE_PIR_ALARM, OnOffType.ON);
                    } else {
                        ipCameraHandler.setChannelState(CHANNEL_ENABLE_PIR_ALARM, OnOffType.OFF);
                    }
                    // Reset the Alarm, need to find better place to put this.
                    ipCameraHandler.noMotionDetected(CHANNEL_PIR_ALARM);
                    break;
                case "/param.cgi?cmd=getioattr":// External Alarm Input
                    if (content.contains("var io_enable=\"1\"")) {
                        ipCameraHandler.setChannelState(CHANNEL_ENABLE_EXTERNAL_ALARM_INPUT, OnOffType.ON);
                    } else {
                        ipCameraHandler.setChannelState(CHANNEL_ENABLE_EXTERNAL_ALARM_INPUT, OnOffType.OFF);
                    }
                    break;
            }
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    @UICameraAction(name = CHANNEL_THRESHOLD_AUDIO_ALARM)
    public void thresholdAudioAlarm(int threshold) {
        if (threshold == 0) {
            ipCameraHandler.sendHttpGET("/cgi-bin/hi3510/param.cgi?cmd=setaudioalarmattr&-aa_enable=0");
        } else {
            ipCameraHandler.sendHttpGET("/cgi-bin/hi3510/param.cgi?cmd=setaudioalarmattr&-aa_enable=1");
            ipCameraHandler.sendHttpGET("/cgi-bin/hi3510/param.cgi?cmd=setaudioalarmattr&-aa_enable=1&-aa_value=" + threshold);
        }
    }

    @UICameraAction(name = CHANNEL_ENABLE_AUDIO_ALARM)
    public void enableAudioAlarm(boolean on) {
        ipCameraHandler.sendHttpGET("/cgi-bin/hi3510/param.cgi?cmd=setaudioalarmattr&-aa_enable=" + boolToInt(on));
    }

    @UICameraAction(name = CHANNEL_ENABLE_MOTION_ALARM)
    public void enableMotionAlarm(boolean on) {
        int val = boolToInt(on);
        ipCameraHandler.sendHttpGET("/cgi-bin/hi3510/param.cgi?cmd=setmdattr&-enable=" + val +
                "&-name=1&cmd=setmdattr&-enable=" + val + "&-name=2&cmd=setmdattr&-enable=" + val + "&-name=3&cmd=setmdattr&-enable=" + val + "&-name=4");
    }

    @UICameraAction(name = CHANNEL_TEXT_OVERLAY)
    public void textOverlay(String value) {
        String text = Helper.encodeSpecialChars(value);
        if (text.isEmpty()) {
            ipCameraHandler.sendHttpGET("/param.cgi?cmd=setoverlayattr&-region=1&-show=0");
        } else {
            ipCameraHandler.sendHttpGET("/param.cgi?cmd=setoverlayattr&-region=1&-show=1&-name=" + text);
        }
    }

    @UICameraAction(name = CHANNEL_AUTO_LED)
    public void autoLED(boolean on) {
        if (on) {
            ipCameraHandler.sendHttpGET("/param.cgi?cmd=setinfrared&-infraredstat=auto");
        } else {
            ipCameraHandler.sendHttpGET("/param.cgi?cmd=setinfrared&-infraredstat=close");
        }
    }

    @UICameraAction(name = CHANNEL_ENABLE_PIR_ALARM)
    public void enablePirAlarm(boolean on) {
        ipCameraHandler.sendHttpGET("/param.cgi?cmd=setpirattr&-pir_enable=" + boolToInt(on));
    }


    @UICameraAction(name = CHANNEL_ENABLE_EXTERNAL_ALARM_INPUT)
    public void enableExternalAlarmInput(boolean on) {
        ipCameraHandler.sendHttpGET("/param.cgi?cmd=setioattr&-io_enable=" + boolToInt(on));
    }

   /* @Override
    protected List<CameraAction> getCameraActions() {
        return CameraAction.builder()
                .add(CHANNEL_THRESHOLD_AUDIO_ALARM, UIFieldType.Slider, param -> {
                    int value = param.getInt("value");
                    if (value == 0) {
                        ipCameraHandler.sendHttpGET("/cgi-bin/hi3510/param.cgi?cmd=setaudioalarmattr&-aa_enable=0");
                    } else {
                        ipCameraHandler.sendHttpGET("/cgi-bin/hi3510/param.cgi?cmd=setaudioalarmattr&-aa_enable=1");
                        ipCameraHandler.sendHttpGET("/cgi-bin/hi3510/param.cgi?cmd=setaudioalarmattr&-aa_enable=1&-aa_value=" + value);
                    }
                })
                .add(CHANNEL_ENABLE_AUDIO_ALARM, UIFieldType.Boolean, param ->
                        ipCameraHandler.sendHttpGET("/cgi-bin/hi3510/param.cgi?cmd=setaudioalarmattr&-aa_enable=" + boolToInt(param)))
                .add(CHANNEL_ENABLE_MOTION_ALARM, UIFieldType.Boolean, param -> {
                    if (param.getBoolean("value")) {
                        ipCameraHandler.sendHttpGET(
                                "/cgi-bin/hi3510/param.cgi?cmd=setmdattr&-enable=1&-name=1&cmd=setmdattr&-enable=1&-name=2&cmd=setmdattr&-enable=1&-name=3&cmd=setmdattr&-enable=1&-name=4");
                    } else {
                        ipCameraHandler.sendHttpGET(
                                "/cgi-bin/hi3510/param.cgi?cmd=setmdattr&-enable=0&-name=1&cmd=setmdattr&-enable=0&-name=2&cmd=setmdattr&-enable=0&-name=3&cmd=setmdattr&-enable=0&-name=4");
                    }
                })
                .add(CHANNEL_TEXT_OVERLAY, UIFieldType.String, param -> {
                    String text = Helper.encodeSpecialChars(param.getString("value"));
                    if (text.isEmpty()) {
                        ipCameraHandler.sendHttpGET("/param.cgi?cmd=setoverlayattr&-region=1&-show=0");
                    } else {
                        ipCameraHandler.sendHttpGET("/param.cgi?cmd=setoverlayattr&-region=1&-show=1&-name=" + text);
                    }
                })
                .add(CHANNEL_AUTO_LED, UIFieldType.Boolean, param -> {
                    if (param.getBoolean("value")) {
                        ipCameraHandler.sendHttpGET("/param.cgi?cmd=setinfrared&-infraredstat=auto");
                    } else {
                        ipCameraHandler.sendHttpGET("/param.cgi?cmd=setinfrared&-infraredstat=close");
                    }
                })
                .add(CHANNEL_ENABLE_PIR_ALARM, UIFieldType.Boolean, param ->
                        ipCameraHandler.sendHttpGET("/param.cgi?cmd=setpirattr&-pir_enable=" + boolToInt(param)))
                .add(CHANNEL_ENABLE_EXTERNAL_ALARM_INPUT, UIFieldType.Boolean, param ->
                        ipCameraHandler.sendHttpGET("/param.cgi?cmd=setioattr&-io_enable=" + boolToInt(param)))
                .get();
    }*/

    public void alarmTriggered(String alarm) {
        ipCameraHandler.getLog().debug("Alarm has been triggered:{}", alarm);
        switch (alarm) {
            case "/instar?&active=1":// The motion area boxes 1-4
            case "/instar?&active=2":
            case "/instar?&active=3":
            case "/instar?&active=4":
                ipCameraHandler.motionDetected(CHANNEL_MOTION_ALARM);
                break;
            case "/instar?&active=5":// PIR
                ipCameraHandler.motionDetected(CHANNEL_PIR_ALARM);
                break;
            case "/instar?&active=6":// Audio Alarm
                ipCameraHandler.audioDetected();
                break;
            case "/instar?&active=7":// Motion Area 1
            case "/instar?&active=8":// Motion Area 2
            case "/instar?&active=9":// Motion Area 3
            case "/instar?&active=10":// Motion Area 4
                ipCameraHandler.motionDetected(CHANNEL_MOTION_ALARM);
                break;
        }
    }

    // If a camera does not need to poll a request as often as snapshots, it can be
    // added here. Binding steps through the list.
    public ArrayList<String> getLowPriorityRequests() {
        ArrayList<String> lowPriorityRequests = new ArrayList<>(2);
        lowPriorityRequests.add("/cgi-bin/hi3510/param.cgi?cmd=getaudioalarmattr");
        lowPriorityRequests.add("/cgi-bin/hi3510/param.cgi?cmd=getmdattr");
        lowPriorityRequests.add("/param.cgi?cmd=getinfrared");
        lowPriorityRequests.add("/param.cgi?cmd=getoverlayattr&-region=1");
        lowPriorityRequests.add("/param.cgi?cmd=getpirattr");
        lowPriorityRequests.add("/param.cgi?cmd=getioattr"); // ext alarm input on/off
        // lowPriorityRequests.add("/param.cgi?cmd=getserverinfo");
        return lowPriorityRequests;
    }
}
