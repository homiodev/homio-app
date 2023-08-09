package org.homio.addon.camera.onvif.impl;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.util.ReferenceCountUtil;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.homio.addon.camera.onvif.brand.BaseOnvifCameraBrandHandler;
import org.homio.addon.camera.onvif.brand.BrandCameraHasAudioAlarm;
import org.homio.addon.camera.onvif.util.Helper;
import org.homio.addon.camera.onvif.util.IpCameraBindingConstants;
import org.homio.addon.camera.service.OnvifCameraService;
import org.homio.addon.camera.ui.UIVideoAction;
import org.homio.api.EntityContext;
import org.homio.api.state.DecimalType;
import org.homio.api.state.OnOffType;
import org.homio.api.state.State;
import org.homio.api.state.StringType;

/**
 * responsible for handling commands, which are sent to one of the channels.
 */
@Log4j2
@CameraBrandHandler(name = "Instar", handlerName = "instarHandler")
public class InstarBrandHandler extends BaseOnvifCameraBrandHandler implements BrandCameraHasAudioAlarm {

    private String requestUrl = "Empty";
    private int audioThreshold;

    public InstarBrandHandler(OnvifCameraService service) {
        super(service);
    }

    public void setURL(String url) {
        requestUrl = url;
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
                case "/param.cgi?cmd=getinfrared":
                    if (content.contains("var infraredstat=\"auto")) {
                        setAttribute(IpCameraBindingConstants.CHANNEL_AUTO_LED, OnOffType.ON);
                    } else {
                        setAttribute(IpCameraBindingConstants.CHANNEL_AUTO_LED, OnOffType.OFF);
                    }
                    break;
                case "/param.cgi?cmd=getoverlayattr&-region=1":// Text Overlays
                    if (content.contains("var show_1=\"0\"")) {
                        setAttribute(IpCameraBindingConstants.CHANNEL_TEXT_OVERLAY, StringType.EMPTY);
                    } else {
                        value1 = Helper.searchString(content, "var name_1=\"");
                        if (!value1.isEmpty()) {
                            setAttribute(IpCameraBindingConstants.CHANNEL_TEXT_OVERLAY, new StringType(value1));
                        }
                    }
                    break;
                case "/cgi-bin/hi3510/param.cgi?cmd=getmdattr":// Motion Alarm
                    // Motion Alarm
                    if (content.contains("var m1_enable=\"1\"")) {
                        setAttribute(IpCameraBindingConstants.CHANNEL_ENABLE_MOTION_ALARM, OnOffType.ON);
                    } else {
                        setAttribute(IpCameraBindingConstants.CHANNEL_ENABLE_MOTION_ALARM, OnOffType.OFF);
                    }
                    break;
                case "/cgi-bin/hi3510/param.cgi?cmd=getaudioalarmattr":// Audio Alarm
                    if (content.contains("var aa_enable=\"1\"")) {
                        setAttribute(IpCameraBindingConstants.CHANNEL_ENABLE_AUDIO_ALARM, OnOffType.ON);
                        value1 = Helper.searchString(content, "var aa_value=\"");
                        if (!value1.isEmpty()) {
                            setAttribute(IpCameraBindingConstants.CHANNEL_AUDIO_THRESHOLD, new DecimalType(value1));
                        }
                    } else {
                        setAttribute(IpCameraBindingConstants.CHANNEL_ENABLE_AUDIO_ALARM, OnOffType.OFF);
                    }
                    break;
                case "param.cgi?cmd=getpirattr":// PIR Alarm
                    if (content.contains("var pir_enable=\"1\"")) {
                        setAttribute(IpCameraBindingConstants.CHANNEL_ENABLE_PIR_ALARM, OnOffType.ON);
                    } else {
                        setAttribute(IpCameraBindingConstants.CHANNEL_ENABLE_PIR_ALARM, OnOffType.OFF);
                    }
                    // Reset the Alarm, need to find better place to put this.
                    getService().motionDetected(false, IpCameraBindingConstants.CHANNEL_PIR_ALARM);
                    break;
                case "/param.cgi?cmd=getioattr":// External Alarm Input
                    if (content.contains("var io_enable=\"1\"")) {
                        setAttribute(IpCameraBindingConstants.CHANNEL_ENABLE_EXTERNAL_ALARM_INPUT, OnOffType.ON);
                    } else {
                        setAttribute(IpCameraBindingConstants.CHANNEL_ENABLE_EXTERNAL_ALARM_INPUT, OnOffType.OFF);
                    }
                    break;
            }
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void setAudioAlarmThreshold(int audioThreshold) {
        if (audioThreshold != this.audioThreshold) {
            this.audioThreshold = audioThreshold;
            OnvifCameraService service = getService();
            if (this.audioThreshold > 0) {
                service.sendHttpGET("/cgi-bin/hi3510/param.cgi?cmd=setaudioalarmattr&-aa_enable=1");
                service.sendHttpGET("/cgi-bin/hi3510/param.cgi?cmd=setaudioalarmattr&-aa_enable=1&-aa_value=" + audioThreshold);
            } else {
                service.sendHttpGET("/cgi-bin/hi3510/param.cgi?cmd=setaudioalarmattr&-aa_enable=0");
            }
        }
    }

    @UIVideoAction(name = IpCameraBindingConstants.CHANNEL_ENABLE_MOTION_ALARM, order = 14, icon = "fas fa-running")
    public void enableMotionAlarm(boolean on) {
        int val = boolToInt(on);
        getService().sendHttpGET("/cgi-bin/hi3510/param.cgi?cmd=setmdattr&-enable=" + val +
            "&-name=1&cmd=setmdattr&-enable=" + val + "&-name=2&cmd=setmdattr&-enable=" + val + "&-name=3&cmd=setmdattr&-enable=" + val +
            "&-name=4");
    }

    @UIVideoAction(name = IpCameraBindingConstants.CHANNEL_TEXT_OVERLAY, order = 100, icon = "fas fa-paragraph")
    public void textOverlay(String value) {
        String text = Helper.encodeSpecialChars(value);
        if (text.isEmpty()) {
            getService().sendHttpGET("/param.cgi?cmd=setoverlayattr&-region=1&-show=0");
        } else {
            getService().sendHttpGET("/param.cgi?cmd=setoverlayattr&-region=1&-show=1&-name=" + text);
        }
    }

    @UIVideoAction(name = IpCameraBindingConstants.CHANNEL_AUTO_LED, order = 60, icon = "fas fa-lightbulb")
    public void autoLED(boolean on) {
        getIRLedHandler().accept(on);
    }

    @Override
    public Consumer<Boolean> getIRLedHandler() {
        return on -> {
            if (on) {
                getService().sendHttpGET("/param.cgi?cmd=setinfrared&-infraredstat=auto");
            } else {
                getService().sendHttpGET("/param.cgi?cmd=setinfrared&-infraredstat=close");
            }
        };
    }

    @Override
    public Supplier<Boolean> getIrLedValueHandler() {
        return () -> Optional.ofNullable(getAttribute(IpCameraBindingConstants.CHANNEL_ENABLE_LED)).map(State::boolValue).orElse(false);
    }

    @UIVideoAction(name = IpCameraBindingConstants.CHANNEL_ENABLE_PIR_ALARM, order = 120, icon = "fas fa-compress-alt")
    public void enablePirAlarm(boolean on) {
        getService().sendHttpGET("/param.cgi?cmd=setpirattr&-pir_enable=" + boolToInt(on));
    }


    @UIVideoAction(name = IpCameraBindingConstants.CHANNEL_ENABLE_EXTERNAL_ALARM_INPUT, order = 250, icon = "fas fa-external-link-square-alt")
    public void enableExternalAlarmInput(boolean on) {
        getService().sendHttpGET("/param.cgi?cmd=setioattr&-io_enable=" + boolToInt(on));
    }

    public void alarmTriggered(String alarm) {
        OnvifCameraService service = getService();
        log.debug("[{}]: Alarm has been triggered:{}", entityID, alarm);
        switch (alarm) {
            case "/instar?&active=1":// The motion area boxes 1-4
            case "/instar?&active=2":
            case "/instar?&active=3":
            case "/instar?&active=4":
                service.motionDetected(true, IpCameraBindingConstants.CHANNEL_MOTION_ALARM);
                break;
            case "/instar?&active=5":// PIR
                service.motionDetected(true, IpCameraBindingConstants.CHANNEL_PIR_ALARM);
                break;
            case "/instar?&active=6":// Audio Alarm
                service.audioDetected(true);
                break;
            case "/instar?&active=7":// Motion Area 1
            case "/instar?&active=8":// Motion Area 2
            case "/instar?&active=9":// Motion Area 3
            case "/instar?&active=10":// Motion Area 4
                service.motionDetected(true, IpCameraBindingConstants.CHANNEL_MOTION_ALARM);
                break;
        }
    }

    @Override
    public void runOncePerMinute(EntityContext entityContext) {
        OnvifCameraService service = getService();
        service.sendHttpGET("/cgi-bin/hi3510/param.cgi?cmd=getaudioalarmattr");
        service.sendHttpGET("/cgi-bin/hi3510/param.cgi?cmd=getmdattr");
        service.sendHttpGET("/param.cgi?cmd=getinfrared");
        service.sendHttpGET("/param.cgi?cmd=getoverlayattr&-region=1");
        service.sendHttpGET("/param.cgi?cmd=getpirattr");
        service.sendHttpGET("/param.cgi?cmd=getioattr"); // ext alarm input on/off
    }

    @Override
    public void pollCameraRunnable() {
        OnvifCameraService service = getService();
        service.motionDetected(false, IpCameraBindingConstants.CHANNEL_MOTION_ALARM);
        service.motionDetected(false, IpCameraBindingConstants.CHANNEL_PIR_ALARM);
        service.audioDetected(false);
    }

    @Override
    public void initialize(EntityContext entityContext) {
        OnvifCameraService service = getService();
        if (StringUtils.isEmpty(service.getMjpegUri())) {
            service.setMjpegUri("/tmpfs/snap.jpg");
        }
        if (StringUtils.isEmpty(service.getSnapshotUri())) {
            service.setSnapshotUri("/mjpegstream.cgi?-chn=12");
        }
    }

    @Override
    public void handleSetURL(ChannelPipeline pipeline, String httpRequestURL) {
        InstarBrandHandler instarHandler = (InstarBrandHandler) pipeline.get("instarHandler");
        instarHandler.setURL(httpRequestURL);
    }
}
