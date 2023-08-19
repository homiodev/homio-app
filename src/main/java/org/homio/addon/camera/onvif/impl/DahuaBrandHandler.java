package org.homio.addon.camera.onvif.impl;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.camera.onvif.brand.BaseOnvifCameraBrandHandler;
import org.homio.addon.camera.onvif.brand.BrandCameraHasAudioAlarm;
import org.homio.addon.camera.onvif.brand.BrandCameraHasMotionAlarm;
import org.homio.addon.camera.onvif.util.Helper;
import org.homio.addon.camera.onvif.util.IpCameraBindingConstants;
import org.homio.addon.camera.service.OnvifCameraService;
import org.homio.addon.camera.ui.UIVideoAction;
import org.homio.addon.camera.ui.UIVideoActionGetter;
import org.homio.api.state.DecimalType;
import org.homio.api.state.OnOffType;
import org.homio.api.state.State;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.homio.addon.camera.VideoConstants.*;

/**
 * responsible for handling commands, which are sent to one of the channels.
 */
@Log4j2
@CameraBrandHandler("Dahua")
public class DahuaBrandHandler extends BaseOnvifCameraBrandHandler implements BrandCameraHasAudioAlarm, BrandCameraHasMotionAlarm {

    private int audioThreshold;

    public DahuaBrandHandler(OnvifCameraService service) {
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
            if (content.startsWith("--myboundary")) {
                processEvent(content);
                return;
            }
            log.debug("[{}]: HTTP Result back from camera is \t:{}:", entityID, content);
            // determine if the motion detection is turned on or off.
            if (content.contains("table.MotionDetect[0].Enable=true")) {
                setAttribute(IpCameraBindingConstants.CHANNEL_ENABLE_MOTION_ALARM, OnOffType.ON);
            } else if (content.contains("table.MotionDetect[" + nvrChannel + "].Enable=false")) {
                setAttribute(IpCameraBindingConstants.CHANNEL_ENABLE_MOTION_ALARM, OnOffType.OFF);
            }

            // determine if the audio alarm is turned on or off.
            if (content.contains("table.AudioDetect[0].MutationDetect=true")) {
                setAttribute(IpCameraBindingConstants.CHANNEL_ENABLE_AUDIO_ALARM, OnOffType.ON);
            } else if (content.contains("table.AudioDetect[0].MutationDetect=false")) {
                setAttribute(IpCameraBindingConstants.CHANNEL_ENABLE_AUDIO_ALARM, OnOffType.OFF);
            }

            // Handle AudioMutationThreshold alarm
            if (content.contains("table.AudioDetect[0].MutationThreold=")) {
                String value = service.returnValueFromString(content, "table.AudioDetect[0].MutationThreold=");
                setAttribute(ENDPOINT_AUDIO_THRESHOLD, new DecimalType(value));
            }

            // CrossLineDetection alarm on/off
            if (content.contains("table.VideoAnalyseRule[0][1].Enable=true")) {
                setAttribute(IpCameraBindingConstants.CHANNEL_ENABLE_LINE_CROSSING_ALARM, OnOffType.ON);
            } else if (content.contains("table.VideoAnalyseRule[0][1].Enable=false")) {
                setAttribute(IpCameraBindingConstants.CHANNEL_ENABLE_LINE_CROSSING_ALARM, OnOffType.OFF);
            }
            // Privacy Mode on/off
            if (content.contains("table.LeLensMask[0].Enable=true")) {
                setAttribute(IpCameraBindingConstants.CHANNEL_ENABLE_PRIVACY_MODE, OnOffType.ON);
            } else if (content.contains("table.LeLensMask[0].Enable=false")) {
                setAttribute(IpCameraBindingConstants.CHANNEL_ENABLE_PRIVACY_MODE, OnOffType.OFF);
            }
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    @UIVideoActionGetter(IpCameraBindingConstants.CHANNEL_ENABLE_PRIVACY_MODE)
    public State getEnablePrivacyMode() {
        return getAttribute(IpCameraBindingConstants.CHANNEL_ENABLE_PRIVACY_MODE);
    }

    @UIVideoAction(name = IpCameraBindingConstants.CHANNEL_ENABLE_PRIVACY_MODE, order = 70, icon = "fas fa-user-secret")
    public void setEnablePrivacyMode(boolean on) {
        service.sendHttpGET(IpCameraBindingConstants.CM + "setConfig&LeLensMask[0].Enable=" + on);
    }

    @UIVideoAction(name = IpCameraBindingConstants.CHANNEL_ACTIVATE_ALARM_OUTPUT2, order = 47, icon = "fas fa-bell")
    public void activateAlarmOutput2(boolean on) {
        service.sendHttpGET(IpCameraBindingConstants.CM + "setConfig&AlarmOut[1].Mode=" + boolToInt(on));
    }

    @UIVideoAction(name = IpCameraBindingConstants.CHANNEL_ACTIVATE_ALARM_OUTPUT, order = 45, icon = "fas fa-bell")
    public void activateAlarmOutput(boolean on) {
        service.sendHttpGET(IpCameraBindingConstants.CM + "setConfig&AlarmOut[1].Mode=" + boolToInt(on));
    }

    @UIVideoActionGetter(IpCameraBindingConstants.CHANNEL_ENABLE_MOTION_ALARM)
    public State getEnableMotionAlarm() {
        return getAttribute(IpCameraBindingConstants.CHANNEL_ENABLE_MOTION_ALARM);
    }

    @UIVideoAction(name = IpCameraBindingConstants.CHANNEL_ENABLE_MOTION_ALARM, order = 14, icon = "fas fa-running")
    public void setEnableMotionAlarm(boolean on) {
        if (on) {
            service.sendHttpGET(IpCameraBindingConstants.CM + "setConfig&MotionDetect[0].Enable=true&MotionDetect[0].EventHandler.Dejitter=1");
        } else {
            service.sendHttpGET(IpCameraBindingConstants.CM + "setConfig&MotionDetect[0].Enable=false");
        }
    }

    @UIVideoActionGetter(IpCameraBindingConstants.CHANNEL_ENABLE_LINE_CROSSING_ALARM)
    public State getEnableLineCrossingAlarm() {
        return getAttribute(IpCameraBindingConstants.CHANNEL_ENABLE_LINE_CROSSING_ALARM);
    }

    @UIVideoAction(name = IpCameraBindingConstants.CHANNEL_ENABLE_LINE_CROSSING_ALARM, order = 150, icon = "fas fa-grip-lines-vertical")
    public void setEnableLineCrossingAlarm(boolean on) {
        service.sendHttpGET(IpCameraBindingConstants.CM + "setConfig&VideoAnalyseRule[0][1].Enable=" + on);
    }

    @Override
    public void setMotionAlarmThreshold(int threshold) {
        if (threshold > 0) {
            service.sendHttpGET(IpCameraBindingConstants.CM + "setConfig&AudioDetect[0].MutationDetect=true&AudioDetect[0].EventHandler.Dejitter=1");
        } else {
            service.sendHttpGET(IpCameraBindingConstants.CM + "setConfig&AudioDetect[0].MutationDetect=false");
        }
    }

    @Override
    public void setAudioAlarmThreshold(int audioThreshold) {
        if (audioThreshold != this.audioThreshold) {
            this.audioThreshold = audioThreshold;
            if (this.audioThreshold > 0) {
                service.sendHttpGET(IpCameraBindingConstants.CM + "setConfig&AudioDetect[0].MutationThreold=" + audioThreshold);
            } else {
                service.sendHttpGET(IpCameraBindingConstants.CM + "setConfig&AudioDetect[0].MutationThreold=1");
            }
        }
    }

    @UIVideoAction(name = ENDPOINT_AUTO_LED, order = 60, icon = "fas fa-lightbulb")
    public void autoLED(boolean on) {
        if (on) {
            setAttribute(ENDPOINT_ENABLE_LED, null/*UnDefType.UNDEF*/);
            service.sendHttpGET(IpCameraBindingConstants.CM + "setConfig&Lighting[0][0].Mode=Auto");
        }
    }

    @UIVideoAction(name = ENDPOINT_ENABLE_LED, order = 50, icon = "far fa-lightbulb")
    public void enableLED(boolean on) {
        getIRLedHandler().accept(on);
    }

    @Override
    public Consumer<Boolean> getIRLedHandler() {
        return on -> {
            setAttribute(ENDPOINT_AUTO_LED, OnOffType.OFF);
            if (!on) {
                service.sendHttpGET(IpCameraBindingConstants.CM + "setConfig&Lighting[0][0].Mode=Off");
            } else {
                service.sendHttpGET(IpCameraBindingConstants.CM + "setConfig&Lighting[0][0].Mode=Manual");
            } /*else {
                        ipCameraHandler.sendHttpGET(
                                CM + "setConfig&Lighting[0][0].Mode=Manual&Lighting[0][0].MiddleLight[0].Light="
                                        + command.toString());
                    }*/
        };
    }

    @Override
    public Supplier<Boolean> getIrLedValueHandler() {
        return () -> Optional.ofNullable(getAttribute(ENDPOINT_ENABLE_LED)).map(State::boolValue).orElse(false);
    }

    @UIVideoAction(name = IpCameraBindingConstants.CHANNEL_TEXT_OVERLAY, order = 100, icon = "fas fa-paragraph")
    public void textOverlay(String value) {
        String text = Helper.encodeSpecialChars(value);
        if (text.isEmpty()) {
            service.sendHttpGET(IpCameraBindingConstants.CM + "setConfig&VideoWidget[0].CustomTitle[1].EncodeBlend=false");
        } else {
            service.sendHttpGET(
                    IpCameraBindingConstants.CM + "setConfig&VideoWidget[0].CustomTitle[1].EncodeBlend=true&VideoWidget[0].CustomTitle[1].Text="
                            + text);
        }
    }

    @Override
    public void pollCameraRunnable() {
        // Check for alarms, channel for NVRs appears not to work at filtering.
        if (service.streamIsStopped("/cgi-bin/eventManager.cgi?action=attach&codes=[All]")) {
            log.info("[{}]: The alarm stream was not running for camera {}, re-starting it now", entityID, getEntity().getIp());
            service.sendHttpGET("/cgi-bin/eventManager.cgi?action=attach&codes=[All]");
        }
    }

    @Override
    public @Nullable String getMjpegUri() {
        return "/cgi-bin/mjpg/video.cgi?channel=" + nvrChannel + "&subtype=1";
    }

    @Override
    public void onCameraConnected() {
        service.sendHttpGET("/cgi-bin/configManager.cgi?action=getConfig&name=AudioDetect[0]");
        service.sendHttpGET("/cgi-bin/configManager.cgi?action=getConfig&name=CrossLineDetection[0]");
        service.sendHttpGET("/cgi-bin/configManager.cgi?action=getConfig&name=MotionDetect[0]");
    }

    @Override
    public @Nullable String getSnapshotUri() {
        return "/cgi-bin/snapshot.cgi?channel=" + nvrChannel;
    }

    @Override
    public String getUrlToKeepOpenForIdleStateEvent() {
        return "/cgi-bin/eventManager.cgi?action=attach&codes=[All]";
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
                case "VideoMotion" -> {
                    if (action.equals("Start")) {
                        service.motionDetected(true, IpCameraBindingConstants.CHANNEL_MOTION_ALARM);
                    } else if (action.equals("Stop")) {
                        service.motionDetected(false, IpCameraBindingConstants.CHANNEL_MOTION_ALARM);
                    }
                }
                case "TakenAwayDetection" -> {
                    if (action.equals("Start")) {
                        service.motionDetected(true, IpCameraBindingConstants.CHANNEL_ITEM_TAKEN);
                    } else if (action.equals("Stop")) {
                        service.motionDetected(false, IpCameraBindingConstants.CHANNEL_ITEM_TAKEN);
                    }
                }
                case "LeftDetection" -> {
                    if (action.equals("Start")) {
                        service.motionDetected(true, IpCameraBindingConstants.CHANNEL_ITEM_LEFT);
                    } else if (action.equals("Stop")) {
                        service.motionDetected(false, IpCameraBindingConstants.CHANNEL_ITEM_LEFT);
                    }
                }
                case "SmartMotionVehicle" -> {
                    if (action.equals("Start")) {
                        service.motionDetected(true, IpCameraBindingConstants.CHANNEL_CAR_ALARM);
                    } else if (action.equals("Stop")) {
                        service.motionDetected(false, IpCameraBindingConstants.CHANNEL_CAR_ALARM);
                    }
                }
                case "SmartMotionHuman" -> {
                    if (action.equals("Start")) {
                        service.motionDetected(true, IpCameraBindingConstants.CHANNEL_HUMAN_ALARM);
                    } else if (action.equals("Stop")) {
                        service.motionDetected(false, IpCameraBindingConstants.CHANNEL_HUMAN_ALARM);
                    }
                }
                case "CrossLineDetection" -> {
                    if (action.equals("Start")) {
                        service.motionDetected(true, IpCameraBindingConstants.CHANNEL_LINE_CROSSING_ALARM);
                    } else if (action.equals("Stop")) {
                        service.motionDetected(false, IpCameraBindingConstants.CHANNEL_LINE_CROSSING_ALARM);
                    }
                }
                case "AudioMutation" -> {
                    if (action.equals("Start")) {
                        service.audioDetected(true);
                    } else if (action.equals("Stop")) {
                        service.audioDetected(false);
                    }
                }
                case "FaceDetection" -> {
                    if (action.equals("Start")) {
                        service.motionDetected(true, IpCameraBindingConstants.CHANNEL_FACE_DETECTED);
                    } else if (action.equals("Stop")) {
                        service.motionDetected(false, IpCameraBindingConstants.CHANNEL_FACE_DETECTED);
                    }
                }
                case "ParkingDetection" -> {
                    if (action.equals("Start")) {
                        setAttribute(IpCameraBindingConstants.CHANNEL_PARKING_ALARM, OnOffType.ON);
                    } else if (action.equals("Stop")) {
                        setAttribute(IpCameraBindingConstants.CHANNEL_PARKING_ALARM, OnOffType.OFF);
                    }
                }
                case "CrossRegionDetection" -> {
                    if (action.equals("Start")) {
                        service.motionDetected(true, IpCameraBindingConstants.CHANNEL_FIELD_DETECTION_ALARM);
                    } else if (action.equals("Stop")) {
                        service.motionDetected(false, IpCameraBindingConstants.CHANNEL_FIELD_DETECTION_ALARM);
                    }
                }
                case "VideoLoss", "VideoBlind" -> {
                    if (action.equals("Start")) {
                        setAttribute(IpCameraBindingConstants.CHANNEL_TOO_DARK_ALARM, OnOffType.ON);
                    } else if (action.equals("Stop")) {
                        setAttribute(IpCameraBindingConstants.CHANNEL_TOO_DARK_ALARM, OnOffType.OFF);
                    }
                }
                case "VideoAbnormalDetection" -> {
                    if (action.equals("Start")) {
                        setAttribute(IpCameraBindingConstants.CHANNEL_SCENE_CHANGE_ALARM, OnOffType.ON);
                    } else if (action.equals("Stop")) {
                        setAttribute(IpCameraBindingConstants.CHANNEL_SCENE_CHANGE_ALARM, OnOffType.OFF);
                    }
                }
                case "VideoUnFocus" -> {
                    if (action.equals("Start")) {
                        setAttribute(IpCameraBindingConstants.CHANNEL_TOO_BLURRY_ALARM, OnOffType.ON);
                    } else if (action.equals("Stop")) {
                        setAttribute(IpCameraBindingConstants.CHANNEL_TOO_BLURRY_ALARM, OnOffType.OFF);
                    }
                }
                case "AlarmLocal" -> {
                    if (action.equals("Start")) {
                        if (content.contains("index=0")) {
                            setAttribute(IpCameraBindingConstants.CHANNEL_EXTERNAL_ALARM_INPUT, OnOffType.ON);
                        } else {
                            setAttribute(IpCameraBindingConstants.CHANNEL_EXTERNAL_ALARM_INPUT2, OnOffType.ON);
                        }
                    } else if (action.equals("Stop")) {
                        if (content.contains("index=0")) {
                            setAttribute(IpCameraBindingConstants.CHANNEL_EXTERNAL_ALARM_INPUT, OnOffType.OFF);
                        } else {
                            setAttribute(IpCameraBindingConstants.CHANNEL_EXTERNAL_ALARM_INPUT2, OnOffType.OFF);
                        }
                    }
                }
                case "LensMaskOpen" -> setAttribute(IpCameraBindingConstants.CHANNEL_ENABLE_PRIVACY_MODE, OnOffType.ON);
                case "LensMaskClose" ->
                        setAttribute(IpCameraBindingConstants.CHANNEL_ENABLE_PRIVACY_MODE, OnOffType.OFF);
                case "TimeChange", "NTPAdjustTime", "StorageChange", "Reboot", "NewFile", "VideoMotionInfo", "RtspSessionDisconnect", "LeFunctionStatusSync", "RecordDelete" -> {
                }
                default -> log.debug("[{}]: Unrecognised Dahua event, Code={}, action={}", entityID, code, action);
            }
        } catch (IndexOutOfBoundsException e) {
            log.debug("[{}]: IndexOutOfBoundsException on Dahua event. Content was:{}", entityID, content);
        }
    }
}
