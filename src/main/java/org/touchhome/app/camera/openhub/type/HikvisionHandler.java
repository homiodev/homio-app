package org.touchhome.app.camera.openhub.type;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;
import lombok.RequiredArgsConstructor;
import org.touchhome.app.camera.openhub.CameraHandler;
import org.touchhome.app.camera.openhub.ChannelTracking;
import org.touchhome.app.camera.openhub.Helper;
import org.touchhome.app.camera.openhub.UICameraAction;
import org.touchhome.app.camera.openhub.handler.IpCameraHandler;
import org.touchhome.bundle.api.measure.OnOffType;
import org.touchhome.bundle.api.measure.StringType;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import static org.touchhome.app.camera.openhub.IpCameraBindingConstants.*;

/**
 * responsible for handling commands, which are sent to one of the channels.
 */
@RequiredArgsConstructor
public class HikvisionHandler extends CameraHandler {

    private final IpCameraHandler ipCameraHandler;
    private final int nvrChannel;

    private int lineCount, vmdCount, leftCount, takenCount, faceCount, pirCount, fieldCount;

    // This handles the incoming http replies back from the camera.
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg == null || ctx == null) {
            return;
        }
        try {
            int debounce = 3;
            String content = msg.toString();
            ipCameraHandler.getLog().trace("HTTP Result back from camera is \t:{}:", content);
            if (content.contains("--boundary")) {// Alarm checking goes in here//
                if (content.contains("<EventNotificationAlert version=\"")) {
                    if (content.contains("hannelID>" + nvrChannel + "</")) {// some camera use c or <dynChannelID>
                        if (content.contains("<eventType>linedetection</eventType>")) {
                            ipCameraHandler.motionDetected(CHANNEL_LINE_CROSSING_ALARM);
                            lineCount = debounce;
                        }
                        if (content.contains("<eventType>fielddetection</eventType>")) {
                            ipCameraHandler.motionDetected(CHANNEL_FIELD_DETECTION_ALARM);
                            fieldCount = debounce;
                        }
                        if (content.contains("<eventType>VMD</eventType>")) {
                            ipCameraHandler.motionDetected(CHANNEL_MOTION_ALARM);
                            vmdCount = debounce;
                        }
                        if (content.contains("<eventType>facedetection</eventType>")) {
                            ipCameraHandler.setChannelState(CHANNEL_FACE_DETECTED, OnOffType.ON);
                            faceCount = debounce;
                        }
                        if (content.contains("<eventType>unattendedBaggage</eventType>")) {
                            ipCameraHandler.setChannelState(CHANNEL_ITEM_LEFT, OnOffType.ON);
                            leftCount = debounce;
                        }
                        if (content.contains("<eventType>attendedBaggage</eventType>")) {
                            ipCameraHandler.setChannelState(CHANNEL_ITEM_TAKEN, OnOffType.ON);
                            takenCount = debounce;
                        }
                        if (content.contains("<eventType>PIR</eventType>")) {
                            ipCameraHandler.motionDetected(CHANNEL_PIR_ALARM);
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
                    case "MotionDetection version=":
                        ipCameraHandler.storeHttpReply(
                                "/ISAPI/System/Video/inputs/channels/" + nvrChannel + "01/motionDetection", content);
                        if (content.contains("<enabled>true</enabled>")) {
                            ipCameraHandler.setChannelState(CHANNEL_ENABLE_MOTION_ALARM, OnOffType.ON);
                        } else if (content.contains("<enabled>false</enabled>")) {
                            ipCameraHandler.setChannelState(CHANNEL_ENABLE_MOTION_ALARM, OnOffType.OFF);
                        }
                        break;
                    case "IOInputPort version=":
                        ipCameraHandler.storeHttpReply("/ISAPI/System/IO/inputs/" + nvrChannel, content);
                        if (content.contains("<enabled>true</enabled>")) {
                            ipCameraHandler.setChannelState(CHANNEL_ENABLE_EXTERNAL_ALARM_INPUT, OnOffType.ON);
                        } else if (content.contains("<enabled>false</enabled>")) {
                            ipCameraHandler.setChannelState(CHANNEL_ENABLE_EXTERNAL_ALARM_INPUT, OnOffType.OFF);
                        }
                        if (content.contains("<triggering>low</triggering>")) {
                            ipCameraHandler.setChannelState(CHANNEL_TRIGGER_EXTERNAL_ALARM_INPUT, OnOffType.OFF);
                        } else if (content.contains("<triggering>high</triggering>")) {
                            ipCameraHandler.setChannelState(CHANNEL_TRIGGER_EXTERNAL_ALARM_INPUT, OnOffType.ON);
                        }
                        break;
                    case "LineDetection":
                        ipCameraHandler.storeHttpReply("/ISAPI/Smart/LineDetection/" + nvrChannel + "01", content);
                        if (content.contains("<enabled>true</enabled>")) {
                            ipCameraHandler.setChannelState(CHANNEL_ENABLE_LINE_CROSSING_ALARM, OnOffType.ON);
                        } else if (content.contains("<enabled>false</enabled>")) {
                            ipCameraHandler.setChannelState(CHANNEL_ENABLE_LINE_CROSSING_ALARM, OnOffType.OFF);
                        }
                        break;
                    case "TextOverlay version=":
                        ipCameraHandler.storeHttpReply(
                                "/ISAPI/System/Video/inputs/channels/" + nvrChannel + "/overlays/text/1", content);
                        String text = Helper.fetchXML(content, "<enabled>true</enabled>", "<displayText>");
                        ipCameraHandler.setChannelState(CHANNEL_TEXT_OVERLAY, new StringType(text));
                        break;
                    case "AudioDetection version=":
                        ipCameraHandler.storeHttpReply("/ISAPI/Smart/AudioDetection/channels/" + nvrChannel + "01",
                                content);
                        if (content.contains("<enabled>true</enabled>")) {
                            ipCameraHandler.setChannelState(CHANNEL_ENABLE_AUDIO_ALARM, OnOffType.ON);
                        } else if (content.contains("<enabled>false</enabled>")) {
                            ipCameraHandler.setChannelState(CHANNEL_ENABLE_AUDIO_ALARM, OnOffType.OFF);
                        }
                        break;
                    case "IOPortStatus version=":
                        if (content.contains("<ioState>active</ioState>")) {
                            ipCameraHandler.setChannelState(CHANNEL_EXTERNAL_ALARM_INPUT, OnOffType.ON);
                        } else if (content.contains("<ioState>inactive</ioState>")) {
                            ipCameraHandler.setChannelState(CHANNEL_EXTERNAL_ALARM_INPUT, OnOffType.OFF);
                        }
                        break;
                    case "FieldDetection version=":
                        ipCameraHandler.storeHttpReply("/ISAPI/Smart/FieldDetection/" + nvrChannel + "01", content);
                        if (content.contains("<enabled>true</enabled>")) {
                            ipCameraHandler.setChannelState(CHANNEL_ENABLE_FIELD_DETECTION_ALARM, OnOffType.ON);
                        } else if (content.contains("<enabled>false</enabled>")) {
                            ipCameraHandler.setChannelState(CHANNEL_ENABLE_FIELD_DETECTION_ALARM, OnOffType.OFF);
                        }
                        break;
                    case "ResponseStatus version=":
                        ////////////////// External Alarm Input ///////////////
                        if (content.contains(
                                "<requestURL>/ISAPI/System/IO/inputs/" + nvrChannel + "/status</requestURL>")) {
                            // Stops checking the external alarm if camera does not have feature.
                            if (content.contains("<statusString>Invalid Operation</statusString>")) {
                                ipCameraHandler.lowPriorityRequests.remove(0);
                                ipCameraHandler.getLog().debug("Stopping checks for alarm inputs as camera appears to be missing this feature.");
                            }
                        }
                        break;
                    default:
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
                            ipCameraHandler.getLog().debug("Unhandled reply-{}.", content);
                        }
                        break;
                }
            }
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    // This does debouncing of the alarms
    void countDown() {
        if (lineCount > 1) {
            lineCount--;
        } else if (lineCount == 1) {
            ipCameraHandler.setChannelState(CHANNEL_LINE_CROSSING_ALARM, OnOffType.OFF);
            lineCount--;
        }
        if (vmdCount > 1) {
            vmdCount--;
        } else if (vmdCount == 1) {
            ipCameraHandler.setChannelState(CHANNEL_MOTION_ALARM, OnOffType.OFF);
            vmdCount--;
        }
        if (leftCount > 1) {
            leftCount--;
        } else if (leftCount == 1) {
            ipCameraHandler.setChannelState(CHANNEL_ITEM_LEFT, OnOffType.OFF);
            leftCount--;
        }
        if (takenCount > 1) {
            takenCount--;
        } else if (takenCount == 1) {
            ipCameraHandler.setChannelState(CHANNEL_ITEM_TAKEN, OnOffType.OFF);
            takenCount--;
        }
        if (faceCount > 1) {
            faceCount--;
        } else if (faceCount == 1) {
            ipCameraHandler.setChannelState(CHANNEL_FACE_DETECTED, OnOffType.OFF);
            faceCount--;
        }
        if (pirCount > 1) {
            pirCount--;
        } else if (pirCount == 1) {
            ipCameraHandler.setChannelState(CHANNEL_PIR_ALARM, OnOffType.OFF);
            pirCount--;
        }
        if (fieldCount > 1) {
            fieldCount--;
        } else if (fieldCount == 1) {
            ipCameraHandler.setChannelState(CHANNEL_FIELD_DETECTION_ALARM, OnOffType.OFF);
            fieldCount--;
        }
        if (fieldCount == 0 && pirCount == 0 && faceCount == 0 && takenCount == 0 && leftCount == 0 && vmdCount == 0
                && lineCount == 0) {
            ipCameraHandler.noMotionDetected(CHANNEL_MOTION_ALARM);
        }
    }

    public void hikSendXml(String httpPutURL, String xml) {
        ipCameraHandler.getLog().trace("Body for PUT:{} is going to be:{}", httpPutURL, xml);
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, new HttpMethod("PUT"), httpPutURL);
        request.headers().set(HttpHeaderNames.HOST, ipCameraHandler.getCameraEntity().getIp());
        request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        request.headers().add(HttpHeaderNames.CONTENT_TYPE, "application/xml; charset=\"UTF-8\"");
        ByteBuf bbuf = Unpooled.copiedBuffer(xml, StandardCharsets.UTF_8);
        request.headers().set(HttpHeaderNames.CONTENT_LENGTH, bbuf.readableBytes());
        request.content().clear().writeBytes(bbuf);
        ipCameraHandler.sendHttpPUT(httpPutURL, request);
    }

    public void hikChangeSetting(String httpGetPutURL, String removeElement, String replaceRemovedElementWith) {
        ChannelTracking localTracker = ipCameraHandler.channelTrackingMap.get(httpGetPutURL);
        if (localTracker == null) {
            ipCameraHandler.sendHttpGET(httpGetPutURL);
            ipCameraHandler.getLog().debug(
                    "Did not have a reply stored before hikChangeSetting was run, try again shortly as a reply has just been requested.");
            return;
        }
        String body = localTracker.getReply();
        if (body.isEmpty()) {
            ipCameraHandler.getLog().debug(
                    "Did not have a reply stored before hikChangeSetting was run, try again shortly as a reply has just been requested.");
            ipCameraHandler.sendHttpGET(httpGetPutURL);
        } else {
            ipCameraHandler.getLog().trace("An OLD reply from the camera was:{}", body);
            if (body.contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")) {
                body = body.substring("<?xml version=\"1.0\" encoding=\"UTF-8\"?>".length());
            }
            int elementIndexStart = body.indexOf("<" + removeElement + ">");
            int elementIndexEnd = body.indexOf("</" + removeElement + ">");
            body = body.substring(0, elementIndexStart) + replaceRemovedElementWith
                    + body.substring(elementIndexEnd + removeElement.length() + 3, body.length());
            ipCameraHandler.getLog().trace("Body for this PUT is going to be:{}", body);
            localTracker.setReply(body);
            FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, new HttpMethod("PUT"),
                    httpGetPutURL);
            request.headers().set(HttpHeaderNames.HOST, ipCameraHandler.getCameraEntity().getIp());
            request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
            request.headers().add(HttpHeaderNames.CONTENT_TYPE, "application/xml; charset=\"UTF-8\"");
            ByteBuf bbuf = Unpooled.copiedBuffer(body, StandardCharsets.UTF_8);
            request.headers().set(HttpHeaderNames.CONTENT_LENGTH, bbuf.readableBytes());
            request.content().clear().writeBytes(bbuf);
            ipCameraHandler.sendHttpPUT(httpGetPutURL, request);
        }
    }

    // If a camera does not need to poll a request as often as snapshots, it can be
    // added here. Binding steps through the list.
    public ArrayList<String> getLowPriorityRequests() {
        ArrayList<String> lowPriorityRequests = new ArrayList<>(1);
        lowPriorityRequests.add("/ISAPI/System/IO/inputs/" + nvrChannel + "/status"); // must stay in element 0.
        return lowPriorityRequests;
    }

    @UICameraAction(name = CHANNEL_TEXT_OVERLAY)
    public void textOverlay(String command) {
        ipCameraHandler.getLog().debug("Changing text overlay to {}", command);
        if (command.isEmpty()) {
            hikChangeSetting("/ISAPI/System/Video/inputs/channels/" + nvrChannel + "/overlays/text/1",
                    "enabled", "<enabled>false</enabled>");
        } else {
            hikChangeSetting("/ISAPI/System/Video/inputs/channels/" + nvrChannel + "/overlays/text/1",
                    "displayText", "<displayText>" + command + "</displayText>");
            hikChangeSetting("/ISAPI/System/Video/inputs/channels/" + nvrChannel + "/overlays/text/1",
                    "enabled", "<enabled>true</enabled>");
        }
    }

    @UICameraAction(name = CHANNEL_ENABLE_EXTERNAL_ALARM_INPUT)
    public void enableExternalAlarmInput(boolean on) {
        ipCameraHandler.getLog().debug("Changing enabled state of the external input 1 to {}", on);
        hikChangeSetting("/ISAPI/System/IO/inputs/" + nvrChannel, "enabled", "<enabled>" + on + "</enabled>");
    }

    @UICameraAction(name = CHANNEL_TRIGGER_EXTERNAL_ALARM_INPUT)
    public void triggerExternalAlarmInput(boolean on) {
        ipCameraHandler.getLog().debug("Changing triggering state of the external input 1 to {}", on);
        hikChangeSetting("/ISAPI/System/IO/inputs/" + nvrChannel, "triggering",
                "<triggering>" + (on ? "high" : "low") + "</triggering>");
    }

    @UICameraAction(name = CHANNEL_ENABLE_PIR_ALARM)
    public void enablePirAlarm(boolean on) {
        hikChangeSetting("/ISAPI/WLAlarm/PIR", "enabled", "<enabled>" + on + "</enabled>");
    }

    @UICameraAction(name = CHANNEL_ENABLE_AUDIO_ALARM)
    public void enableAudioAlarm(boolean on) {
        hikChangeSetting("/ISAPI/WLAlarm/PIR", "enabled", "<enabled>" + on + "</enabled>");
    }

    @UICameraAction(name = CHANNEL_ENABLE_LINE_CROSSING_ALARM)
    public void enableLineCrossingAlarm(boolean on) {
        hikChangeSetting("/ISAPI/Smart/LineDetection/" + nvrChannel + "01", "enabled",
                "<enabled>" + on + "</enabled>");
    }

    @UICameraAction(name = CHANNEL_ENABLE_MOTION_ALARM)
    public void enableMotionAlarm(boolean on) {
        hikChangeSetting("/ISAPI/System/Video/inputs/channels/" + nvrChannel + "01/motionDetection",
                "enabled", "<enabled>" + on + "</enabled>");
    }

    @UICameraAction(name = CHANNEL_ENABLE_FIELD_DETECTION_ALARM)
    public void enableFieldDetectionAlarm(boolean on) {
        hikChangeSetting("/ISAPI/Smart/FieldDetection/" + nvrChannel + "01", "enabled",
                "<enabled>" + on + "</enabled>");
    }

    @UICameraAction(name = CHANNEL_ACTIVATE_ALARM_OUTPUT)
    public void activateAlarmOutput(boolean on) {
        hikSendXml("/ISAPI/System/IO/outputs/" + nvrChannel + "/trigger",
                "<IOPortData version=\"1.0\" xmlns=\"http://www.hikvision.com/ver10/XMLSchema\">\r\n    <outputState>" +
                        (on ? "high" : "low") + "</outputState>\r\n</IOPortData>\r\n");
    }

    /*@Override
    protected List<CameraAction> getCameraActions() {
        return CameraAction.builder()
                .add(CHANNEL_TEXT_OVERLAY, UIFieldType.String, param -> {
                    String command = param.getString("value");
                    ipCameraHandler.getLog().debug("Changing text overlay to {}", command);
                    if (command.isEmpty()) {
                        hikChangeSetting("/ISAPI/System/Video/inputs/channels/" + nvrChannel + "/overlays/text/1",
                                "enabled", "<enabled>false</enabled>");
                    } else {
                        hikChangeSetting("/ISAPI/System/Video/inputs/channels/" + nvrChannel + "/overlays/text/1",
                                "displayText", "<displayText>" + command + "</displayText>");
                        hikChangeSetting("/ISAPI/System/Video/inputs/channels/" + nvrChannel + "/overlays/text/1",
                                "enabled", "<enabled>true</enabled>");
                    }
                }, () -> ipCameraHandler.sendHttpGET("/ISAPI/System/Video/inputs/channels/" + nvrChannel + "/overlays/text/1"))
                .add(CHANNEL_ENABLE_EXTERNAL_ALARM_INPUT, UIFieldType.Boolean, param -> {
                    boolean on = param.getBoolean("value");
                    ipCameraHandler.getLog().debug("Changing enabled state of the external input 1 to {}", on);
                    if (on) {
                        hikChangeSetting("/ISAPI/System/IO/inputs/" + nvrChannel, "enabled", "<enabled>true</enabled>");
                    } else {
                        hikChangeSetting("/ISAPI/System/IO/inputs/" + nvrChannel, "enabled", "<enabled>false</enabled>");
                    }
                }, () -> ipCameraHandler.sendHttpGET("/ISAPI/System/IO/inputs/" + nvrChannel))
                .add(CHANNEL_TRIGGER_EXTERNAL_ALARM_INPUT, UIFieldType.Boolean, param -> {
                    boolean on = param.getBoolean("value");
                    ipCameraHandler.getLog().debug("Changing triggering state of the external input 1 to {}", on);
                    if (!on) {
                        hikChangeSetting("/ISAPI/System/IO/inputs/" + nvrChannel, "triggering",
                                "<triggering>low</triggering>");
                    } else {
                        hikChangeSetting("/ISAPI/System/IO/inputs/" + nvrChannel, "triggering",
                                "<triggering>high</triggering>");
                    }
                }, () -> ipCameraHandler.sendHttpGET("/ISAPI/System/IO/inputs/" + nvrChannel))
                .add(CHANNEL_ENABLE_PIR_ALARM, UIFieldType.Boolean, param -> {
                    if (param.getBoolean("value")) {
                        hikChangeSetting("/ISAPI/WLAlarm/PIR", "enabled", "<enabled>true</enabled>");
                    } else {
                        hikChangeSetting("/ISAPI/WLAlarm/PIR", "enabled", "<enabled>false</enabled>");
                    }
                })
                .add(CHANNEL_ENABLE_AUDIO_ALARM, UIFieldType.Boolean, param -> {
                    if (param.getBoolean("value")) {
                        hikChangeSetting("/ISAPI/Smart/AudioDetection/channels/" + nvrChannel + "01", "enabled",
                                "<enabled>true</enabled>");
                    } else {
                        hikChangeSetting("/ISAPI/Smart/AudioDetection/channels/" + nvrChannel + "01", "enabled",
                                "<enabled>false</enabled>");
                    }
                }, () -> ipCameraHandler.sendHttpGET("/ISAPI/Smart/AudioDetection/channels/" + nvrChannel + "01"))
                .add(CHANNEL_ENABLE_LINE_CROSSING_ALARM, UIFieldType.Boolean, param -> {
                    if (param.getBoolean("value")) {
                        hikChangeSetting("/ISAPI/Smart/LineDetection/" + nvrChannel + "01", "enabled",
                                "<enabled>true</enabled>");
                    } else {
                        hikChangeSetting("/ISAPI/Smart/LineDetection/" + nvrChannel + "01", "enabled",
                                "<enabled>false</enabled>");
                    }
                }, () -> ipCameraHandler.sendHttpGET("/ISAPI/Smart/LineDetection/" + nvrChannel + "01"))
                .add(CHANNEL_ENABLE_MOTION_ALARM, UIFieldType.Boolean, param -> {
                    if (param.getBoolean("value")) {
                        hikChangeSetting("/ISAPI/System/Video/inputs/channels/" + nvrChannel + "01/motionDetection",
                                "enabled", "<enabled>true</enabled>");
                    } else {
                        hikChangeSetting("/ISAPI/System/Video/inputs/channels/" + nvrChannel + "01/motionDetection",
                                "enabled", "<enabled>false</enabled>");
                    }
                }, () -> ipCameraHandler
                        .sendHttpGET("/ISAPI/System/Video/inputs/channels/" + nvrChannel + "01/motionDetection"))
                .add(CHANNEL_ENABLE_FIELD_DETECTION_ALARM, UIFieldType.Boolean, param -> {
                    if (param.getBoolean("value")) {
                        hikChangeSetting("/ISAPI/Smart/FieldDetection/" + nvrChannel + "01", "enabled",
                                "<enabled>true</enabled>");
                    } else {
                        hikChangeSetting("/ISAPI/Smart/FieldDetection/" + nvrChannel + "01", "enabled",
                                "<enabled>false</enabled>");
                    }
                }, () -> {
                    ipCameraHandler.getLog().debug("FieldDetection command");
                    ipCameraHandler.sendHttpGET("/ISAPI/Smart/FieldDetection/" + nvrChannel + "01");
                })
                .add(CHANNEL_ACTIVATE_ALARM_OUTPUT, UIFieldType.Boolean, param -> {
                    if (param.getBoolean("value")) {
                        hikSendXml("/ISAPI/System/IO/outputs/" + nvrChannel + "/trigger",
                                "<IOPortData version=\"1.0\" xmlns=\"http://www.hikvision.com/ver10/XMLSchema\">\r\n    <outputState>high</outputState>\r\n</IOPortData>\r\n");
                    } else {
                        hikSendXml("/ISAPI/System/IO/outputs/" + nvrChannel + "/trigger",
                                "<IOPortData version=\"1.0\" xmlns=\"http://www.hikvision.com/ver10/XMLSchema\">\r\n    <outputState>low</outputState>\r\n</IOPortData>\r\n");
                    }
                })
                .get();
    }*/
}
