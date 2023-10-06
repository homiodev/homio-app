package org.homio.addon.camera.onvif.impl;

import static org.homio.addon.camera.CameraConstants.AlarmEvents.MotionAlarm;
import static org.homio.addon.camera.CameraConstants.AlarmEvents.PirAlarm;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_AUDIO_THRESHOLD;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_AUTO_LED;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_ENABLE_AUDIO_ALARM;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_ENABLE_EXTERNAL_ALARM;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_ENABLE_MOTION_ALARM;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_ENABLE_PIR_ALARM;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_TEXT_OVERLAY;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.util.ReferenceCountUtil;
import lombok.NoArgsConstructor;
import org.homio.addon.camera.onvif.brand.BaseOnvifCameraBrandHandler;
import org.homio.addon.camera.onvif.util.Helper;
import org.homio.addon.camera.service.OnvifCameraService;
import org.homio.api.EntityContext;
import org.homio.api.state.DecimalType;
import org.homio.api.state.OnOffType;
import org.homio.api.state.StringType;
import org.jetbrains.annotations.Nullable;

/**
 * responsible for handling commands, which are sent to one of the channels.
 */
@NoArgsConstructor
@CameraBrandHandler("Instar")
public class InstarBrandHandler extends BaseOnvifCameraBrandHandler {

    private String requestUrl = "Empty";
    private int audioThreshold;
    private boolean newApi;

    public InstarBrandHandler(OnvifCameraService service) {
        super(service);
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
            log.debug("[{}]: HTTP Result back from camera is \t:{}:", entityID, content);
            switch (requestUrl) {
                case "/param.cgi?cmd=getinfrared" -> {
                    if (content.contains("var infraredstat=\"auto")) {
                        setAttribute(ENDPOINT_AUTO_LED, OnOffType.ON);
                    } else {
                        setAttribute(ENDPOINT_AUTO_LED, OnOffType.OFF);
                    }
                }
                case "/param.cgi?cmd=getoverlayattr&-region=1" -> {// Text Overlays
                    if (content.contains("var show_1=\"0\"")) {
                        setAttribute(ENDPOINT_TEXT_OVERLAY, StringType.EMPTY);
                    } else {
                        value1 = Helper.searchString(content, "var name_1=\"");
                        if (!value1.isEmpty()) {
                            setAttribute(ENDPOINT_TEXT_OVERLAY, new StringType(value1));
                        }
                    }
                }
                case "/cgi-bin/hi3510/param.cgi?cmd=getmdattr" -> {// Motion Alarm
                    // Motion Alarm
                    if (content.contains("var m1_enable=\"1\"")) {
                        setAttribute(ENDPOINT_ENABLE_MOTION_ALARM, OnOffType.ON);
                    } else {
                        setAttribute(ENDPOINT_ENABLE_MOTION_ALARM, OnOffType.OFF);
                    }
                }
                case "/cgi-bin/hi3510/param.cgi?cmd=getaudioalarmattr" -> {// Audio Alarm
                    if (content.contains("var aa_enable=\"1\"")) {
                        setAttribute(ENDPOINT_ENABLE_AUDIO_ALARM, OnOffType.ON);
                        value1 = Helper.searchString(content, "var aa_value=\"");
                        if (!value1.isEmpty()) {
                            setAttribute(ENDPOINT_AUDIO_THRESHOLD, new DecimalType(value1));
                        }
                    } else {
                        setAttribute(ENDPOINT_ENABLE_AUDIO_ALARM, OnOffType.OFF);
                    }
                }
                case "param.cgi?cmd=getpirattr" -> {// PIR Alarm
                    if (content.contains("var pir_enable=\"1\"")) {
                        setAttribute(ENDPOINT_ENABLE_PIR_ALARM, OnOffType.ON);
                    } else {
                        setAttribute(ENDPOINT_ENABLE_PIR_ALARM, OnOffType.OFF);
                    }
                    // Reset the Alarm, need to find better place to put this.
                    service.motionDetected(false, PirAlarm);
                }
                case "/param.cgi?cmd=getioattr" -> {// External Alarm Input
                    if (content.contains("var io_enable=\"1\"")) {
                        setAttribute(ENDPOINT_ENABLE_EXTERNAL_ALARM, OnOffType.ON);
                    } else {
                        setAttribute(ENDPOINT_ENABLE_EXTERNAL_ALARM, OnOffType.OFF);
                    }
                }
                default -> {
                    if (requestUrl.startsWith("/param.cgi?cmd=setasaction&-server=1&enable=1")
                        && content.contains("response=\"200\";")) {// new
                        newApi = true;
                        log.debug("Alarm server successfully setup for a 2k+ Instar camera");
                        service.urls.setRtspUri("rtsp://%s/livestream/12".formatted(ip));
                        service.urls.setMjpegUri("/livestream/12?action=play&media=mjpeg");
                        service.urls.setSnapshotUri("/snap.cgi?chn=12");
                    } else if (requestUrl.startsWith("/param.cgi?cmd=setmdalarm&-aname=server2&-switch=on&-interval=1")
                        && content.startsWith("[Succeed]set ok")) {
                        newApi = false;
                        log.debug("Alarm server successfully setup for a 1080p Instar camera");
                    } else {
                        log.debug("Unknown reply from URI:{}", requestUrl);
                    }
                }
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
            if (this.audioThreshold > 0) {
                service.sendHttpGET("/cgi-bin/hi3510/param.cgi?cmd=setaudioalarmattr&-aa_enable=1");
                service.sendHttpGET("/cgi-bin/hi3510/param.cgi?cmd=setaudioalarmattr&-aa_enable=1&-aa_value=" + audioThreshold);
            } else {
                service.sendHttpGET("/cgi-bin/hi3510/param.cgi?cmd=setaudioalarmattr&-aa_enable=0");
            }
        }
    }

    public void alarmTriggered(String alarm) {
        log.debug("[{}]: Alarm has been triggered:{}", entityID, alarm);
        switch (alarm) {
            case "/instar?&active=1", "/instar?&active=2", "/instar?&active=3", "/instar?&active=4" ->
                service.motionDetected(true, MotionAlarm);
            case "/instar?&active=5" ->// PIR
                service.motionDetected(true, PirAlarm);
            case "/instar?&active=6" ->// Audio Alarm
                service.audioDetected(true);
            case "/instar?&active=7", "/instar?&active=8", "/instar?&active=9", "/instar?&active=10" ->// Motion Area 4
                service.motionDetected(true, MotionAlarm);
        }
    }

    @Override
    public void pollCameraRunnable() {
        service.motionDetected(false, MotionAlarm);
        service.motionDetected(false, PirAlarm);
        service.audioDetected(false);
    }

    @Override
    public void onCameraConnected() {
        addEndpoints();
        service.sendHttpGET("/cgi-bin/hi3510/param.cgi?cmd=getaudioalarmattr");
        service.sendHttpGET("/param.cgi?cmd=getioattr");
        service.sendHttpGET(newApi ? "/param.cgi?cmd=getalarmattr" : "/cgi-bin/hi3510/param.cgi?cmd=getmdattr");
        service.sendHttpGET("/param.cgi?cmd=getpirattr");
        service.sendHttpGET("/param.cgi?cmd=getinfrared");
        service.sendHttpGET("/param.cgi?cmd=getoverlayattr&-region=1");
    }

    private void addEndpoints() {
        service.addEndpointSwitch(ENDPOINT_AUTO_LED, state ->
            service.sendHttpGET("/param.cgi?cmd=setinfrared&-infraredstat=" + state.boolValue("auto", "close")));

        service.addEndpointSwitch(ENDPOINT_ENABLE_MOTION_ALARM, state -> {
            int val = state.intValue();
            service.sendHttpGET("/cgi-bin/hi3510/param.cgi?cmd=setmdattr&-enable=" + val +
                "&-name=1&cmd=setmdattr&-enable=" + val + "&-name=2&cmd=setmdattr&-enable=" + val + "&-name=3&cmd=setmdattr&-enable=" + val +
                "&-name=4");
        });

        service.addEndpointInput(ENDPOINT_TEXT_OVERLAY, state -> {
            String text = Helper.encodeSpecialChars(state.stringValue());
            if (text.isEmpty()) {
                service.sendHttpGET("/param.cgi?cmd=setoverlayattr&-region=1&-show=0");
            } else {
                service.sendHttpGET("/param.cgi?cmd=setoverlayattr&-region=1&-show=1&-name=" + text);
            }
        });

        service.addEndpointSwitch(ENDPOINT_ENABLE_PIR_ALARM, state ->
            service.sendHttpGET("/param.cgi?cmd=setpirattr&-pir_enable=" + state.intValue()));

        service.addEndpointSwitch(ENDPOINT_ENABLE_EXTERNAL_ALARM, state ->
            service.sendHttpGET("/param.cgi?cmd=setioattr&-io_enable=" + state.intValue()));
    }

    @Override
    public void postInitializeCamera(EntityContext entityContext) {
        if (service.lowPriorityRequests.isEmpty()) {
            service.addLowRequestGet("/param.cgi?cmd=getaudioalarmattr");
            service.addLowRequestGet("/cgi-bin/hi3510/param.cgi?cmd=getmdattr");
            service.addLowRequestGet("/param.cgi?cmd=getalarmattr");
            service.addLowRequestGet("/param.cgi?cmd=getinfrared");
            service.addLowRequestGet("/param.cgi?cmd=getoverlayattr&-region=1");
            service.addLowRequestGet("/param.cgi?cmd=getpirattr");
            service.addLowRequestGet("/param.cgi?cmd=getioattr"); // ext alarm input on/off
        }
    }

    @Override
    public @Nullable String getMjpegUri() {
        return "/tmpfs/snap.jpg";
    }

    @Override
    public @Nullable String getSnapshotUri() {
        return "/mjpegstream.cgi?-chn=12";
    }

    @Override
    public void handleSetURL(ChannelPipeline pipeline, String httpRequestURL) {
        requestUrl = httpRequestURL;
    }
}
