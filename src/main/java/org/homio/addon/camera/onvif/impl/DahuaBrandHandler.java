package org.homio.addon.camera.onvif.impl;

import static org.homio.addon.camera.CameraConstants.AlarmEvents.CarAlarm;
import static org.homio.addon.camera.CameraConstants.AlarmEvents.FaceDetect;
import static org.homio.addon.camera.CameraConstants.AlarmEvents.FieldDetectAlarm;
import static org.homio.addon.camera.CameraConstants.AlarmEvents.HumanAlarm;
import static org.homio.addon.camera.CameraConstants.AlarmEvents.ItemLeftDetection;
import static org.homio.addon.camera.CameraConstants.AlarmEvents.ItemTakenDetection;
import static org.homio.addon.camera.CameraConstants.AlarmEvents.LineCrossAlarm;
import static org.homio.addon.camera.CameraConstants.AlarmEvents.MotionAlarm;
import static org.homio.addon.camera.CameraConstants.AlarmEvents.SceneChangeAlarm;
import static org.homio.addon.camera.CameraConstants.AlarmEvents.TooBlurryAlarm;
import static org.homio.addon.camera.CameraConstants.AlarmEvents.TooDarkAlarm;
import static org.homio.addon.camera.CameraConstants.CM;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_ACTIVATE_ALARM_OUTPUT;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_ACTIVATE_ALARM_OUTPUT2;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_AUDIO_THRESHOLD;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_AUTO_LED;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_ENABLE_AUDIO_ALARM;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_ENABLE_LINE_CROSSING_ALARM;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_ENABLE_MOTION_ALARM;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_ENABLE_PRIVACY_MODE;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_EXTERNAL_ALARM_INPUT;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_EXTERNAL_ALARM_INPUT2;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_PARKING_ALARM;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_SCENE_CHANGE_ALARM;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_TEXT_OVERLAY;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_TOO_BLURRY_ALARM;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_TOO_DARK_ALARM;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ReferenceCountUtil;
import lombok.NoArgsConstructor;
import org.homio.addon.camera.CameraConstants.AlarmEvents;
import org.homio.addon.camera.onvif.brand.BaseOnvifCameraBrandHandler;
import org.homio.addon.camera.onvif.util.Helper;
import org.homio.addon.camera.service.OnvifCameraService;
import org.homio.api.state.DecimalType;
import org.homio.api.state.OnOffType;
import org.jetbrains.annotations.Nullable;

/**
 * responsible for handling commands, which are sent to one of the channels.
 */
@NoArgsConstructor
@CameraBrandHandler("Dahua")
public class DahuaBrandHandler extends BaseOnvifCameraBrandHandler {

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
                getEndpointRequire(ENDPOINT_ENABLE_MOTION_ALARM).setValue(OnOffType.ON);
            } else if (content.contains("table.MotionDetect[" + nvrChannel + "].Enable=false")) {
                getEndpointRequire(ENDPOINT_ENABLE_MOTION_ALARM).setValue(OnOffType.OFF);
            }

            // determine if the audio alarm is turned on or off.
            if (content.contains("table.AudioDetect[0].MutationDetect=true")) {
                getEndpointRequire(ENDPOINT_ENABLE_AUDIO_ALARM).setValue(OnOffType.ON);
            } else if (content.contains("table.AudioDetect[0].MutationDetect=false")) {
                getEndpointRequire(ENDPOINT_ENABLE_AUDIO_ALARM).setValue(OnOffType.OFF);
            }

            // Handle AudioMutationThreshold alarm
            if (content.contains("table.AudioDetect[0].MutationThreold=")) {
                DecimalType value = new DecimalType(service.returnValueFromString(content, "table.AudioDetect[0].MutationThreold="));
                this.audioThreshold = value.intValue();
                getEndpointRequire(ENDPOINT_AUDIO_THRESHOLD).setValue(value);
            }

            // CrossLineDetection alarm on/off
            if (content.contains("table.VideoAnalyseRule[0][1].Enable=true")) {
                getEndpointRequire(ENDPOINT_ENABLE_LINE_CROSSING_ALARM).setValue(OnOffType.ON);
            } else if (content.contains("table.VideoAnalyseRule[0][1].Enable=false")) {
                getEndpointRequire(ENDPOINT_ENABLE_LINE_CROSSING_ALARM).setValue(OnOffType.OFF);
            }
            // Privacy Mode on/off
            if (content.contains("table.LeLensMask[0].Enable=true")) {
                getEndpointRequire(ENDPOINT_ENABLE_PRIVACY_MODE).setValue(OnOffType.ON);
            } else if (content.contains("table.LeLensMask[0].Enable=false")) {
                getEndpointRequire(ENDPOINT_ENABLE_PRIVACY_MODE).setValue(OnOffType.OFF);
            }
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public boolean isHasMotionAlarm() {
        return true;
    }

    @Override
    public void setMotionAlarmThreshold(int threshold) {
        if (threshold > 0) {
            service.sendHttpGET(CM + "setConfig&AudioDetect[0].MutationDetect=true&AudioDetect[0].EventHandler.Dejitter=1");
        } else {
            service.sendHttpGET(CM + "setConfig&AudioDetect[0].MutationDetect=false");
        }
    }

    @Override
    public boolean isHasAudioAlarm() {
        return true;
    }

    @Override
    public void setAudioAlarmThreshold(int audioThreshold) {
        if (audioThreshold != this.audioThreshold) {
            this.audioThreshold = audioThreshold;
            if (this.audioThreshold > 0) {
                service.sendHttpGET(CM + "setConfig&AudioDetect[0].MutationThreold=" + audioThreshold);
            } else {
                service.sendHttpGET(CM + "setConfig&AudioDetect[0].MutationThreold=1");
            }
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
        addEndpoints();
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
                        service.motionDetected(true, MotionAlarm);
                    } else if (action.equals("Stop")) {
                        service.motionDetected(false, MotionAlarm);
                    }
                }
                case "TakenAwayDetection" -> {
                    if (action.equals("Start")) {
                        service.motionDetected(true, ItemTakenDetection);
                    } else if (action.equals("Stop")) {
                        service.motionDetected(false, ItemTakenDetection);
                    }
                }
                case "LeftDetection" -> {
                    if (action.equals("Start")) {
                        service.motionDetected(true, ItemLeftDetection);
                    } else if (action.equals("Stop")) {
                        service.motionDetected(false, ItemLeftDetection);
                    }
                }
                case "SmartMotionVehicle" -> {
                    if (action.equals("Start")) {
                        service.motionDetected(true, CarAlarm);
                    } else if (action.equals("Stop")) {
                        service.motionDetected(false, CarAlarm);
                    }
                }
                case "SmartMotionHuman" -> {
                    if (action.equals("Start")) {
                        service.motionDetected(true, HumanAlarm);
                    } else if (action.equals("Stop")) {
                        service.motionDetected(false, HumanAlarm);
                    }
                }
                case "CrossLineDetection" -> {
                    if (action.equals("Start")) {
                        service.motionDetected(true, LineCrossAlarm);
                    } else if (action.equals("Stop")) {
                        service.motionDetected(false, LineCrossAlarm);
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
                        service.motionDetected(true, FaceDetect);
                    } else if (action.equals("Stop")) {
                        service.motionDetected(false, FaceDetect);
                    }
                }
                case "ParkingDetection" -> {
                    if (action.equals("Start")) {
                        service.motionDetected(true, AlarmEvents.ParkingAlarm);
                    } else if (action.equals("Stop")) {
                        service.motionDetected(false, AlarmEvents.ParkingAlarm);
                    }
                }
                case "CrossRegionDetection" -> {
                    if (action.equals("Start")) {
                        service.motionDetected(true, FieldDetectAlarm);
                    } else if (action.equals("Stop")) {
                        service.motionDetected(false, FieldDetectAlarm);
                    }
                }
                case "VideoLoss", "VideoBlind" -> {
                    if (action.equals("Start")) {
                        service.motionDetected(true, TooDarkAlarm);
                    } else if (action.equals("Stop")) {
                        service.motionDetected(false, TooDarkAlarm);
                    }
                }
                case "VideoAbnormalDetection" -> {
                    if (action.equals("Start")) {
                        service.motionDetected(true, SceneChangeAlarm);
                    } else if (action.equals("Stop")) {
                        service.motionDetected(false, SceneChangeAlarm);
                    }
                }
                case "VideoUnFocus" -> {
                    if (action.equals("Start")) {
                        service.motionDetected(true, TooBlurryAlarm);
                    } else if (action.equals("Stop")) {
                        service.motionDetected(false, TooBlurryAlarm);
                    }
                }
                case "AlarmLocal" -> {
                    if (action.equals("Start")) {
                        if (content.contains("index=0")) {
                            getEndpointRequire(ENDPOINT_EXTERNAL_ALARM_INPUT).setValue(OnOffType.ON);
                        } else {
                            getEndpointRequire(ENDPOINT_EXTERNAL_ALARM_INPUT2).setValue(OnOffType.ON);
                        }
                    } else if (action.equals("Stop")) {
                        if (content.contains("index=0")) {
                            getEndpointRequire(ENDPOINT_EXTERNAL_ALARM_INPUT).setValue(OnOffType.OFF);
                        } else {
                            getEndpointRequire(ENDPOINT_EXTERNAL_ALARM_INPUT2).setValue(OnOffType.OFF);
                        }
                    }
                }
                case "LensMaskOpen" -> {
                    getEndpointRequire(ENDPOINT_ENABLE_PRIVACY_MODE).setValue(OnOffType.ON);
                }
                case "LensMaskClose" -> {
                    getEndpointRequire(ENDPOINT_ENABLE_PRIVACY_MODE).setValue(OnOffType.OFF);
                }
                case "TimeChange", "NTPAdjustTime", "StorageChange", "Reboot", "NewFile", "VideoMotionInfo", "RtspSessionDisconnect", "LeFunctionStatusSync", "RecordDelete" -> {
                }
                default -> log.debug("[{}]: Unrecognised Dahua event, Code={}, action={}", entityID, code, action);
            }
        } catch (IndexOutOfBoundsException e) {
            log.debug("[{}]: IndexOutOfBoundsException on Dahua event. Content was:{}", entityID, content);
        }
    }

    private void addEndpoints() {
        service.addEndpointSwitch(ENDPOINT_ENABLE_PRIVACY_MODE, state ->
            service.sendHttpGET(CM + "setConfig&LeLensMask[0].Enable=" + state.boolValue()));

        service.addEndpointSwitch(ENDPOINT_ACTIVATE_ALARM_OUTPUT, state ->
            service.sendHttpGET(CM + "setConfig&AlarmOut[0].Mode=" + state.intValue()));

        service.addEndpointSwitch(ENDPOINT_ACTIVATE_ALARM_OUTPUT2, state ->
            service.sendHttpGET(CM + "setConfig&AlarmOut[1].Mode=" + state.intValue()));

        service.addEndpointSwitch(ENDPOINT_ENABLE_MOTION_ALARM, state -> {
            if (state.boolValue()) {
                service.sendHttpGET(CM + "setConfig&MotionDetect[0].Enable=true&MotionDetect[0].EventHandler.Dejitter=1");
            } else {
                service.sendHttpGET(CM + "setConfig&MotionDetect[0].Enable=false");
            }
        });

        service.addEndpointSwitch(ENDPOINT_ENABLE_LINE_CROSSING_ALARM, state -> {
            service.sendHttpGET(CM + "setConfig&VideoAnalyseRule[0][1].Enable=" + state.boolValue());
        });

        service.addEndpointSwitch(ENDPOINT_AUTO_LED, state -> {
            service.sendHttpGET(CM + "setConfig&Lighting[0][0].Mode=" + state.boolValue("Auto", "Off"));
            /*else {
                        ipCameraHandler.sendHttpGET(
                                CM + "setConfig&Lighting[0][0].Mode=Manual&Lighting[0][0].MiddleLight[0].Light="
                                        + command.toString());
                    }*/
        });

        service.addEndpointInput(ENDPOINT_TEXT_OVERLAY, state -> {
            String text = Helper.encodeSpecialChars(state.stringValue());
            if (text.isEmpty()) {
                service.sendHttpGET(CM + "setConfig&VideoWidget[0].CustomTitle[1].EncodeBlend=false");
            } else {
                service.sendHttpGET(CM + "setConfig&VideoWidget[0].CustomTitle[1].EncodeBlend=true&VideoWidget[0].CustomTitle[1].Text="
                    + text);
            }
        });
    }
}
