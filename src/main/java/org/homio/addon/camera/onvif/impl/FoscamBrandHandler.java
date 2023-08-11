package org.homio.addon.camera.onvif.impl;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ReferenceCountUtil;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.homio.addon.camera.entity.OnvifCameraEntity;
import org.homio.addon.camera.onvif.brand.BaseOnvifCameraBrandHandler;
import org.homio.addon.camera.onvif.brand.BrandCameraHasAudioAlarm;
import org.homio.addon.camera.onvif.brand.BrandCameraHasMotionAlarm;
import org.homio.addon.camera.onvif.util.Helper;
import org.homio.addon.camera.onvif.util.IpCameraBindingConstants;
import org.homio.addon.camera.service.OnvifCameraService;
import org.homio.addon.camera.ui.UIVideoAction;
import org.homio.addon.camera.ui.UIVideoActionGetter;
import org.homio.api.EntityContext;
import org.homio.api.state.DecimalType;
import org.homio.api.state.OnOffType;
import org.homio.api.state.State;
import org.jetbrains.annotations.Nullable;

/**
 * responsible for handling commands, which are sent to one of the channels.
 */
@Log4j2
@CameraBrandHandler("Foscam")
public class FoscamBrandHandler extends BaseOnvifCameraBrandHandler implements BrandCameraHasAudioAlarm, BrandCameraHasMotionAlarm {

    private static final String CG = "/cgi-bin/CGIProxy.fcgi?cmd=";
    private int audioThreshold;

    public FoscamBrandHandler(OnvifCameraService service) {
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
                    setAttribute(IpCameraBindingConstants.CHANNEL_ENABLE_MOTION_ALARM, OnOffType.OFF);
                } else if (content.contains("<motionDetectAlarm>1</motionDetectAlarm>")) { // Enabled but no alarm
                    setAttribute(IpCameraBindingConstants.CHANNEL_ENABLE_MOTION_ALARM, OnOffType.ON);
                    service.motionDetected(false, IpCameraBindingConstants.CHANNEL_MOTION_ALARM);
                } else if (content.contains("<motionDetectAlarm>2</motionDetectAlarm>")) {// Enabled, alarm on
                    setAttribute(IpCameraBindingConstants.CHANNEL_ENABLE_MOTION_ALARM, OnOffType.ON);
                    service.motionDetected(true, IpCameraBindingConstants.CHANNEL_MOTION_ALARM);
                }
            }

            ////////////// Sound Alarm //////////////
            if (content.contains("<soundAlarm>0</soundAlarm>")) {
                setAttribute(IpCameraBindingConstants.CHANNEL_ENABLE_AUDIO_ALARM, OnOffType.OFF);
                setAttribute(IpCameraBindingConstants.CHANNEL_AUDIO_ALARM, OnOffType.OFF);
            }
            if (content.contains("<soundAlarm>1</soundAlarm>")) {
                setAttribute(IpCameraBindingConstants.CHANNEL_ENABLE_AUDIO_ALARM, OnOffType.ON);
                service.audioDetected(false);
            }
            if (content.contains("<soundAlarm>2</soundAlarm>")) {
                setAttribute(IpCameraBindingConstants.CHANNEL_ENABLE_AUDIO_ALARM, OnOffType.ON);
                service.audioDetected(true);
            }

            ////////////// Sound Threshold //////////////
            if (content.contains("<sensitivity>0</sensitivity>")) {
                setAttribute(IpCameraBindingConstants.CHANNEL_AUDIO_THRESHOLD, DecimalType.ZERO);
            }
            if (content.contains("<sensitivity>1</sensitivity>")) {
                setAttribute(IpCameraBindingConstants.CHANNEL_AUDIO_THRESHOLD, new DecimalType(50));
            }
            if (content.contains("<sensitivity>2</sensitivity>")) {
                setAttribute(IpCameraBindingConstants.CHANNEL_AUDIO_THRESHOLD, DecimalType.HUNDRED);
            }

            //////////////// Infrared LED /////////////////////
            if (content.contains("<infraLedState>0</infraLedState>")) {
                setAttribute(IpCameraBindingConstants.CHANNEL_ENABLE_LED, OnOffType.OFF);
            }
            if (content.contains("<infraLedState>1</infraLedState>")) {
                setAttribute(IpCameraBindingConstants.CHANNEL_ENABLE_LED, OnOffType.ON);
            }

            if (content.contains("</CGI_Result>")) {
                ctx.close();
            }
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    @UIVideoAction(name = IpCameraBindingConstants.CHANNEL_ENABLE_LED, order = 50, icon = "far fa-lightbulb")
    public void enableLED(boolean on) {
        getIRLedHandler().accept(on);
    }

    @Override
    public Consumer<Boolean> getIRLedHandler() {
        return on -> {
            // Disable the auto mode first
            service.sendHttpGET(CG + "setInfraLedConfig&mode=1&usr=" + username + "&pwd=" + password);
            setAttribute(IpCameraBindingConstants.CHANNEL_AUTO_LED, OnOffType.OFF);
            if (on) {
                service.sendHttpGET(CG + "openInfraLed&usr=" + username + "&pwd=" + password);
            } else {
                service.sendHttpGET(CG + "closeInfraLed&usr=" + username + "&pwd=" + password);
            }
        };
    }

    @Override
    public Supplier<Boolean> getIrLedValueHandler() {
        return () -> Optional.ofNullable(getAttribute(IpCameraBindingConstants.CHANNEL_ENABLE_LED)).map(State::boolValue).orElse(false);
    }

    @UIVideoAction(name = IpCameraBindingConstants.CHANNEL_AUTO_LED, order = 60, icon = "fas fa-lightbulb")
    public void autoLED(boolean on) {
        if (on) {
            setAttribute(IpCameraBindingConstants.CHANNEL_ENABLE_LED, null/*UnDefType.UNDEF*/);
            service.sendHttpGET(CG + "setInfraLedConfig&mode=0&usr=" + username + "&pwd=" + password);
        } else {
            service.sendHttpGET(CG + "setInfraLedConfig&mode=1&usr=" + username + "&pwd=" + password);
        }
    }

    @Override
    public void setAudioAlarmThreshold(int audioThreshold) {
        if (audioThreshold != this.audioThreshold) {
            this.audioThreshold = audioThreshold;
            if (audioThreshold == 0) {
                service.sendHttpGET(CG + "setAudioAlarmConfig&isEnable=0&usr="
                    + username + "&pwd=" + password);
            } else if (audioThreshold <= 33) {
                service.sendHttpGET(CG + "setAudioAlarmConfig&isEnable=1&sensitivity=0&usr="
                    + username + "&pwd=" + password);
            } else if (audioThreshold <= 66) {
                service.sendHttpGET(CG + "setAudioAlarmConfig&isEnable=1&sensitivity=1&usr="
                    + username + "&pwd=" + password);
            } else {
                service.sendHttpGET(CG + "setAudioAlarmConfig&isEnable=1&sensitivity=2&usr="
                    + username + "&pwd=" + password);
            }
        }
    }

    @Override
    public void setMotionAlarmThreshold(int threshold) {
        if (threshold > 0) {
            if (getEntity().getCustomAudioAlarmUrl().isEmpty()) {
                service.sendHttpGET(CG + "setAudioAlarmConfig&isEnable=1&usr="
                    + username + "&pwd=" + password);
            } else {
                service.sendHttpGET(getEntity().getCustomAudioAlarmUrl());
            }
        } else {
            service.sendHttpGET(CG + "setAudioAlarmConfig&isEnable=0&usr="
                + username + "&pwd=" + password);
        }
    }

    @UIVideoActionGetter(IpCameraBindingConstants.CHANNEL_ENABLE_MOTION_ALARM)
    public State getEnableMotionAlarm() {
        return getAttribute(IpCameraBindingConstants.CHANNEL_ENABLE_MOTION_ALARM);
    }

    @UIVideoAction(name = IpCameraBindingConstants.CHANNEL_ENABLE_MOTION_ALARM, order = 14, icon = "fas fa-running")
    public void setEnableMotionAlarm(boolean on) {
        if (on) {
            if (getEntity().getCustomMotionAlarmUrl().isEmpty()) {
                service.sendHttpGET(CG + "setMotionDetectConfig&isEnable=1&usr="
                    + username + "&pwd=" + password);
                service.sendHttpGET(CG + "setMotionDetectConfig1&isEnable=1&usr="
                    + username + "&pwd=" + password);
            } else {
                service.sendHttpGET(getEntity().getCustomMotionAlarmUrl());
            }
        } else {
            service.sendHttpGET(CG + "setMotionDetectConfig&isEnable=0&usr="
                + username + "&pwd=" + password);
            service.sendHttpGET(CG + "setMotionDetectConfig1&isEnable=0&usr="
                + username + "&pwd=" + password);
        }
    }

    @Override
    public void runOncePerMinute(EntityContext entityContext) {
        service.sendHttpGET(CG + "getDevState&usr=" + username + "&pwd=" + password);
        service.sendHttpGET(CG + "getAudioAlarmConfig&usr=" + username + "&pwd=" + password);
    }

    @Override
    public void initialize(EntityContext entityContext) {
        OnvifCameraEntity entity = getEntity();
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
    public @Nullable String getSnapshotUri() {
        return "/cgi-bin/CGIProxy.fcgi?usr=" + getEntity().getUser() + "&pwd="
            + getEntity().getPassword().asString() + "&cmd=snapPicture2";
    }
}
