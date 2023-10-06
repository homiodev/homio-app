package org.homio.addon.camera.onvif.impl;

import static org.homio.addon.camera.CameraConstants.AlarmEvents.MotionAlarm;
import static org.homio.addon.camera.CameraConstants.CM;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_ACTIVATE_ALARM_OUTPUT;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_ACTIVATE_ALARM_OUTPUT2;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_AUDIO_THRESHOLD;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_AUTO_LED;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_ENABLE_AUDIO_ALARM;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_ENABLE_LINE_CROSSING_ALARM;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_ENABLE_MOTION_ALARM;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_ENABLE_PRIVACY_MODE;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_TEXT_OVERLAY;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.util.ReferenceCountUtil;
import lombok.NoArgsConstructor;
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
@CameraBrandHandler("Amcrest")
public class AmcrestBrandHandler extends BaseOnvifCameraBrandHandler {

    private String requestUrl = "Empty";
    private int audioThreshold;

    public AmcrestBrandHandler(OnvifCameraService service) {
        super(service);
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
                getEndpointRequire(ENDPOINT_ENABLE_MOTION_ALARM).setValue(OnOffType.OFF);
            } else if (content.contains("table.MotionDetect[0].Enable=true")) {
                getEndpointRequire(ENDPOINT_ENABLE_MOTION_ALARM).setValue(OnOffType.ON);
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
            // Privacy Mode on/off
            if (content.contains("Code=LensMaskOpen;") || content.contains("table.LeLensMask[0].Enable=true")) {
                getEndpointRequire(ENDPOINT_ENABLE_PRIVACY_MODE).setValue(OnOffType.ON);
            } else if (content.contains("Code=LensMaskClose;") || content.contains("table.LeLensMask[0].Enable=false")) {
                getEndpointRequire(ENDPOINT_ENABLE_PRIVACY_MODE).setValue(OnOffType.OFF);
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
        addEndpoints();
        service.sendHttpGET("/cgi-bin/configManager.cgi?action=getConfig&name=AudioDetect[0]");
        service.sendHttpGET("/cgi-bin/configManager.cgi?action=getConfig&name=LeLensMask[0]");
        service.sendHttpGET("/cgi-bin/configManager.cgi?action=getConfig&name=MotionDetect[0]");
        service.sendHttpGET("/cgi-bin/configManager.cgi?action=getConfig&name=CrossLineDetection[0]");
    }

    private void addEndpoints() {
        service.addEndpointInput(ENDPOINT_TEXT_OVERLAY, state -> {
            String text = Helper.encodeSpecialChars(state.stringValue());
            if (text.isEmpty()) {
                service.sendHttpGET(CM + "setConfig&VideoWidget[0].CustomTitle[1].EncodeBlend=false");
            } else {
                service.sendHttpGET(CM + "setConfig&VideoWidget[0].CustomTitle[1].EncodeBlend=true&VideoWidget[0].CustomTitle[1].Text="
                    + text);
            }
        });

        service.addEndpointSwitch(ENDPOINT_AUTO_LED, state ->
            service.sendHttpGET(CM + "setConfig&Lighting[0][0].Mode=" + (state.boolValue() ? "Auto" : "Off")));

        service.addEndpointSwitch(ENDPOINT_ENABLE_LINE_CROSSING_ALARM, state ->
            service.sendHttpGET(CM + "setConfig&VideoAnalyseRule[0][1].Enable=" + state.boolValue()));

        service.addEndpointSwitch(ENDPOINT_ENABLE_MOTION_ALARM, state -> {
            if (state.boolValue()) {
                service.sendHttpGET(CM + "setConfig&MotionDetect[0].Enable=true&MotionDetect[0].EventHandler.Dejitter=1");
            } else {
                service.sendHttpGET(CM + "setConfig&MotionDetect[0].Enable=false");
            }
        });

        service.addEndpointSwitch(ENDPOINT_ACTIVATE_ALARM_OUTPUT, state ->
            service.sendHttpGET(CM + "setConfig&AlarmOut[0].Mode=" + state.intValue()));

        service.addEndpointSwitch(ENDPOINT_ACTIVATE_ALARM_OUTPUT2, state ->
            service.sendHttpGET(CM + "setConfig&AlarmOut[1].Mode=" + state.intValue()));

        service.addEndpointSwitch(ENDPOINT_ENABLE_PRIVACY_MODE, state ->
            service.sendHttpGET(CM + "setConfig&LeLensMask[0].Enable=" + state.boolValue()));
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
