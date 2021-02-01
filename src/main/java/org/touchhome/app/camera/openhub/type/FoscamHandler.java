package org.touchhome.app.camera.openhub.type;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ReferenceCountUtil;
import lombok.RequiredArgsConstructor;
import org.touchhome.app.camera.openhub.CameraHandler;
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
public class FoscamHandler extends CameraHandler {
    private static final String CG = "/cgi-bin/CGIProxy.fcgi?cmd=";

    private final IpCameraHandler ipCameraHandler;
    private final String username;
    private final String password;

    // This handles the incoming http replies back from the camera.
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg == null || ctx == null) {
            return;
        }
        try {
            String content = msg.toString();
            ipCameraHandler.getLog().trace("HTTP Result back from camera is \t:{}:", content);
            ////////////// Motion Alarm //////////////
            if (content.contains("<motionDetectAlarm>")) {
                if (content.contains("<motionDetectAlarm>0</motionDetectAlarm>")) {
                    ipCameraHandler.setChannelState(CHANNEL_ENABLE_MOTION_ALARM, OnOffType.OFF);
                } else if (content.contains("<motionDetectAlarm>1</motionDetectAlarm>")) { // Enabled but no alarm
                    ipCameraHandler.setChannelState(CHANNEL_ENABLE_MOTION_ALARM, OnOffType.ON);
                    ipCameraHandler.noMotionDetected(CHANNEL_MOTION_ALARM);
                } else if (content.contains("<motionDetectAlarm>2</motionDetectAlarm>")) {// Enabled, alarm on
                    ipCameraHandler.setChannelState(CHANNEL_ENABLE_MOTION_ALARM, OnOffType.ON);
                    ipCameraHandler.motionDetected(CHANNEL_MOTION_ALARM);
                }
            }

            ////////////// Sound Alarm //////////////
            if (content.contains("<soundAlarm>0</soundAlarm>")) {
                ipCameraHandler.setChannelState(CHANNEL_ENABLE_AUDIO_ALARM, OnOffType.OFF);
                ipCameraHandler.setChannelState(CHANNEL_AUDIO_ALARM, OnOffType.OFF);
            }
            if (content.contains("<soundAlarm>1</soundAlarm>")) {
                ipCameraHandler.setChannelState(CHANNEL_ENABLE_AUDIO_ALARM, OnOffType.ON);
                ipCameraHandler.noAudioDetected();
            }
            if (content.contains("<soundAlarm>2</soundAlarm>")) {
                ipCameraHandler.setChannelState(CHANNEL_ENABLE_AUDIO_ALARM, OnOffType.ON);
                ipCameraHandler.audioDetected();
            }

            ////////////// Sound Threshold //////////////
            if (content.contains("<sensitivity>0</sensitivity>")) {
                ipCameraHandler.setChannelState(CHANNEL_THRESHOLD_AUDIO_ALARM, DecimalType.ZERO);
            }
            if (content.contains("<sensitivity>1</sensitivity>")) {
                ipCameraHandler.setChannelState(CHANNEL_THRESHOLD_AUDIO_ALARM, new DecimalType(50));
            }
            if (content.contains("<sensitivity>2</sensitivity>")) {
                ipCameraHandler.setChannelState(CHANNEL_THRESHOLD_AUDIO_ALARM, DecimalType.HUNDRED);
            }

            //////////////// Infrared LED /////////////////////
            if (content.contains("<infraLedState>0</infraLedState>")) {
                ipCameraHandler.setChannelState(CHANNEL_ENABLE_LED, OnOffType.OFF);
            }
            if (content.contains("<infraLedState>1</infraLedState>")) {
                ipCameraHandler.setChannelState(CHANNEL_ENABLE_LED, OnOffType.ON);
            }

            if (content.contains("</CGI_Result>")) {
                ctx.close();
                ipCameraHandler.getLog().debug("End of FOSCAM handler reached, so closing the channel to the camera now");
            }
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    @UICameraAction(name = CHANNEL_ENABLE_LED)
    public void enableLED(boolean on) {
        // Disable the auto mode first
        ipCameraHandler.sendHttpGET(CG + "setInfraLedConfig&mode=1&usr=" + username + "&pwd=" + password);
        ipCameraHandler.setChannelState(CHANNEL_AUTO_LED, OnOffType.OFF);
        if (on) {
            ipCameraHandler.sendHttpGET(CG + "openInfraLed&usr=" + username + "&pwd=" + password);
        } else {
            ipCameraHandler.sendHttpGET(CG + "closeInfraLed&usr=" + username + "&pwd=" + password);
        }
    }

    @UICameraAction(name = CHANNEL_AUTO_LED)
    public void autoLED(boolean on) {
        if (on) {
            ipCameraHandler.setChannelState(CHANNEL_ENABLE_LED, null/*UnDefType.UNDEF*/);
            ipCameraHandler.sendHttpGET(CG + "setInfraLedConfig&mode=0&usr=" + username + "&pwd=" + password);
        } else {
            ipCameraHandler.sendHttpGET(CG + "setInfraLedConfig&mode=1&usr=" + username + "&pwd=" + password);
        }
    }

    @UICameraAction(name = CHANNEL_THRESHOLD_AUDIO_ALARM)
    public void thresholdAudioAlarm(int threshold) {
        if (threshold == 0) {
            ipCameraHandler.sendHttpGET(CG + "setAudioAlarmConfig&isEnable=0&usr="
                    + username + "&pwd=" + password);
        } else if (threshold <= 33) {
            ipCameraHandler.sendHttpGET(CG + "setAudioAlarmConfig&isEnable=1&sensitivity=0&usr="
                    + username + "&pwd=" + password);
        } else if (threshold <= 66) {
            ipCameraHandler.sendHttpGET(CG + "setAudioAlarmConfig&isEnable=1&sensitivity=1&usr="
                    + username + "&pwd=" + password);
        } else {
            ipCameraHandler.sendHttpGET(CG + "setAudioAlarmConfig&isEnable=1&sensitivity=2&usr="
                    + username + "&pwd=" + password);
        }
    }

    @UICameraAction(name = CHANNEL_ENABLE_AUDIO_ALARM)
    public void enableAudioAlarm(boolean on) {
        if (on) {
            if (ipCameraHandler.getCameraEntity().getCustomAudioAlarmUrl().isEmpty()) {
                ipCameraHandler.sendHttpGET(CG + "setAudioAlarmConfig&isEnable=1&usr="
                        + username + "&pwd=" + password);
            } else {
                ipCameraHandler.sendHttpGET(ipCameraHandler.getCameraEntity().getCustomAudioAlarmUrl());
            }
        } else {
            ipCameraHandler.sendHttpGET(CG + "setAudioAlarmConfig&isEnable=0&usr="
                    + username + "&pwd=" + password);
        }
    }

    @UICameraAction(name = CHANNEL_ENABLE_MOTION_ALARM)
    public void enableMotionAlarm(boolean on) {
        if (on) {
            if (ipCameraHandler.getCameraEntity().getCustomMotionAlarmUrl().isEmpty()) {
                ipCameraHandler.sendHttpGET(CG + "setMotionDetectConfig&isEnable=1&usr="
                        + username + "&pwd=" + password);
                ipCameraHandler.sendHttpGET(CG + "setMotionDetectConfig1&isEnable=1&usr="
                        + username + "&pwd=" + password);
            } else {
                ipCameraHandler.sendHttpGET(ipCameraHandler.getCameraEntity().getCustomMotionAlarmUrl());
            }
        } else {
            ipCameraHandler.sendHttpGET(CG + "setMotionDetectConfig&isEnable=0&usr="
                    + username + "&pwd=" + password);
            ipCameraHandler.sendHttpGET(CG + "setMotionDetectConfig1&isEnable=0&usr="
                    + username + "&pwd=" + password);
        }
    }

    /*@Override
    protected List<CameraAction> getCameraActions() {
        return CameraAction.builder()
                .add(CHANNEL_ENABLE_LED, UIFieldType.Boolean, param -> {
                    // Disable the auto mode first
                    ipCameraHandler.sendHttpGET(
                            CG + "setInfraLedConfig&mode=1&usr=" + username + "&pwd=" + password);
                    ipCameraHandler.setChannelState(CHANNEL_AUTO_LED, OnOffType.OFF);
                    if (!param.getBoolean("value")) {
                        ipCameraHandler.sendHttpGET(
                                CG + "closeInfraLed&usr=" + username + "&pwd=" + password);
                    } else {
                        ipCameraHandler.sendHttpGET(
                                CG + "openInfraLed&usr=" + username + "&pwd=" + password);
                    }
                })
                .add(CHANNEL_AUTO_LED, UIFieldType.Boolean, param -> {
                    if (param.getBoolean("value")) {
                        ipCameraHandler.setChannelState(CHANNEL_ENABLE_LED, null*//*UnDefType.UNDEF*//*);
                        ipCameraHandler.sendHttpGET(
                                CG + "setInfraLedConfig&mode=0&usr=" + username + "&pwd=" + password);
                    } else {
                        ipCameraHandler.sendHttpGET(
                                CG + "setInfraLedConfig&mode=1&usr=" + username + "&pwd=" + password);
                    }
                })
                .add(CHANNEL_THRESHOLD_AUDIO_ALARM, UIFieldType.Slider, param -> {
                    int value = param.getInt("value");
                    if (value == 0) {
                        ipCameraHandler.sendHttpGET(CG + "setAudioAlarmConfig&isEnable=0&usr="
                                + username + "&pwd=" + password);
                    } else if (value <= 33) {
                        ipCameraHandler.sendHttpGET(CG + "setAudioAlarmConfig&isEnable=1&sensitivity=0&usr="
                                + username + "&pwd=" + password);
                    } else if (value <= 66) {
                        ipCameraHandler.sendHttpGET(CG + "setAudioAlarmConfig&isEnable=1&sensitivity=1&usr="
                                + username + "&pwd=" + password);
                    } else {
                        ipCameraHandler.sendHttpGET(CG + "setAudioAlarmConfig&isEnable=1&sensitivity=2&usr="
                                + username + "&pwd=" + password);
                    }
                }, () -> ipCameraHandler.sendHttpGET(
                        CG + "getAudioAlarmConfig&usr=" + username + "&pwd=" + password))
                .add(CHANNEL_ENABLE_AUDIO_ALARM, UIFieldType.Boolean, param -> {
                    if (param.getBoolean("value")) {
                        if (ipCameraHandler.getCameraEntity().getCustomAudioAlarmUrl().isEmpty()) {
                            ipCameraHandler.sendHttpGET(CG + "setAudioAlarmConfig&isEnable=1&usr="
                                    + username + "&pwd=" + password);
                        } else {
                            ipCameraHandler.sendHttpGET(ipCameraHandler.getCameraEntity().getCustomAudioAlarmUrl());
                        }
                    } else {
                        ipCameraHandler.sendHttpGET(CG + "setAudioAlarmConfig&isEnable=0&usr="
                                + username + "&pwd=" + password);
                    }
                }, () -> ipCameraHandler.sendHttpGET(
                        CG + "getAudioAlarmConfig&usr=" + username + "&pwd=" + password))
                .add(CHANNEL_ENABLE_MOTION_ALARM, UIFieldType.Boolean, param -> {
                    if (param.getBoolean("value")) {
                        if (ipCameraHandler.getCameraEntity().getCustomMotionAlarmUrl().isEmpty()) {
                            ipCameraHandler.sendHttpGET(CG + "setMotionDetectConfig&isEnable=1&usr="
                                    + username + "&pwd=" + password);
                            ipCameraHandler.sendHttpGET(CG + "setMotionDetectConfig1&isEnable=1&usr="
                                    + username + "&pwd=" + password);
                        } else {
                            ipCameraHandler.sendHttpGET(ipCameraHandler.getCameraEntity().getCustomMotionAlarmUrl());
                        }
                    } else {
                        ipCameraHandler.sendHttpGET(CG + "setMotionDetectConfig&isEnable=0&usr="
                                + username + "&pwd=" + password);
                        ipCameraHandler.sendHttpGET(CG + "setMotionDetectConfig1&isEnable=0&usr="
                                + username + "&pwd=" + password);
                    }
                }, () -> ipCameraHandler
                        .sendHttpGET(CG + "getDevState&usr=" + username + "&pwd=" + password))
                .get();
    }*/

    // If a camera does not need to poll a request as often as snapshots, it can be
    // added here. Binding steps through the list.
    public ArrayList<String> getLowPriorityRequests() {
        ArrayList<String> lowPriorityRequests = new ArrayList<>(1);
        lowPriorityRequests.add(CG + "getDevState&usr=" + username + "&pwd=" + password);
        return lowPriorityRequests;
    }
}
