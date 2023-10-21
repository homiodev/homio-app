package org.homio.addon.camera.onvif.impl;

import static org.homio.addon.camera.CameraConstants.AlarmEvent.AudioAlarm;
import static org.homio.addon.camera.CameraConstants.AlarmEvent.MotionAlarm;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_AUDIO_THRESHOLD;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_AUTO_LED;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_ENABLE_AUDIO_ALARM;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_ENABLE_LED;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_ENABLE_MOTION_ALARM;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ReferenceCountUtil;
import lombok.NoArgsConstructor;
import org.homio.addon.camera.entity.IpCameraEntity;
import org.homio.addon.camera.onvif.brand.BaseOnvifCameraBrandHandler;
import org.homio.addon.camera.onvif.util.Helper;
import org.homio.addon.camera.service.IpCameraService;
import org.homio.api.Context;
import org.homio.api.state.DecimalType;
import org.homio.api.state.OnOffType;
import org.jetbrains.annotations.Nullable;

/**
 * responsible for handling commands, which are sent to one of the channels.
 */
@NoArgsConstructor
@CameraBrandHandler("Foscam")
public class FoscamBrandHandler extends BaseOnvifCameraBrandHandler {

    private static final String CG = "/cgi-bin/CGIProxy.fcgi?cmd=";
    private int audioThreshold;

    public FoscamBrandHandler(IpCameraService service) {
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
            log.debug("[{}]: HTTP Result back from camera is \t:{}:", entityID, content);
            ////////////// Motion Alarm //////////////
            if (content.contains("<motionDetectAlarm>")) {
                if (content.contains("<motionDetectAlarm>0</motionDetectAlarm>")) {
                    getEndpointRequire(ENDPOINT_ENABLE_MOTION_ALARM).setValue(OnOffType.OFF);
                } else if (content.contains("<motionDetectAlarm>1</motionDetectAlarm>")) { // Enabled but no alarm
                    getEndpointRequire(ENDPOINT_ENABLE_MOTION_ALARM).setValue(OnOffType.ON);
                } else if (content.contains("<motionDetectAlarm>2</motionDetectAlarm>")) {// Enabled, alarm on
                    getEndpointRequire(ENDPOINT_ENABLE_MOTION_ALARM).setValue(OnOffType.ON);
                    service.alarmDetected(true, MotionAlarm);
                }
            }

            ////////////// Sound Alarm //////////////
            if (content.contains("<soundAlarm>0</soundAlarm>")) {
                getEndpointRequire(ENDPOINT_ENABLE_AUDIO_ALARM).setValue(OnOffType.OFF);
            }
            if (content.contains("<soundAlarm>1</soundAlarm>")) {
                getEndpointRequire(ENDPOINT_ENABLE_AUDIO_ALARM).setValue(OnOffType.ON);
                service.alarmDetected(false, AudioAlarm);
            }
            if (content.contains("<soundAlarm>2</soundAlarm>")) {
                getEndpointRequire(ENDPOINT_ENABLE_AUDIO_ALARM).setValue(OnOffType.ON);
                service.alarmDetected(true, AudioAlarm);
            }

            ////////////// Sound Threshold //////////////
            if (content.contains("<sensitivity>0</sensitivity>")) {
                audioThreshold = 0;
                getEndpointRequire(ENDPOINT_AUDIO_THRESHOLD).setValue(DecimalType.ZERO);
            }
            if (content.contains("<sensitivity>1</sensitivity>")) {
                audioThreshold = 50;
                getEndpointRequire(ENDPOINT_AUDIO_THRESHOLD).setValue(new DecimalType(50));
            }
            if (content.contains("<sensitivity>2</sensitivity>")) {
                audioThreshold = 100;
                getEndpointRequire(ENDPOINT_AUDIO_THRESHOLD).setValue(DecimalType.HUNDRED);
            }

            //////////////// Infrared LED /////////////////////
            if (content.contains("<infraLedState>0</infraLedState>")) {
                getEndpointRequire(ENDPOINT_ENABLE_LED).setValue(OnOffType.OFF);
            }
            if (content.contains("<infraLedState>1</infraLedState>")) {
                getEndpointRequire(ENDPOINT_ENABLE_LED).setValue(OnOffType.ON);
            }

            if (content.contains("</CGI_Result>")) {
                ctx.close();
            }
        } finally {
            ReferenceCountUtil.release(msg);
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
            String user = getEntity().getUser();
            String password = getEntity().getPassword().asString();
            if (audioThreshold == 0) {
                service.sendHttpGET(CG + "setAudioAlarmConfig&isEnable=0&usr="
                        + user + "&pwd=" + password);
            } else if (audioThreshold <= 33) {
                service.sendHttpGET(CG + "setAudioAlarmConfig&isEnable=1&sensitivity=0&usr="
                        + user + "&pwd=" + password);
            } else if (audioThreshold <= 66) {
                service.sendHttpGET(CG + "setAudioAlarmConfig&isEnable=1&sensitivity=1&usr="
                        + user + "&pwd=" + password);
            } else {
                service.sendHttpGET(CG + "setAudioAlarmConfig&isEnable=1&sensitivity=2&usr="
                        + user + "&pwd=" + password);
            }
        }
    }

    @Override
    public boolean isHasMotionAlarm() {
        return true;
    }

    @Override
    public void setMotionAlarmThreshold(int threshold) {
        String user = getEntity().getUser();
        String password = getEntity().getPassword().asString();
        if (threshold > 0) {
            if (getEntity().getCustomAudioAlarmUrl().isEmpty()) {
                service.sendHttpGET(CG + "setAudioAlarmConfig&isEnable=1&usr="
                        + user + "&pwd=" + password);
            } else {
                service.sendHttpGET(getEntity().getCustomAudioAlarmUrl());
            }
        } else {
            service.sendHttpGET(CG + "setAudioAlarmConfig&isEnable=0&usr="
                    + user + "&pwd=" + password);
        }
    }

    @Override
    public void postInitializeCamera(Context context) {
        IpCameraEntity entity = getEntity();
        // Foscam needs any special char like spaces (%20) to be encoded for URLs.
        entity.setUser(Helper.encodeSpecialChars(entity.getUser()));
        entity.setPassword(Helper.encodeSpecialChars(entity.getPassword().asString()));
    }

    @Override
    public @Nullable String getMjpegUri() {
        return "/cgi-bin/CGIStream.cgi?cmd=GetMJStream&usr=" + getEntity().getUser() + "&pwd="
                + getEntity().getPassword().asString();
    }

    @Override
    public void onCameraConnected() {
        addEndpoints();
        String user = getEntity().getUser();
        String password = getEntity().getPassword().asString();
        service.sendHttpGET(CG + "getDevState&usr=" + user + "&pwd=" + password);
        service.sendHttpGET(CG + "getAudioAlarmConfig&usr=" + user + "&pwd=" + password);
    }

    private void addEndpoints() {
        service.addEndpointSwitch(ENDPOINT_AUTO_LED, state -> {
            /*if (on) {
                set Attribute(ENDPOINT_ENABLE_LED, OnOffType.OFF*//*UnDefType.UNDEF*//*);
                service.sendHttpGET(CG + "setInfraLedConfig&mode=0&usr=" + username + "&pwd=" + password);
            } else {
                service.sendHttpGET(CG + "setInfraLedConfig&mode=1&usr=" + username + "&pwd=" + password);
            }*/

            // Disable the auto mode first
            /*service.sendHttpGET(CG + "setInfraLedConfig&mode=1&usr=" + username + "&pwd=" + password);
            se tAttribute(ENDPOINT_AUTO_LED, OnOffType.OFF);
            */
            String user = getEntity().getUser();
            String password = getEntity().getPassword().asString();
            if (state.boolValue()) {
                service.sendHttpGET(CG + "openInfraLed&usr=" + user + "&pwd=" + password);
            } else {
                service.sendHttpGET(CG + "closeInfraLed&usr=" + user + "&pwd=" + password);
            }
        });

        service.addEndpointSwitch(ENDPOINT_ENABLE_MOTION_ALARM, state -> {
            String user = getEntity().getUser();
            String password = getEntity().getPassword().asString();
            String prefix = CG + "setMotionDetectConfig&isEnable=%s&usr=" + user + "&pwd=" + password;
            String prefix1 = CG + "setMotionDetectConfig1&isEnable=%s&usr=" + user + "&pwd=" + password;
            if (state.boolValue()) {
                if (getEntity().getCustomMotionAlarmUrl().isEmpty()) {
                    service.sendHttpGET(prefix.formatted("1"));
                    service.sendHttpGET(prefix1.formatted("1"));
                } else {
                    service.sendHttpGET(getEntity().getCustomMotionAlarmUrl());
                }
            } else {
                service.sendHttpGET(prefix.formatted("0"));
                service.sendHttpGET(prefix1.formatted("0"));
            }
        });
    }

    @Override
    public @Nullable String getSnapshotUri() {
        return "/cgi-bin/CGIProxy.fcgi?usr=" + getEntity().getUser() + "&pwd="
                + getEntity().getPassword().asString() + "&cmd=snapPicture2";
    }
}
