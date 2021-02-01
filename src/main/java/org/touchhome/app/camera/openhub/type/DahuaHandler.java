package org.touchhome.app.camera.openhub.type;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ReferenceCountUtil;
import lombok.RequiredArgsConstructor;
import org.touchhome.app.camera.openhub.CameraHandler;
import org.touchhome.app.camera.openhub.Helper;
import org.touchhome.app.camera.openhub.UICameraAction;
import org.touchhome.app.camera.openhub.handler.CameraAction;
import org.touchhome.app.camera.openhub.handler.IpCameraHandler;
import org.touchhome.bundle.api.measure.DecimalType;
import org.touchhome.bundle.api.measure.OnOffType;
import org.touchhome.bundle.api.ui.field.UIFieldType;

import java.util.ArrayList;
import java.util.List;

import static org.touchhome.app.camera.openhub.IpCameraBindingConstants.*;

/**
 * responsible for handling commands, which are sent to one of the channels.
 */
@RequiredArgsConstructor
public class DahuaHandler extends CameraHandler {
    private final IpCameraHandler ipCameraHandler;
    private final int nvrChannel;

    // This handles the incoming http replies back from the camera.
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg == null || ctx == null) {
            return;
        }
        try {
            String content = msg.toString();
            ipCameraHandler.getLog().trace("HTTP Result back from camera is \t:{}:", content);
            // determine if the motion detection is turned on or off.
            if (content.contains("table.MotionDetect[0].Enable=true")) {
                ipCameraHandler.setChannelState(CHANNEL_ENABLE_MOTION_ALARM, OnOffType.ON);
            } else if (content.contains("table.MotionDetect[" + nvrChannel + "].Enable=false")) {
                ipCameraHandler.setChannelState(CHANNEL_ENABLE_MOTION_ALARM, OnOffType.OFF);
            }
            // Handle motion alarm
            if (content.contains("Code=VideoMotion;action=Start;index=0")) {
                ipCameraHandler.motionDetected(CHANNEL_MOTION_ALARM);
            } else if (content.contains("Code=VideoMotion;action=Stop;index=0")) {
                ipCameraHandler.noMotionDetected(CHANNEL_MOTION_ALARM);
            }
            // Handle item taken alarm
            if (content.contains("Code=TakenAwayDetection;action=Start;index=0")) {
                ipCameraHandler.motionDetected(CHANNEL_ITEM_TAKEN);
            } else if (content.contains("Code=TakenAwayDetection;action=Stop;index=0")) {
                ipCameraHandler.noMotionDetected(CHANNEL_ITEM_TAKEN);
            }
            // Handle item left alarm
            if (content.contains("Code=LeftDetection;action=Start;index=0")) {
                ipCameraHandler.motionDetected(CHANNEL_ITEM_LEFT);
            } else if (content.contains("Code=LeftDetection;action=Stop;index=0")) {
                ipCameraHandler.noMotionDetected(CHANNEL_ITEM_LEFT);
            }
            // Handle CrossLineDetection alarm
            if (content.contains("Code=CrossLineDetection;action=Start;index=0")) {
                ipCameraHandler.motionDetected(CHANNEL_LINE_CROSSING_ALARM);
            } else if (content.contains("Code=CrossLineDetection;action=Stop;index=0")) {
                ipCameraHandler.noMotionDetected(CHANNEL_LINE_CROSSING_ALARM);
            }
            // determine if the audio alarm is turned on or off.
            if (content.contains("table.AudioDetect[0].MutationDetect=true")) {
                ipCameraHandler.setChannelState(CHANNEL_ENABLE_AUDIO_ALARM, OnOffType.ON);
            } else if (content.contains("table.AudioDetect[0].MutationDetect=false")) {
                ipCameraHandler.setChannelState(CHANNEL_ENABLE_AUDIO_ALARM, OnOffType.OFF);
            }
            // Handle AudioMutation alarm
            if (content.contains("Code=AudioMutation;action=Start;index=0")) {
                ipCameraHandler.audioDetected();
            } else if (content.contains("Code=AudioMutation;action=Stop;index=0")) {
                ipCameraHandler.noAudioDetected();
            }
            // Handle AudioMutationThreshold alarm
            if (content.contains("table.AudioDetect[0].MutationThreold=")) {
                String value = ipCameraHandler.returnValueFromString(content, "table.AudioDetect[0].MutationThreold=");
                ipCameraHandler.setChannelState(CHANNEL_THRESHOLD_AUDIO_ALARM, new DecimalType(value));
            }
            // Handle FaceDetection alarm
            if (content.contains("Code=FaceDetection;action=Start;index=0")) {
                ipCameraHandler.motionDetected(CHANNEL_FACE_DETECTED);
            } else if (content.contains("Code=FaceDetection;action=Stop;index=0")) {
                ipCameraHandler.noMotionDetected(CHANNEL_FACE_DETECTED);
            }
            // Handle ParkingDetection alarm
            if (content.contains("Code=ParkingDetection;action=Start;index=0")) {
                ipCameraHandler.motionDetected(CHANNEL_PARKING_ALARM);
            } else if (content.contains("Code=ParkingDetection;action=Stop;index=0")) {
                ipCameraHandler.noMotionDetected(CHANNEL_PARKING_ALARM);
            }
            // Handle CrossRegionDetection alarm
            if (content.contains("Code=CrossRegionDetection;action=Start;index=0")) {
                ipCameraHandler.motionDetected(CHANNEL_FIELD_DETECTION_ALARM);
            } else if (content.contains("Code=CrossRegionDetection;action=Stop;index=0")) {
                ipCameraHandler.noMotionDetected(CHANNEL_FIELD_DETECTION_ALARM);
            }
            // Handle External Input alarm
            if (content.contains("Code=AlarmLocal;action=Start;index=0")) {
                ipCameraHandler.setChannelState(CHANNEL_EXTERNAL_ALARM_INPUT, OnOffType.ON);
            } else if (content.contains("Code=AlarmLocal;action=Stop;index=0")) {
                ipCameraHandler.setChannelState(CHANNEL_EXTERNAL_ALARM_INPUT, OnOffType.OFF);
            }
            // Handle External Input alarm2
            if (content.contains("Code=AlarmLocal;action=Start;index=1")) {
                ipCameraHandler.setChannelState(CHANNEL_EXTERNAL_ALARM_INPUT2, OnOffType.ON);
            } else if (content.contains("Code=AlarmLocal;action=Stop;index=1")) {
                ipCameraHandler.setChannelState(CHANNEL_EXTERNAL_ALARM_INPUT2, OnOffType.OFF);
            }
            // CrossLineDetection alarm on/off
            if (content.contains("table.VideoAnalyseRule[0][1].Enable=true")) {
                ipCameraHandler.setChannelState(CHANNEL_ENABLE_LINE_CROSSING_ALARM, OnOffType.ON);
            } else if (content.contains("table.VideoAnalyseRule[0][1].Enable=false")) {
                ipCameraHandler.setChannelState(CHANNEL_ENABLE_LINE_CROSSING_ALARM, OnOffType.OFF);
            }
            // Privacy Mode on/off
            if (content.contains("Code=LensMaskOpen;") || content.contains("table.LeLensMask[0].Enable=true")) {
                ipCameraHandler.setChannelState(CHANNEL_ENABLE_PRIVACY_MODE, OnOffType.ON);
            } else if (content.contains("Code=LensMaskClose;")
                    || content.contains("table.LeLensMask[0].Enable=false")) {
                ipCameraHandler.setChannelState(CHANNEL_ENABLE_PRIVACY_MODE, OnOffType.OFF);
            }
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    @UICameraAction(name = CHANNEL_ENABLE_PRIVACY_MODE)
    public void enablePrivacyMode(boolean on) {
        ipCameraHandler.sendHttpGET(CM + "setConfig&LeLensMask[0].Enable=" + on);
    }

    @UICameraAction(name = CHANNEL_ACTIVATE_ALARM_OUTPUT2)
    public void activateAlarmOutput2(boolean on) {
        ipCameraHandler.sendHttpGET(CM + "setConfig&AlarmOut[1].Mode=" + boolToInt(on));
    }

    @UICameraAction(name = CHANNEL_ACTIVATE_ALARM_OUTPUT)
    public void activateAlarmOutput(boolean on) {
        ipCameraHandler.sendHttpGET(CM + "setConfig&AlarmOut[1].Mode=" + boolToInt(on));
    }

    @UICameraAction(name = CHANNEL_ENABLE_MOTION_ALARM)
    public void enableMotionAlarm(boolean on) {
        if (on) {
            ipCameraHandler.sendHttpGET(CM + "setConfig&MotionDetect[0].Enable=true&MotionDetect[0].EventHandler.Dejitter=1");
        } else {
            ipCameraHandler.sendHttpGET(CM + "setConfig&MotionDetect[0].Enable=false");
        }
    }

    @UICameraAction(name = CHANNEL_ENABLE_LINE_CROSSING_ALARM)
    public void enableLineCrossingAlarm(boolean on) {
        ipCameraHandler.sendHttpGET(CM + "setConfig&VideoAnalyseRule[0][1].Enable=" + on);
    }

    @UICameraAction(name = CHANNEL_ENABLE_AUDIO_ALARM)
    public void enableAudioAlarm(boolean on) {
        if (on) {
            ipCameraHandler.sendHttpGET(CM + "setConfig&AudioDetect[0].MutationDetect=true&AudioDetect[0].EventHandler.Dejitter=1");
        } else {
            ipCameraHandler.sendHttpGET(CM + "setConfig&AudioDetect[0].MutationDetect=false");
        }
    }

    @UICameraAction(name = CHANNEL_THRESHOLD_AUDIO_ALARM)
    public void thresholdAudioAlarm(int threshold) {
        if (threshold == 0) {
            ipCameraHandler.sendHttpGET(CM + "setConfig&AudioDetect[0].MutationThreold=1");
        } else {
            ipCameraHandler.sendHttpGET(CM + "setConfig&AudioDetect[0].MutationThreold=" + threshold);
        }
    }

    @UICameraAction(name = CHANNEL_AUTO_LED)
    public void autoLED(boolean on) {
        if (on) {
            ipCameraHandler.setChannelState(CHANNEL_ENABLE_LED, null/*UnDefType.UNDEF*/);
            ipCameraHandler.sendHttpGET(CM + "setConfig&Lighting[0][0].Mode=Auto");
        }
    }

    @UICameraAction(name = CHANNEL_ENABLE_LED)
    public void enableLED(boolean on) {
        ipCameraHandler.setChannelState(CHANNEL_AUTO_LED, OnOffType.OFF);
        if (!on) {
            ipCameraHandler.sendHttpGET(CM + "setConfig&Lighting[0][0].Mode=Off");
        } else {
            ipCameraHandler
                    .sendHttpGET(CM + "setConfig&Lighting[0][0].Mode=Manual");
        } /*else {
                        ipCameraHandler.sendHttpGET(
                                CM + "setConfig&Lighting[0][0].Mode=Manual&Lighting[0][0].MiddleLight[0].Light="
                                        + command.toString());
                    }*/
    }

    @UICameraAction(name = CHANNEL_TEXT_OVERLAY)
    public void textOverlay(String value) {
        String text = Helper.encodeSpecialChars(value);
        if (text.isEmpty()) {
            ipCameraHandler.sendHttpGET(CM + "setConfig&VideoWidget[0].CustomTitle[1].EncodeBlend=false");
        } else {
            ipCameraHandler.sendHttpGET(CM + "setConfig&VideoWidget[0].CustomTitle[1].EncodeBlend=true&VideoWidget[0].CustomTitle[1].Text="
                    + text);
        }
    }

    /*@Override
    protected List<CameraAction> getCameraActions() {
        return CameraAction.builder()
                .add(CHANNEL_ENABLE_PRIVACY_MODE, UIFieldType.Boolean, param ->
                        ipCameraHandler.sendHttpGET(CM + "setConfig&LeLensMask[0].Enable=" + param.getBoolean("value")),
                        () -> ipCameraHandler.sendHttpGET(CM + "getConfig&name=LeLensMask[0]"))
                .add(CHANNEL_ACTIVATE_ALARM_OUTPUT2, UIFieldType.Boolean, param ->
                        ipCameraHandler.sendHttpGET(CM + "setConfig&AlarmOut[1].Mode=" + boolToInt(param)))
                .add(CHANNEL_ACTIVATE_ALARM_OUTPUT, UIFieldType.Boolean, param ->
                        ipCameraHandler.sendHttpGET(CM + "setConfig&AlarmOut[0].Mode=" + boolToInt(param)))
                .add(CHANNEL_ENABLE_MOTION_ALARM, UIFieldType.Boolean, param -> {
                    if (param.getBoolean("value")) {
                        ipCameraHandler.sendHttpGET(CM + "setConfig&MotionDetect[0].Enable=true&MotionDetect[0].EventHandler.Dejitter=1");
                    } else {
                        ipCameraHandler.sendHttpGET(CM + "setConfig&MotionDetect[0].Enable=false");
                    }
                }, () -> ipCameraHandler.sendHttpGET(CM + "getConfig&name=MotionDetect[0]"))
                .add(CHANNEL_ENABLE_LINE_CROSSING_ALARM, UIFieldType.Boolean, param ->
                                ipCameraHandler.sendHttpGET(CM + "setConfig&VideoAnalyseRule[0][1].Enable=" + param.getBoolean("value")),
                        () -> ipCameraHandler.sendHttpGET(CM + "getConfig&name=VideoAnalyseRule"))
                .add(CHANNEL_ENABLE_AUDIO_ALARM, UIFieldType.Boolean, param -> {
                    if (param.getBoolean("value")) {
                        ipCameraHandler.sendHttpGET(CM + "setConfig&AudioDetect[0].MutationDetect=true&AudioDetect[0].EventHandler.Dejitter=1");
                    } else {
                        ipCameraHandler.sendHttpGET(CM + "setConfig&AudioDetect[0].MutationDetect=false");
                    }
                }, () -> ipCameraHandler.sendHttpGET(CM + "getConfig&name=AudioDetect[0]"))
                .add(CHANNEL_THRESHOLD_AUDIO_ALARM, UIFieldType.Slider, param -> {
                    int threshold = param.getInt("value");

                    if (threshold == 0) {
                        ipCameraHandler.sendHttpGET(CM + "setConfig&AudioDetect[0].MutationThreold=1");
                    } else {
                        ipCameraHandler.sendHttpGET(CM + "setConfig&AudioDetect[0].MutationThreold=" + threshold);
                    }
                }, () -> {
                    // ipCameraHandler.sendHttpGET(CM + "getConfig&name=AudioDetect[0]");
                })
                .add(CHANNEL_AUTO_LED, UIFieldType.Boolean, param -> {
                    if (param.getBoolean("value")) {
                        ipCameraHandler.setChannelState(CHANNEL_ENABLE_LED, null*//*UnDefType.UNDEF*//*);
                        ipCameraHandler.sendHttpGET(CM + "setConfig&Lighting[0][0].Mode=Auto");
                    }
                })
                .add(CHANNEL_ENABLE_LED, UIFieldType.Boolean, param -> {
                    boolean on = param.getBoolean("value");
                    ipCameraHandler.setChannelState(CHANNEL_AUTO_LED, OnOffType.OFF);
                    if (!on) {
                        ipCameraHandler.sendHttpGET(CM + "setConfig&Lighting[0][0].Mode=Off");
                    } else {
                        ipCameraHandler
                                .sendHttpGET(CM + "setConfig&Lighting[0][0].Mode=Manual");
                    } *//*else {
                        ipCameraHandler.sendHttpGET(
                                CM + "setConfig&Lighting[0][0].Mode=Manual&Lighting[0][0].MiddleLight[0].Light="
                                        + command.toString());
                    }*//*
                })
                .add(CHANNEL_TEXT_OVERLAY, UIFieldType.String, param -> {
                    String text = Helper.encodeSpecialChars(param.getString("value"));
                    if (text.isEmpty()) {
                        ipCameraHandler.sendHttpGET(
                                CM + "setConfig&VideoWidget[0].CustomTitle[1].EncodeBlend=false");
                    } else {
                        ipCameraHandler.sendHttpGET(
                                CM + "setConfig&VideoWidget[0].CustomTitle[1].EncodeBlend=true&VideoWidget[0].CustomTitle[1].Text="
                                        + text);
                    }
                })
                .get();
    }*/

    // If a camera does not need to poll a request as often as snapshots, it can be
    // added here. Binding steps through the list.
    public ArrayList<String> getLowPriorityRequests() {
        return new ArrayList<>(1);
    }
}
