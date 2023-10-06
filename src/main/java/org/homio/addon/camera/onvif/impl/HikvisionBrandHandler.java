package org.homio.addon.camera.onvif.impl;

import static org.homio.addon.camera.CameraConstants.AlarmEvents.FaceDetect;
import static org.homio.addon.camera.CameraConstants.AlarmEvents.FieldDetectAlarm;
import static org.homio.addon.camera.CameraConstants.AlarmEvents.ItemLeftDetection;
import static org.homio.addon.camera.CameraConstants.AlarmEvents.ItemTakenDetection;
import static org.homio.addon.camera.CameraConstants.AlarmEvents.LineCrossAlarm;
import static org.homio.addon.camera.CameraConstants.AlarmEvents.MotionAlarm;
import static org.homio.addon.camera.CameraConstants.AlarmEvents.PirAlarm;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_ACTIVATE_ALARM_OUTPUT;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_ENABLE_AUDIO_ALARM;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_ENABLE_EXTERNAL_ALARM;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_ENABLE_FIELD_DETECTION_ALARM;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_ENABLE_LINE_CROSSING_ALARM;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_ENABLE_MOTION_ALARM;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_ENABLE_PIR_ALARM;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_EXTERNAL_ALARM_INPUT;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_TEXT_OVERLAY;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_TRIGGER_EXTERNAL_ALARM_INPUT;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.util.ReferenceCountUtil;
import lombok.NoArgsConstructor;
import org.homio.addon.camera.onvif.brand.BaseOnvifCameraBrandHandler;
import org.homio.addon.camera.onvif.util.ChannelTracking;
import org.homio.addon.camera.onvif.util.Helper;
import org.homio.addon.camera.service.OnvifCameraService;
import org.homio.api.EntityContext;
import org.homio.api.state.OnOffType;
import org.homio.api.state.StringType;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.MediaType;

/**
 * responsible for handling commands, which are sent to one of the channels.
 */
@NoArgsConstructor
@CameraBrandHandler("Hikvision")
public class HikvisionBrandHandler extends BaseOnvifCameraBrandHandler {

    private int lineCount, vmdCount, leftCount, takenCount, faceCount, pirCount, fieldCount;

    public HikvisionBrandHandler(OnvifCameraService service) {
        super(service);
    }

    // This handles the incoming http replies back from the camera.
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg == null || ctx == null) {
            return;
        }
        try {
            int debounce = 3;
            String content = msg.toString();
            log.debug("[{}]: HTTP Result back from camera is \t:{}:", entityID, content);
            if (content.contains("--boundary")) {// Alarm checking goes in here//
                if (content.contains("<EventNotificationAlert version=\"")) {
                    if (content.contains("hannelID>" + nvrChannel + "</")) {// some camera use c or <dynChannelID>
                        if (content.contains("<eventType>linedetection</eventType>")) {
                            service.motionDetected(true, LineCrossAlarm);
                            lineCount = debounce;
                        }
                        if (content.contains("<eventType>fielddetection</eventType>")) {
                            service.motionDetected(true, FieldDetectAlarm);
                            fieldCount = debounce;
                        }
                        if (content.contains("<eventType>VMD</eventType>")) {
                            service.motionDetected(true, MotionAlarm);
                            vmdCount = debounce;
                        }
                        if (content.contains("<eventType>facedetection</eventType>")) {
                            service.motionDetected(true, FaceDetect);
                            faceCount = debounce;
                        }
                        if (content.contains("<eventType>unattendedBaggage</eventType>")) {
                            service.motionDetected(true, ItemLeftDetection);
                            leftCount = debounce;
                        }
                        if (content.contains("<eventType>attendedBaggage</eventType>")) {
                            service.motionDetected(true, ItemTakenDetection);
                            takenCount = debounce;
                        }
                        if (content.contains("<eventType>PIR</eventType>")) {
                            service.motionDetected(true, PirAlarm);
                            pirCount = debounce;
                        }
                        if (content.contains("<eventType>videoloss</eventType>\r\n<eventState>inactive</eventState>")) {
                            if (vmdCount > 1) {
                                vmdCount = 1;
                            }
                            countDown();
                            countDown();
                        }
                    } else if (content.contains("<channelID>0</channelID>")) {// NVR uses channel 0 to say all
                        // channels
                        if (content.contains("<eventType>videoloss</eventType>\r\n<eventState>inactive</eventState>")) {
                            if (vmdCount > 1) {
                                vmdCount = 1;
                            }
                            countDown();
                            countDown();
                        }
                    }
                    countDown();
                }
            } else {
                String replyElement = Helper.fetchXML(content, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>", "<");
                switch (replyElement) {
                    case "MotionDetection version=" -> {
                        service.storeHttpReply(
                                "/ISAPI/System/Video/inputs/channels/" + nvrChannel + "01/motionDetection", content);
                        if (content.contains("<enabled>true</enabled>")) {
                            getEndpointRequire(ENDPOINT_ENABLE_MOTION_ALARM).setValue(OnOffType.ON, true);
                        } else if (content.contains("<enabled>false</enabled>")) {
                            getEndpointRequire(ENDPOINT_ENABLE_MOTION_ALARM).setValue(OnOffType.OFF, true);
                        }
                    }
                    case "IOInputPort version=" -> {
                        service.storeHttpReply("/ISAPI/System/IO/inputs/" + nvrChannel, content);
                        if (content.contains("<enabled>true</enabled>")) {
                            getEndpointRequire(ENDPOINT_ENABLE_EXTERNAL_ALARM).setValue(OnOffType.ON, true);
                        } else if (content.contains("<enabled>false</enabled>")) {
                            getEndpointRequire(ENDPOINT_ENABLE_EXTERNAL_ALARM).setValue(OnOffType.OFF, true);
                        }
                        if (content.contains("<triggering>low</triggering>")) {
                            getEndpointRequire(ENDPOINT_TRIGGER_EXTERNAL_ALARM_INPUT).setValue(OnOffType.OFF, true);
                        } else if (content.contains("<triggering>high</triggering>")) {
                            getEndpointRequire(ENDPOINT_TRIGGER_EXTERNAL_ALARM_INPUT).setValue(OnOffType.ON, true);
                        }
                    }
                    case "LineDetection" -> {
                        service.storeHttpReply("/ISAPI/Smart/LineDetection/" + nvrChannel + "01", content);
                        if (content.contains("<enabled>true</enabled>")) {
                            getEndpointRequire(ENDPOINT_ENABLE_LINE_CROSSING_ALARM).setValue(OnOffType.ON, true);
                        } else if (content.contains("<enabled>false</enabled>")) {
                            getEndpointRequire(ENDPOINT_ENABLE_LINE_CROSSING_ALARM).setValue(OnOffType.OFF, true);
                        }
                    }
                    case "TextOverlay version=" -> {
                        service.storeHttpReply(
                                "/ISAPI/System/Video/inputs/channels/" + nvrChannel + "/overlays/text/1", content);
                        String text = Helper.fetchXML(content, "<enabled>true</enabled>", "<displayText>");
                        getEndpointRequire(ENDPOINT_ENABLE_AUDIO_ALARM).setValue(new StringType(text), true);
                    }
                    case "AudioDetection version=" -> {
                        service.storeHttpReply("/ISAPI/Smart/AudioDetection/channels/" + nvrChannel + "01",
                                content);
                        if (content.contains("<enabled>true</enabled>")) {
                            getEndpointRequire(ENDPOINT_ENABLE_AUDIO_ALARM).setValue(OnOffType.ON, true);
                        } else if (content.contains("<enabled>false</enabled>")) {
                            getEndpointRequire(ENDPOINT_ENABLE_AUDIO_ALARM).setValue(OnOffType.OFF, true);
                        }
                    }
                    case "IOPortStatus version=" -> {
                        if (content.contains("<ioState>active</ioState>")) {
                            getEndpointRequire(ENDPOINT_EXTERNAL_ALARM_INPUT).setValue(OnOffType.ON, true);
                        } else if (content.contains("<ioState>inactive</ioState>")) {
                            getEndpointRequire(ENDPOINT_EXTERNAL_ALARM_INPUT).setValue(OnOffType.OFF, true);
                        }
                    }
                    case "FieldDetection version=" -> {
                        service.storeHttpReply("/ISAPI/Smart/FieldDetection/" + nvrChannel + "01", content);
                        if (content.contains("<enabled>true</enabled>")) {
                            getEndpointRequire(ENDPOINT_ENABLE_FIELD_DETECTION_ALARM).setValue(OnOffType.ON, true);
                        } else if (content.contains("<enabled>false</enabled>")) {
                            getEndpointRequire(ENDPOINT_ENABLE_FIELD_DETECTION_ALARM).setValue(OnOffType.OFF, true);
                        }
                    }
                    case "ResponseStatus version=" -> {
                        ////////////////// External Alarm Input ///////////////
                        if (content.contains("<requestURL>/ISAPI/System/IO/inputs/" + nvrChannel + "/status</requestURL>")) {
                            // Stops checking the external alarm if camera does not have feature.
                            if (content.contains("<statusString>Invalid Operation</statusString>")) {
                                log.debug("[{}]: Stopping checks for alarm inputs as camera appears to be missing this feature.", entityID);
                            }
                        }
                    }
                    default -> {
                        if (content.contains("<EventNotificationAlert")) {
                            if (content.contains("hannelID>" + nvrChannel + "</")
                                    || content.contains("<channelID>0</channelID>")) {// some camera use c or
                                // <dynChannelID>
                                if (content.contains(
                                        "<eventType>videoloss</eventType>\r\n<eventState>inactive</eventState>")) {
                                    if (vmdCount > 1) {
                                        vmdCount = 1;
                                    }
                                    countDown();
                                    countDown();
                                }
                                countDown();
                            }
                        } else {
                            log.debug("[{}]: Unhandled reply-{}.", entityID, content);
                        }
                    }
                }
            }
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    public void hikSendXml(String httpPutURL, String xml) {
        log.debug("[{}]: Body for PUT:{} is going to be:{}", entityID, httpPutURL, xml);
        FullHttpRequest fullHttpRequest = buildFullHttpRequest(httpPutURL, xml, HttpMethod.PUT, MediaType.APPLICATION_XML);
        service.sendHttpPUT(httpPutURL, fullHttpRequest);
    }

    public void hikChangeSetting(String httpGetPutURL, String removeElement, String replaceRemovedElementWith) {
        ChannelTracking localTracker = service.getChannelTrack(httpGetPutURL);
        if (localTracker == null) {
            service.sendHttpGET(httpGetPutURL);
            log.debug("[{}]: Did not have a reply stored before hikChangeSetting was run, try again shortly as a reply has just been requested.", entityID);
            return;
        }
        String body = localTracker.getReply();
        if (body.isEmpty()) {
            log.debug("[{}]: Did not have a reply stored before hikChangeSetting was run, try again shortly as a reply has just been requested.", entityID);
            service.sendHttpGET(httpGetPutURL);
        } else {
            log.debug("[{}]: An OLD reply from the camera was:{}", entityID, body);
            if (body.contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")) {
                body = body.substring("<?xml version=\"1.0\" encoding=\"UTF-8\"?>".length());
            }
            int elementIndexStart = body.indexOf("<" + removeElement + ">");
            int elementIndexEnd = body.indexOf("</" + removeElement + ">");
            body = body.substring(0, elementIndexStart) + replaceRemovedElementWith
                    + body.substring(elementIndexEnd + removeElement.length() + 3);
            log.debug("[{}]: Body for this PUT is going to be:{}", entityID, body);
            localTracker.setReply(body);
            FullHttpRequest fullHttpRequest = buildFullHttpRequest(httpGetPutURL, body, HttpMethod.PUT, MediaType.APPLICATION_XML);
            service.sendHttpPUT(httpGetPutURL, fullHttpRequest);
        }
    }

    @Override
    public boolean isHasMotionAlarm() {
        return true;
    }

    @Override
    public void setMotionAlarmThreshold(int threshold) {
        hikChangeSetting("/ISAPI/WLAlarm/PIR", "enabled", "<enabled>" + (threshold > 0) + "</enabled>");
    }

    // This does debouncing of the alarms
    void countDown() {
        if (lineCount > 1) {
            lineCount--;
        } else if (lineCount == 1) {
            service.motionDetected(false, LineCrossAlarm);
            lineCount--;
        }
        if (vmdCount > 1) {
            vmdCount--;
        } else if (vmdCount == 1) {
            service.motionDetected(false, MotionAlarm);
            vmdCount--;
        }
        if (leftCount > 1) {
            leftCount--;
        } else if (leftCount == 1) {
            service.motionDetected(false, ItemLeftDetection);
            leftCount--;
        }
        if (takenCount > 1) {
            takenCount--;
        } else if (takenCount == 1) {
            service.motionDetected(false, ItemTakenDetection);
            takenCount--;
        }
        if (faceCount > 1) {
            faceCount--;
        } else if (faceCount == 1) {
            service.motionDetected(false, FaceDetect);
            faceCount--;
        }
        if (pirCount > 1) {
            pirCount--;
        } else if (pirCount == 1) {
            service.motionDetected(false, PirAlarm);
            pirCount--;
        }
        if (fieldCount > 1) {
            fieldCount--;
        } else if (fieldCount == 1) {
            service.motionDetected(false, FieldDetectAlarm);
            fieldCount--;
        }
        if (fieldCount == 0 && pirCount == 0 && faceCount == 0 && takenCount == 0 && leftCount == 0 && vmdCount == 0
                && lineCount == 0) {
            service.motionDetected(false, MotionAlarm);
        }
    }

    @Override
    public void pollCameraRunnable() {
        if (service.streamIsStopped("/ISAPI/Event/notification/alertStream")) {
            log.info("[{}]: The alarm stream was not running for camera {}, re-starting it now",
                    entityID, getEntity().getIp());
            service.sendHttpGET("/ISAPI/Event/notification/alertStream");
        }
    }

    @Override
    public @Nullable String getSnapshotUri() {
        return "/ISAPI/Streaming/channels/" + nvrChannel + "01" + "/picture";
    }

    @Override
    public @Nullable String getMjpegUri() {
        return "/ISAPI/Streaming/channels/" + nvrChannel + "02" + "/httppreview";
    }

    @Override
    public void postInitializeCamera(EntityContext entityContext) {
        if (service.lowPriorityRequests.isEmpty()) {
            service.addLowRequestGet("/ISAPI/System/IO/inputs/" + nvrChannel + "/status");
        }
    }

    @Override
    public void onCameraConnected() {
        addEndpoints();
        service.sendHttpGET("/ISAPI/System/Video/inputs/channels/" + nvrChannel + "01/motionDetection");
        service.sendHttpGET("/ISAPI/Smart/LineDetection/" + nvrChannel + "01");
        service.sendHttpGET("/ISAPI/Smart/AudioDetection/channels/" + nvrChannel + "01");
        service.sendHttpGET("/ISAPI/System/Video/inputs/channels/" + nvrChannel + "/overlays/text/1");
        service.sendHttpGET("/ISAPI/System/IO/inputs/" + nvrChannel);
    }

    private void addEndpoints() {
        service.addEndpointSwitch(ENDPOINT_ENABLE_LINE_CROSSING_ALARM, state ->
            hikChangeSetting("/ISAPI/Smart/LineDetection/" + nvrChannel + "01", "enabled",
                "<enabled>" + state.boolValue() + "</enabled>"));

        service.addEndpointSwitch(ENDPOINT_ENABLE_MOTION_ALARM, state ->
            hikChangeSetting("/ISAPI/System/Video/inputs/channels/" + nvrChannel + "01/motionDetection",
                "enabled", "<enabled>" + state.boolValue() + "</enabled>"));

        service.addEndpointSwitch(ENDPOINT_ENABLE_FIELD_DETECTION_ALARM, state ->
            hikChangeSetting("/ISAPI/Smart/FieldDetection/" + nvrChannel + "01", "enabled",
                "<enabled>" + state.boolValue() + "</enabled>"));

        service.addEndpointSwitch(ENDPOINT_ACTIVATE_ALARM_OUTPUT, state ->
            hikSendXml("/ISAPI/System/IO/outputs/" + nvrChannel + "/trigger",
                "<IOPortData version=\"1.0\" xmlns=\"http://www.hikvision.com/ver10/XMLSchema\">\r\n    <outputState>" +
                    (state.boolValue() ? "high" : "low") + "</outputState>\r\n</IOPortData>\r\n"));

        service.addEndpointInput(ENDPOINT_TEXT_OVERLAY, state -> {
            if (state.stringValue().isEmpty()) {
                hikChangeSetting("/ISAPI/System/Video/inputs/channels/" + nvrChannel + "/overlays/text/1",
                    "enabled", "<enabled>false</enabled>");
            } else {
                hikChangeSetting("/ISAPI/System/Video/inputs/channels/" + nvrChannel + "/overlays/text/1",
                    "displayText", "<displayText>" + state.stringValue() + "</displayText>");
                hikChangeSetting("/ISAPI/System/Video/inputs/channels/" + nvrChannel + "/overlays/text/1",
                    "enabled", "<enabled>true</enabled>");
            }
        });

        service.addEndpointSwitch(ENDPOINT_ENABLE_EXTERNAL_ALARM, state ->
            hikChangeSetting("/ISAPI/System/IO/inputs/" + nvrChannel,
                "enabled", "<enabled>" + state.boolValue() + "</enabled>"));

        service.addEndpointSwitch(ENDPOINT_TRIGGER_EXTERNAL_ALARM_INPUT, state ->
            hikChangeSetting("/ISAPI/System/IO/inputs/" + nvrChannel, "triggering",
                "<triggering>" + (state.boolValue() ? "high" : "low") + "</triggering>"));

        service.addEndpointSwitch(ENDPOINT_ENABLE_PIR_ALARM, state ->
            hikChangeSetting("/ISAPI/WLAlarm/PIR", "enabled",
                "<enabled>" + state.boolValue() + "</enabled>"));
    }

    @Override
    public String getUrlToKeepOpenForIdleStateEvent() {
        return "/ISAPI/Event/notification/alertStream";
    }
}
