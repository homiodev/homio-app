package org.touchhome.app.videoStream.onvif.impl;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.log4j.Log4j2;
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
@Log4j2
public class DahuaTypeHandler extends CameraTypeHandler {

    public DahuaTypeHandler(OnvifCameraEntity onvifCameraEntity) {
        super(onvifCameraEntity);
    }

    private void processEvent(String content) {
        int startIndex = content.indexOf("Code=", 12) + 5;// skip --myboundary
        int endIndex = content.indexOf(";", startIndex + 1);
        try {
            String code = content.substring(startIndex, endIndex);
            startIndex = endIndex + 8;// skip ;action=
            endIndex = content.indexOf(";", startIndex);
            String action = content.substring(startIndex, endIndex);
            switch (code) {
                case "VideoMotion":
                    if (action.equals("Start")) {
                        onvifCameraHandler.motionDetected(CHANNEL_MOTION_ALARM);
                    } else if (action.equals("Stop")) {
                        onvifCameraHandler.noMotionDetected(CHANNEL_MOTION_ALARM);
                    }
                    break;
                case "TakenAwayDetection":
                    if (action.equals("Start")) {
                        onvifCameraHandler.motionDetected(CHANNEL_ITEM_TAKEN);
                    } else if (action.equals("Stop")) {
                        onvifCameraHandler.noMotionDetected(CHANNEL_ITEM_TAKEN);
                    }
                    break;
                case "LeftDetection":
                    if (action.equals("Start")) {
                        onvifCameraHandler.motionDetected(CHANNEL_ITEM_LEFT);
                    } else if (action.equals("Stop")) {
                        onvifCameraHandler.noMotionDetected(CHANNEL_ITEM_LEFT);
                    }
                    break;
                case "SmartMotionVehicle":
                    if (action.equals("Start")) {
                        onvifCameraHandler.motionDetected(CHANNEL_CAR_ALARM);
                    } else if (action.equals("Stop")) {
                        onvifCameraHandler.noMotionDetected(CHANNEL_CAR_ALARM);
                    }
                    break;
                case "SmartMotionHuman":
                    if (action.equals("Start")) {
                        onvifCameraHandler.motionDetected(CHANNEL_HUMAN_ALARM);
                    } else if (action.equals("Stop")) {
                        onvifCameraHandler.noMotionDetected(CHANNEL_HUMAN_ALARM);
                    }
                    break;
                case "CrossLineDetection":
                    if (action.equals("Start")) {
                        onvifCameraHandler.motionDetected(CHANNEL_LINE_CROSSING_ALARM);
                    } else if (action.equals("Stop")) {
                        onvifCameraHandler.noMotionDetected(CHANNEL_LINE_CROSSING_ALARM);
                    }
                    break;
                case "AudioMutation":
                    if (action.equals("Start")) {
                        onvifCameraHandler.audioDetected();
                    } else if (action.equals("Stop")) {
                        onvifCameraHandler.noAudioDetected();
                    }
                    break;
                case "FaceDetection":
                    if (action.equals("Start")) {
                        onvifCameraHandler.motionDetected(CHANNEL_FACE_DETECTED);
                    } else if (action.equals("Stop")) {
                        onvifCameraHandler.noMotionDetected(CHANNEL_FACE_DETECTED);
                    }
                    break;
                case "ParkingDetection":
                    if (action.equals("Start")) {
                        attributes.put(CHANNEL_PARKING_ALARM, OnOffType.ON);
                    } else if (action.equals("Stop")) {
                        attributes.put(CHANNEL_PARKING_ALARM, OnOffType.OFF);
                    }
                    break;
                case "CrossRegionDetection":
                    if (action.equals("Start")) {
                        onvifCameraHandler.motionDetected(CHANNEL_FIELD_DETECTION_ALARM);
                    } else if (action.equals("Stop")) {
                        onvifCameraHandler.noMotionDetected(CHANNEL_FIELD_DETECTION_ALARM);
                    }
                    break;
                case "VideoLoss":
                case "VideoBlind":
                    if (action.equals("Start")) {
                        attributes.put(CHANNEL_TOO_DARK_ALARM, OnOffType.ON);
                    } else if (action.equals("Stop")) {
                        attributes.put(CHANNEL_TOO_DARK_ALARM, OnOffType.OFF);
                    }
                    break;
                case "VideoAbnormalDetection":
                    if (action.equals("Start")) {
                        attributes.put(CHANNEL_SCENE_CHANGE_ALARM, OnOffType.ON);
                    } else if (action.equals("Stop")) {
                        attributes.put(CHANNEL_SCENE_CHANGE_ALARM, OnOffType.OFF);
                    }
                    break;
                case "VideoUnFocus":
                    if (action.equals("Start")) {
                        attributes.put(CHANNEL_TOO_BLURRY_ALARM, OnOffType.ON);
                    } else if (action.equals("Stop")) {
                        attributes.put(CHANNEL_TOO_BLURRY_ALARM, OnOffType.OFF);
                    }
                    break;
                case "AlarmLocal":
                    if (action.equals("Start")) {
                        if (content.contains("index=0")) {
                            attributes.put(CHANNEL_EXTERNAL_ALARM_INPUT, OnOffType.ON);
                        } else {
                            attributes.put(CHANNEL_EXTERNAL_ALARM_INPUT2, OnOffType.ON);
                        }
                    } else if (action.equals("Stop")) {
                        if (content.contains("index=0")) {
                            attributes.put(CHANNEL_EXTERNAL_ALARM_INPUT, OnOffType.OFF);
                        } else {
                            attributes.put(CHANNEL_EXTERNAL_ALARM_INPUT2, OnOffType.OFF);
                        }
                    }
                    break;
                case "LensMaskOpen":
                    attributes.put(CHANNEL_ENABLE_PRIVACY_MODE, OnOffType.ON);
                    break;
                case "LensMaskClose":
                    attributes.put(CHANNEL_ENABLE_PRIVACY_MODE, OnOffType.OFF);
                    break;
                case "TimeChange":
                case "NTPAdjustTime":
                case "StorageChange":
                case "Reboot":
                case "NewFile":
                case "VideoMotionInfo":
                case "RtspSessionDisconnect":
                case "LeFunctionStatusSync":
                case "RecordDelete":
                    break;
                default:
                    log.debug("Unrecognised Dahua event, Code={}, action={}", code, action);
            }
        } catch (IndexOutOfBoundsException e) {
            log.debug("IndexOutOfBoundsException on Dahua event. Content was:{}", content);
            return;
        }
    }

    // This handles the incoming http replies back from the camera.
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg == null || ctx == null) {
            return;
        }
        try {
            String content = msg.toString();
            if (content.startsWith("--myboundary")) {
                processEvent(content);
                return;
            }
            onvifCameraHandler.getLog().trace("HTTP Result back from camera is \t:{}:", content);
            // determine if the motion detection is turned on or off.
            if (content.contains("table.MotionDetect[0].Enable=true")) {
                attributes.put(CHANNEL_ENABLE_MOTION_ALARM, OnOffType.ON);
            } else if (content.contains("table.MotionDetect[" + nvrChannel + "].Enable=false")) {
                attributes.put(CHANNEL_ENABLE_MOTION_ALARM, OnOffType.OFF);
            }
            // Handle motion alarm
            if (content.contains("Code=VideoMotion;action=Start;index=0")) {
                onvifCameraHandler.motionDetected(CHANNEL_MOTION_ALARM);
            } else if (content.contains("Code=VideoMotion;action=Stop;index=0")) {
                onvifCameraHandler.noMotionDetected(CHANNEL_MOTION_ALARM);
            }
            // Handle item taken alarm
            if (content.contains("Code=TakenAwayDetection;action=Start;index=0")) {
                onvifCameraHandler.motionDetected(CHANNEL_ITEM_TAKEN);
            } else if (content.contains("Code=TakenAwayDetection;action=Stop;index=0")) {
                onvifCameraHandler.noMotionDetected(CHANNEL_ITEM_TAKEN);
            }
            // Handle item left alarm
            if (content.contains("Code=LeftDetection;action=Start;index=0")) {
                onvifCameraHandler.motionDetected(CHANNEL_ITEM_LEFT);
            } else if (content.contains("Code=LeftDetection;action=Stop;index=0")) {
                onvifCameraHandler.noMotionDetected(CHANNEL_ITEM_LEFT);
            }
            // Handle CrossLineDetection alarm
            if (content.contains("Code=CrossLineDetection;action=Start;index=0")) {
                onvifCameraHandler.motionDetected(CHANNEL_LINE_CROSSING_ALARM);
            } else if (content.contains("Code=CrossLineDetection;action=Stop;index=0")) {
                onvifCameraHandler.noMotionDetected(CHANNEL_LINE_CROSSING_ALARM);
            }
            // determine if the audio alarm is turned on or off.
            if (content.contains("table.AudioDetect[0].MutationDetect=true")) {
                attributes.put(CHANNEL_ENABLE_AUDIO_ALARM, OnOffType.ON);
            } else if (content.contains("table.AudioDetect[0].MutationDetect=false")) {
                attributes.put(CHANNEL_ENABLE_AUDIO_ALARM, OnOffType.OFF);
            }
            // Handle AudioMutation alarm
            if (content.contains("Code=AudioMutation;action=Start;index=0")) {
                onvifCameraHandler.audioDetected();
            } else if (content.contains("Code=AudioMutation;action=Stop;index=0")) {
                onvifCameraHandler.noAudioDetected();
            }
            // Handle AudioMutationThreshold alarm
            if (content.contains("table.AudioDetect[0].MutationThreold=")) {
                String value = onvifCameraHandler.returnValueFromString(content, "table.AudioDetect[0].MutationThreold=");
                attributes.put(CHANNEL_THRESHOLD_AUDIO_ALARM, new DecimalType(value));
            }
            // Handle FaceDetection alarm
            if (content.contains("Code=FaceDetection;action=Start;index=0")) {
                onvifCameraHandler.motionDetected(CHANNEL_FACE_DETECTED);
            } else if (content.contains("Code=FaceDetection;action=Stop;index=0")) {
                onvifCameraHandler.noMotionDetected(CHANNEL_FACE_DETECTED);
            }
            // Handle ParkingDetection alarm
            if (content.contains("Code=ParkingDetection;action=Start;index=0")) {
                onvifCameraHandler.motionDetected(CHANNEL_PARKING_ALARM);
            } else if (content.contains("Code=ParkingDetection;action=Stop;index=0")) {
                onvifCameraHandler.noMotionDetected(CHANNEL_PARKING_ALARM);
            }
            // Handle CrossRegionDetection alarm
            if (content.contains("Code=CrossRegionDetection;action=Start;index=0")) {
                onvifCameraHandler.motionDetected(CHANNEL_FIELD_DETECTION_ALARM);
            } else if (content.contains("Code=CrossRegionDetection;action=Stop;index=0")) {
                onvifCameraHandler.noMotionDetected(CHANNEL_FIELD_DETECTION_ALARM);
            }
            // Handle External Input alarm
            if (content.contains("Code=AlarmLocal;action=Start;index=0")) {
                attributes.put(CHANNEL_EXTERNAL_ALARM_INPUT, OnOffType.ON);
            } else if (content.contains("Code=AlarmLocal;action=Stop;index=0")) {
                attributes.put(CHANNEL_EXTERNAL_ALARM_INPUT, OnOffType.OFF);
            }
            // Handle External Input alarm2
            if (content.contains("Code=AlarmLocal;action=Start;index=1")) {
                attributes.put(CHANNEL_EXTERNAL_ALARM_INPUT2, OnOffType.ON);
            } else if (content.contains("Code=AlarmLocal;action=Stop;index=1")) {
                attributes.put(CHANNEL_EXTERNAL_ALARM_INPUT2, OnOffType.OFF);
            }
            // CrossLineDetection alarm on/off
            if (content.contains("table.VideoAnalyseRule[0][1].Enable=true")) {
                attributes.put(CHANNEL_ENABLE_LINE_CROSSING_ALARM, OnOffType.ON);
            } else if (content.contains("table.VideoAnalyseRule[0][1].Enable=false")) {
                attributes.put(CHANNEL_ENABLE_LINE_CROSSING_ALARM, OnOffType.OFF);
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

    @UICameraAction(name = CHANNEL_ACTIVATE_ALARM_OUTPUT2, icon = "fas fa-bell")
    public void activateAlarmOutput2(boolean on) {
        onvifCameraHandler.sendHttpGET(CM + "setConfig&AlarmOut[1].Mode=" + boolToInt(on));
    }

    @UICameraAction(name = CHANNEL_ACTIVATE_ALARM_OUTPUT, icon = "fas fa-bell")
    public void activateAlarmOutput(boolean on) {
        onvifCameraHandler.sendHttpGET(CM + "setConfig&AlarmOut[1].Mode=" + boolToInt(on));
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

    @UICameraActionGetter(CHANNEL_ENABLE_LINE_CROSSING_ALARM)
    public State getEnableLineCrossingAlarm() {
        return getState(CHANNEL_ENABLE_LINE_CROSSING_ALARM);
    }

    @UICameraAction(name = CHANNEL_ENABLE_LINE_CROSSING_ALARM, icon = "fas fa-grip-lines-vertical")
    public void setEnableLineCrossingAlarm(boolean on) {
        onvifCameraHandler.sendHttpGET(CM + "setConfig&VideoAnalyseRule[0][1].Enable=" + on);
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

    @UICameraAction(name = CHANNEL_AUTO_LED, icon = "fas fa-lightbulb")
    public void autoLED(boolean on) {
        if (on) {
            attributes.put(CHANNEL_ENABLE_LED, null/*UnDefType.UNDEF*/);
            onvifCameraHandler.sendHttpGET(CM + "setConfig&Lighting[0][0].Mode=Auto");
        }
    }

    @UICameraAction(name = CHANNEL_ENABLE_LED, icon = "far fa-lightbulb")
    public void enableLED(boolean on) {
        attributes.put(CHANNEL_AUTO_LED, OnOffType.OFF);
        if (!on) {
            onvifCameraHandler.sendHttpGET(CM + "setConfig&Lighting[0][0].Mode=Off");
        } else {
            onvifCameraHandler
                    .sendHttpGET(CM + "setConfig&Lighting[0][0].Mode=Manual");
        } /*else {
                        ipCameraHandler.sendHttpGET(
                                CM + "setConfig&Lighting[0][0].Mode=Manual&Lighting[0][0].MiddleLight[0].Light="
                                        + command.toString());
                    }*/
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

    public ArrayList<String> getLowPriorityRequests() {
        ArrayList<String> lowPriorityRequests = new ArrayList<>(3);
        lowPriorityRequests.add("/cgi-bin/configManager.cgi?action=getConfig&name=AudioDetect[0]");
        lowPriorityRequests.add("/cgi-bin/configManager.cgi?action=getConfig&name=CrossLineDetection[0]");
        lowPriorityRequests.add("/cgi-bin/configManager.cgi?action=getConfig&name=MotionDetect[0]");
        return lowPriorityRequests;
    }
}
