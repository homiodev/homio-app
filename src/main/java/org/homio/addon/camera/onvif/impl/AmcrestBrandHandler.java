package org.homio.addon.camera.onvif.impl;

import static org.homio.addon.camera.VideoConstants.ENDPOINT_ACTIVATE_ALARM_OUTPUT;
import static org.homio.addon.camera.VideoConstants.ENDPOINT_ACTIVATE_ALARM_OUTPUT2;
import static org.homio.addon.camera.VideoConstants.ENDPOINT_ENABLE_PRIVACY_MODE;
import static org.homio.addon.camera.VideoConstants.ENDPOINT_TEXT_OVERLAY;
import static org.homio.addon.camera.VideoConstants.CM;
import static org.homio.addon.camera.VideoConstants.ENDPOINT_AUDIO_THRESHOLD;
import static org.homio.addon.camera.VideoConstants.ENDPOINT_AUTO_LED;
import static org.homio.addon.camera.VideoConstants.ENDPOINT_ENABLE_AUDIO_ALARM;
import static org.homio.addon.camera.VideoConstants.ENDPOINT_ENABLE_LED;
import static org.homio.addon.camera.VideoConstants.ENDPOINT_ENABLE_LINE_CROSSING_ALARM;
import static org.homio.addon.camera.VideoConstants.ENDPOINT_ENABLE_MOTION_ALARM;
import static org.homio.addon.camera.VideoConstants.AlarmEvents.MotionAlarm;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.util.ReferenceCountUtil;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.NoArgsConstructor;
import org.homio.addon.camera.onvif.brand.BaseOnvifCameraBrandHandler;
import org.homio.addon.camera.onvif.brand.BrandCameraHasAudioAlarm;
import org.homio.addon.camera.onvif.brand.BrandCameraHasMotionAlarm;
import org.homio.addon.camera.onvif.util.Helper;
import org.homio.addon.camera.service.OnvifCameraService;
import org.homio.addon.camera.ui.UIVideoAction;
import org.homio.addon.camera.ui.UIVideoActionGetter;
import org.homio.api.state.DecimalType;
import org.homio.api.state.OnOffType;
import org.homio.api.state.State;
import org.jetbrains.annotations.Nullable;

/**
 * responsible for handling commands, which are sent to one of the channels.
 */
@NoArgsConstructor
@CameraBrandHandler("Amcrest")
public class AmcrestBrandHandler extends BaseOnvifCameraBrandHandler implements BrandCameraHasAudioAlarm,
        BrandCameraHasMotionAlarm {

    private String requestUrl = "Empty";
    private int audioThreshold;

    public AmcrestBrandHandler(OnvifCameraService service) {
        super(service);
    }

    @UIVideoAction(name = ENDPOINT_TEXT_OVERLAY, order = 100, icon = "fas fa-paragraph")
    public void textOverlay(String value) {
        String text = Helper.encodeSpecialChars(value);
        if (text.isEmpty()) {
            service.sendHttpGET(CM + "setConfig&VideoWidget[0].CustomTitle[1].EncodeBlend=false");
        } else {
            service.sendHttpGET(CM + "setConfig&VideoWidget[0].CustomTitle[1].EncodeBlend=true&VideoWidget[0].CustomTitle[1].Text="
                    + text);
        }
    }

    @UIVideoAction(name = ENDPOINT_ENABLE_LED, order = 50, icon = "far fa-lightbulb")
    public void enableLed(boolean on) {
        setAttribute(ENDPOINT_AUTO_LED, OnOffType.OFF);
        if (on) {
            service.sendHttpGET(CM + "setConfig&Lighting[0][0].Mode=Manual");
        } else {
            service.sendHttpGET(CM + "setConfig&Lighting[0][0].Mode=Off");
        }
    }

    @UIVideoAction(name = ENDPOINT_AUTO_LED, order = 60, icon = "fas fa-lightbulb")
    public void autoLed(boolean on) {
        if (on) {
            setAttribute(ENDPOINT_ENABLE_LED, OnOffType.ON);
            service.sendHttpGET(CM + "setConfig&Lighting[0][0].Mode=Auto");
        }
    }

    @Override
    public Consumer<Boolean> getIRLedHandler() {
        return on -> {
            setAttribute(ENDPOINT_AUTO_LED, OnOffType.OFF);
            if (on) {
                service.sendHttpGET(CM + "setConfig&Lighting[0][0].Mode=Manual");
            } else {
                service.sendHttpGET(CM + "setConfig&Lighting[0][0].Mode=Off");
            }
        };
    }

    @Override
    public Supplier<Boolean> getIrLedValueHandler() {
        return () -> Optional.ofNullable(getAttribute(ENDPOINT_ENABLE_LED)).map(State::boolValue).orElse(false);
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
    public void setMotionAlarmThreshold(int threshold) {
        if (threshold > 0) {
            service.sendHttpGET(CM + "setConfig&AudioDetect[0].MutationDetect=true&AudioDetect[0].EventHandler.Dejitter=1");
        } else {
            service.sendHttpGET(CM + "setConfig&AudioDetect[0].MutationDetect=false");
        }
    }

    @UIVideoActionGetter(ENDPOINT_ENABLE_LINE_CROSSING_ALARM)
    public State getEnableLineCrossingAlarm() {
        return getAttribute(ENDPOINT_ENABLE_LINE_CROSSING_ALARM);
    }

    @UIVideoAction(name = ENDPOINT_ENABLE_LINE_CROSSING_ALARM, order = 150, icon = "fas fa-grip-lines-vertical")
    public void setEnableLineCrossingAlarm(boolean on) {
        if (on) {
            service.sendHttpGET(CM + "setConfig&VideoAnalyseRule[0][1].Enable=true");
        } else {
            service.sendHttpGET(CM + "setConfig&VideoAnalyseRule[0][1].Enable=false");
        }
    }

    @UIVideoActionGetter(ENDPOINT_ENABLE_MOTION_ALARM)
    public State getEnableMotionAlarm() {
        return getAttribute(ENDPOINT_ENABLE_MOTION_ALARM);
    }

    @UIVideoAction(name = ENDPOINT_ENABLE_MOTION_ALARM, order = 14, icon = "fas fa-running")
    public void setEnableMotionAlarm(boolean on) {
        if (on) {
            service.sendHttpGET(CM + "setConfig&MotionDetect[0].Enable=true&MotionDetect[0].EventHandler.Dejitter=1");
        } else {
            service.sendHttpGET(CM + "setConfig&MotionDetect[0].Enable=false");
        }
    }

    @UIVideoAction(name = ENDPOINT_ACTIVATE_ALARM_OUTPUT, order = 45, icon = "fas fa-bell")
    public void activateAlarmOutput(boolean on) {
        if (on) {
            service.sendHttpGET(CM + "setConfig&AlarmOut[0].Mode=1");
        } else {
            service.sendHttpGET(CM + "setConfig&AlarmOut[0].Mode=0");
        }
    }

    @UIVideoAction(name = ENDPOINT_ACTIVATE_ALARM_OUTPUT2, order = 47, icon = "fas fa-bell")
    public void activateAlarmOutput2(boolean on) {
        if (on) {
            service.sendHttpGET(CM + "setConfig&AlarmOut[1].Mode=1");
        } else {
            service.sendHttpGET(CM + "setConfig&AlarmOut[1].Mode=0");
        }
    }

    @UIVideoActionGetter(ENDPOINT_ENABLE_PRIVACY_MODE)
    public State getEnablePrivacyMode() {
        return getAttribute(ENDPOINT_ENABLE_PRIVACY_MODE);
    }

    @UIVideoAction(name = ENDPOINT_ENABLE_PRIVACY_MODE, order = 70, icon = "fas fa-user-secret")
    public void setEnablePrivacyMode(boolean on) {
        service.sendHttpGET(CM + "setConfig&LeLensMask[0].Enable=" + on);
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
            if (content.contains("Error: No Events")) {
                if ("/cgi-bin/eventManager.cgi?action=getEventIndexes&code=VideoMotion".equals(requestUrl)) {
                    service.motionDetected(false, MotionAlarm);
                } else if ("/cgi-bin/eventManager.cgi?action=getEventIndexes&code=AudioMutation".equals(requestUrl)) {
                    service.audioDetected(false);
                }
            } else if (content.contains("channels[0]=0")) {
                if ("/cgi-bin/eventManager.cgi?action=getEventIndexes&code=VideoMotion".equals(requestUrl)) {
                    service.motionDetected(true, MotionAlarm);
                } else if ("/cgi-bin/eventManager.cgi?action=getEventIndexes&code=AudioMutation".equals(requestUrl)) {
                    service.audioDetected(true);
                }
            }

            if (content.contains("table.MotionDetect[0].Enable=false")) {
                setAttribute(ENDPOINT_ENABLE_MOTION_ALARM, OnOffType.OFF);
            } else if (content.contains("table.MotionDetect[0].Enable=true")) {
                setAttribute(ENDPOINT_ENABLE_MOTION_ALARM, OnOffType.ON);
            }
            // determine if the audio alarm is turned on or off.
            if (content.contains("table.AudioDetect[0].MutationDetect=true")) {
                setAttribute(ENDPOINT_ENABLE_AUDIO_ALARM, OnOffType.ON);
            } else if (content.contains("table.AudioDetect[0].MutationDetect=false")) {
                setAttribute(ENDPOINT_ENABLE_AUDIO_ALARM, OnOffType.OFF);
            }
            // Handle AudioMutationThreshold alarm
            if (content.contains("table.AudioDetect[0].MutationThreold=")) {
                String value = service.returnValueFromString(content, "table.AudioDetect[0].MutationThreold=");
                setAttribute(ENDPOINT_AUDIO_THRESHOLD, new DecimalType(value));
            }
            // Privacy Mode on/off
            if (content.contains("Code=LensMaskOpen;") || content.contains("table.LeLensMask[0].Enable=true")) {
                setAttribute(ENDPOINT_ENABLE_PRIVACY_MODE, OnOffType.ON);
            } else if (content.contains("Code=LensMaskClose;")
                    || content.contains("table.LeLensMask[0].Enable=false")) {
                setAttribute(ENDPOINT_ENABLE_PRIVACY_MODE, OnOffType.OFF);
            }
        } finally {
            ReferenceCountUtil.release(msg);
            ctx.close();
        }
    }

    @Override
    public void pollCameraRunnable() {
        service.sendHttpGET("/cgi-bin/eventManager.cgi?action=getEventIndexes&code=VideoMotion");
        service.sendHttpGET("/cgi-bin/eventManager.cgi?action=getEventIndexes&code=AudioMutation");
    }

    @Override
    public @Nullable String getMjpegUri() {
        return "/cgi-bin/mjpg/video.cgi?channel=" + nvrChannel + "&subtype=1";
    }

    @Override
    public void onCameraConnected() {
        service.sendHttpGET("/cgi-bin/configManager.cgi?action=getConfig&name=AudioDetect[0]");
        service.sendHttpGET("/cgi-bin/configManager.cgi?action=getConfig&name=LeLensMask[0]");
        service.sendHttpGET("/cgi-bin/configManager.cgi?action=getConfig&name=MotionDetect[0]");
        service.sendHttpGET("/cgi-bin/configManager.cgi?action=getConfig&name=CrossLineDetection[0]");
    }

    @Override
    public @Nullable String getSnapshotUri() {
        return "/cgi-bin/snapshot.cgi?channel=" + nvrChannel;
    }

    @Override
    public void handleSetURL(ChannelPipeline pipeline, String httpRequestURL) {
        requestUrl = httpRequestURL;
    }
}
