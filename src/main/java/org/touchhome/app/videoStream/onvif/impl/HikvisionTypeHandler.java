package org.touchhome.app.videoStream.onvif.impl;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;
import org.touchhome.app.videoStream.entity.OnvifCameraEntity;
import org.touchhome.app.videoStream.onvif.util.CameraTypeHandler;
import org.touchhome.app.videoStream.onvif.util.ChannelTracking;
import org.touchhome.app.videoStream.onvif.util.Helper;
import org.touchhome.app.videoStream.ui.UICameraAction;
import org.touchhome.app.videoStream.ui.UICameraActionGetter;
import org.touchhome.bundle.api.measure.OnOffType;
import org.touchhome.bundle.api.measure.State;
import org.touchhome.bundle.api.measure.StringType;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import static org.touchhome.app.videoStream.onvif.util.IpCameraBindingConstants.*;

/**
 * responsible for handling commands, which are sent to one of the channels.
 */
public class HikvisionTypeHandler extends CameraTypeHandler {

    private int lineCount, vmdCount, leftCount, takenCount, faceCount, pirCount, fieldCount;

    public HikvisionTypeHandler(OnvifCameraEntity onvifCameraEntity) {
        super(onvifCameraEntity);
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
            onvifCameraHandler.getLog().trace("HTTP Result back from camera is \t:{}:", content);
            if (content.contains("--boundary")) {// Alarm checking goes in here//
                if (content.contains("<EventNotificationAlert version=\"")) {
                    if (content.contains("hannelID>" + nvrChannel + "</")) {// some camera use c or <dynChannelID>
                        if (content.contains("<eventType>linedetection</eventType>")) {
                            onvifCameraHandler.motionDetected(CHANNEL_LINE_CROSSING_ALARM);
                            lineCount = debounce;
                        }
                        if (content.contains("<eventType>fielddetection</eventType>")) {
                            onvifCameraHandler.motionDetected(CHANNEL_FIELD_DETECTION_ALARM);
                            fieldCount = debounce;
                        }
                        if (content.contains("<eventType>VMD</eventType>")) {
                            onvifCameraHandler.motionDetected(CHANNEL_MOTION_ALARM);
                            vmdCount = debounce;
                        }
                        if (content.contains("<eventType>facedetection</eventType>")) {
                            attributes.put(CHANNEL_FACE_DETECTED, OnOffType.ON);
                            faceCount = debounce;
                        }
                        if (content.contains("<eventType>unattendedBaggage</eventType>")) {
                            attributes.put(CHANNEL_ITEM_LEFT, OnOffType.ON);
                            leftCount = debounce;
                        }
                        if (content.contains("<eventType>attendedBaggage</eventType>")) {
                            attributes.put(CHANNEL_ITEM_TAKEN, OnOffType.ON);
                            takenCount = debounce;
                        }
                        if (content.contains("<eventType>PIR</eventType>")) {
                            onvifCameraHandler.motionDetected(CHANNEL_PIR_ALARM);
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
                        onvifCameraHandler.storeHttpReply(
                                "/ISAPI/System/Video/inputs/channels/" + nvrChannel + "01/motionDetection", content);
                        if (content.contains("<enabled>true</enabled>")) {
                            attributes.put(CHANNEL_ENABLE_MOTION_ALARM, OnOffType.ON);
                        } else if (content.contains("<enabled>false</enabled>")) {
                            attributes.put(CHANNEL_ENABLE_MOTION_ALARM, OnOffType.OFF);
                        }
                        break;
                    case "IOInputPort version=":
                        onvifCameraHandler.storeHttpReply("/ISAPI/System/IO/inputs/" + nvrChannel, content);
                        if (content.contains("<enabled>true</enabled>")) {
                            attributes.put(CHANNEL_ENABLE_EXTERNAL_ALARM_INPUT, OnOffType.ON);
                        } else if (content.contains("<enabled>false</enabled>")) {
                            attributes.put(CHANNEL_ENABLE_EXTERNAL_ALARM_INPUT, OnOffType.OFF);
                        }
                        if (content.contains("<triggering>low</triggering>")) {
                            attributes.put(CHANNEL_TRIGGER_EXTERNAL_ALARM_INPUT, OnOffType.OFF);
                        } else if (content.contains("<triggering>high</triggering>")) {
                            attributes.put(CHANNEL_TRIGGER_EXTERNAL_ALARM_INPUT, OnOffType.ON);
                        }
                        break;
                    case "LineDetection":
                        onvifCameraHandler.storeHttpReply("/ISAPI/Smart/LineDetection/" + nvrChannel + "01", content);
                        if (content.contains("<enabled>true</enabled>")) {
                            attributes.put(CHANNEL_ENABLE_LINE_CROSSING_ALARM, OnOffType.ON);
                        } else if (content.contains("<enabled>false</enabled>")) {
                            attributes.put(CHANNEL_ENABLE_LINE_CROSSING_ALARM, OnOffType.OFF);
                        }
                        break;
                    case "TextOverlay version=":
                        onvifCameraHandler.storeHttpReply(
                                "/ISAPI/System/Video/inputs/channels/" + nvrChannel + "/overlays/text/1", content);
                        String text = Helper.fetchXML(content, "<enabled>true</enabled>", "<displayText>");
                        attributes.put(CHANNEL_TEXT_OVERLAY, new StringType(text));
                        break;
                    case "AudioDetection version=":
                        onvifCameraHandler.storeHttpReply("/ISAPI/Smart/AudioDetection/channels/" + nvrChannel + "01",
                                content);
                        if (content.contains("<enabled>true</enabled>")) {
                            attributes.put(CHANNEL_ENABLE_AUDIO_ALARM, OnOffType.ON);
                        } else if (content.contains("<enabled>false</enabled>")) {
                            attributes.put(CHANNEL_ENABLE_AUDIO_ALARM, OnOffType.OFF);
                        }
                        break;
                    case "IOPortStatus version=":
                        if (content.contains("<ioState>active</ioState>")) {
                            attributes.put(CHANNEL_EXTERNAL_ALARM_INPUT, OnOffType.ON);
                        } else if (content.contains("<ioState>inactive</ioState>")) {
                            attributes.put(CHANNEL_EXTERNAL_ALARM_INPUT, OnOffType.OFF);
                        }
                        break;
                    case "FieldDetection version=":
                        onvifCameraHandler.storeHttpReply("/ISAPI/Smart/FieldDetection/" + nvrChannel + "01", content);
                        if (content.contains("<enabled>true</enabled>")) {
                            attributes.put(CHANNEL_ENABLE_FIELD_DETECTION_ALARM, OnOffType.ON);
                        } else if (content.contains("<enabled>false</enabled>")) {
                            attributes.put(CHANNEL_ENABLE_FIELD_DETECTION_ALARM, OnOffType.OFF);
                        }
                        break;
                    case "ResponseStatus version=":
                        ////////////////// External Alarm Input ///////////////
                        if (content.contains(
                                "<requestURL>/ISAPI/System/IO/inputs/" + nvrChannel + "/status</requestURL>")) {
                            // Stops checking the external alarm if camera does not have feature.
                            if (content.contains("<statusString>Invalid Operation</statusString>")) {
                                onvifCameraHandler.lowPriorityRequests.remove(0);
                                onvifCameraHandler.getLog().debug("Stopping checks for alarm inputs as camera appears to be missing this feature.");
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
                            onvifCameraHandler.getLog().debug("Unhandled reply-{}.", content);
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
            attributes.put(CHANNEL_LINE_CROSSING_ALARM, OnOffType.OFF);
            lineCount--;
        }
        if (vmdCount > 1) {
            vmdCount--;
        } else if (vmdCount == 1) {
            attributes.put(CHANNEL_MOTION_ALARM, OnOffType.OFF);
            vmdCount--;
        }
        if (leftCount > 1) {
            leftCount--;
        } else if (leftCount == 1) {
            attributes.put(CHANNEL_ITEM_LEFT, OnOffType.OFF);
            leftCount--;
        }
        if (takenCount > 1) {
            takenCount--;
        } else if (takenCount == 1) {
            attributes.put(CHANNEL_ITEM_TAKEN, OnOffType.OFF);
            takenCount--;
        }
        if (faceCount > 1) {
            faceCount--;
        } else if (faceCount == 1) {
            attributes.put(CHANNEL_FACE_DETECTED, OnOffType.OFF);
            faceCount--;
        }
        if (pirCount > 1) {
            pirCount--;
        } else if (pirCount == 1) {
            attributes.put(CHANNEL_PIR_ALARM, OnOffType.OFF);
            pirCount--;
        }
        if (fieldCount > 1) {
            fieldCount--;
        } else if (fieldCount == 1) {
            attributes.put(CHANNEL_FIELD_DETECTION_ALARM, OnOffType.OFF);
            fieldCount--;
        }
        if (fieldCount == 0 && pirCount == 0 && faceCount == 0 && takenCount == 0 && leftCount == 0 && vmdCount == 0
                && lineCount == 0) {
            onvifCameraHandler.noMotionDetected(CHANNEL_MOTION_ALARM);
        }
    }

    public void hikSendXml(String httpPutURL, String xml) {
        onvifCameraHandler.getLog().trace("Body for PUT:{} is going to be:{}", httpPutURL, xml);
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, new HttpMethod("PUT"), httpPutURL);
        request.headers().set(HttpHeaderNames.HOST, onvifCameraHandler.getCameraEntity().getIp());
        request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        request.headers().add(HttpHeaderNames.CONTENT_TYPE, "application/xml; charset=\"UTF-8\"");
        ByteBuf bbuf = Unpooled.copiedBuffer(xml, StandardCharsets.UTF_8);
        request.headers().set(HttpHeaderNames.CONTENT_LENGTH, bbuf.readableBytes());
        request.content().clear().writeBytes(bbuf);
        onvifCameraHandler.sendHttpPUT(httpPutURL, request);
    }

    public void hikChangeSetting(String httpGetPutURL, String removeElement, String replaceRemovedElementWith) {
        ChannelTracking localTracker = onvifCameraHandler.channelTrackingMap.get(httpGetPutURL);
        if (localTracker == null) {
            onvifCameraHandler.sendHttpGET(httpGetPutURL);
            onvifCameraHandler.getLog().debug(
                    "Did not have a reply stored before hikChangeSetting was run, try again shortly as a reply has just been requested.");
            return;
        }
        String body = localTracker.getReply();
        if (body.isEmpty()) {
            onvifCameraHandler.getLog().debug(
                    "Did not have a reply stored before hikChangeSetting was run, try again shortly as a reply has just been requested.");
            onvifCameraHandler.sendHttpGET(httpGetPutURL);
        } else {
            onvifCameraHandler.getLog().trace("An OLD reply from the camera was:{}", body);
            if (body.contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")) {
                body = body.substring("<?xml version=\"1.0\" encoding=\"UTF-8\"?>".length());
            }
            int elementIndexStart = body.indexOf("<" + removeElement + ">");
            int elementIndexEnd = body.indexOf("</" + removeElement + ">");
            body = body.substring(0, elementIndexStart) + replaceRemovedElementWith
                    + body.substring(elementIndexEnd + removeElement.length() + 3, body.length());
            onvifCameraHandler.getLog().trace("Body for this PUT is going to be:{}", body);
            localTracker.setReply(body);
            FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, new HttpMethod("PUT"),
                    httpGetPutURL);
            request.headers().set(HttpHeaderNames.HOST, onvifCameraHandler.getCameraEntity().getIp());
            request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
            request.headers().add(HttpHeaderNames.CONTENT_TYPE, "application/xml; charset=\"UTF-8\"");
            ByteBuf bbuf = Unpooled.copiedBuffer(body, StandardCharsets.UTF_8);
            request.headers().set(HttpHeaderNames.CONTENT_LENGTH, bbuf.readableBytes());
            request.content().clear().writeBytes(bbuf);
            onvifCameraHandler.sendHttpPUT(httpGetPutURL, request);
        }
    }

    @UICameraActionGetter(CHANNEL_TEXT_OVERLAY)
    public State getTextOverlay() {
        return getState(CHANNEL_TEXT_OVERLAY);
    }

    @UICameraAction(name = CHANNEL_TEXT_OVERLAY, icon = "fas fa-paragraph")
    public void setTextOverlay(String command) {
        onvifCameraHandler.getLog().debug("Changing text overlay to {}", command);
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

    @UICameraActionGetter(CHANNEL_ENABLE_EXTERNAL_ALARM_INPUT)
    public State getEnableExternalAlarmInput() {
        return getState(CHANNEL_ENABLE_EXTERNAL_ALARM_INPUT);
    }

    @UICameraAction(name = CHANNEL_ENABLE_EXTERNAL_ALARM_INPUT, icon = "fas fa-external-link-square-alt")
    public void setEnableExternalAlarmInput(boolean on) {
        onvifCameraHandler.getLog().debug("Changing enabled state of the external input 1 to {}", on);
        hikChangeSetting("/ISAPI/System/IO/inputs/" + nvrChannel, "enabled", "<enabled>" + on + "</enabled>");
    }

    @UICameraActionGetter(CHANNEL_TRIGGER_EXTERNAL_ALARM_INPUT)
    public State getTriggerExternalAlarmInput() {
        return getState(CHANNEL_TRIGGER_EXTERNAL_ALARM_INPUT);
    }

    @UICameraAction(name = CHANNEL_TRIGGER_EXTERNAL_ALARM_INPUT, icon = "fas fa-external-link-alt")
    public void setTriggerExternalAlarmInput(boolean on) {
        onvifCameraHandler.getLog().debug("Changing triggering state of the external input 1 to {}", on);
        hikChangeSetting("/ISAPI/System/IO/inputs/" + nvrChannel, "triggering",
                "<triggering>" + (on ? "high" : "low") + "</triggering>");
    }

    @UICameraAction(name = CHANNEL_ENABLE_PIR_ALARM, icon = "fas fa-compress-alt")
    public void enablePirAlarm(boolean on) {
        hikChangeSetting("/ISAPI/WLAlarm/PIR", "enabled", "<enabled>" + on + "</enabled>");
    }

    @UICameraActionGetter(CHANNEL_ENABLE_AUDIO_ALARM)
    public State getEnableAudioAlarm() {
        return getState(CHANNEL_ENABLE_AUDIO_ALARM);
    }

    @UICameraAction(name = CHANNEL_ENABLE_AUDIO_ALARM, icon = "fas fa-volume-mute")
    public void setEnableAudioAlarm(boolean on) {
        hikChangeSetting("/ISAPI/WLAlarm/PIR", "enabled", "<enabled>" + on + "</enabled>");
    }

    @UICameraActionGetter(CHANNEL_ENABLE_LINE_CROSSING_ALARM)
    public State getEnableLineCrossingAlarm() {
        return getState(CHANNEL_ENABLE_LINE_CROSSING_ALARM);
    }

    @UICameraAction(name = CHANNEL_ENABLE_LINE_CROSSING_ALARM, icon = "fas fa-grip-lines-vertical")
    public void setEnableLineCrossingAlarm(boolean on) {
        hikChangeSetting("/ISAPI/Smart/LineDetection/" + nvrChannel + "01", "enabled",
                "<enabled>" + on + "</enabled>");
    }

    @UICameraAction(name = CHANNEL_ENABLE_MOTION_ALARM, icon = "fas fa-running")
    public void enableMotionAlarm(boolean on) {
        hikChangeSetting("/ISAPI/System/Video/inputs/channels/" + nvrChannel + "01/motionDetection",
                "enabled", "<enabled>" + on + "</enabled>");
    }

    @UICameraActionGetter(CHANNEL_ENABLE_FIELD_DETECTION_ALARM)
    public State getEnableFieldDetectionAlarm() {
        return getState(CHANNEL_ENABLE_FIELD_DETECTION_ALARM);
    }

    @UICameraAction(name = CHANNEL_ENABLE_FIELD_DETECTION_ALARM, icon = "fas fa-shield-alt")
    public void setEnableFieldDetectionAlarm(boolean on) {
        hikChangeSetting("/ISAPI/Smart/FieldDetection/" + nvrChannel + "01", "enabled",
                "<enabled>" + on + "</enabled>");
    }

    @UICameraAction(name = CHANNEL_ACTIVATE_ALARM_OUTPUT, icon = "fas fa-bell")
    public void activateAlarmOutput(boolean on) {
        hikSendXml("/ISAPI/System/IO/outputs/" + nvrChannel + "/trigger",
                "<IOPortData version=\"1.0\" xmlns=\"http://www.hikvision.com/ver10/XMLSchema\">\r\n    <outputState>" +
                        (on ? "high" : "low") + "</outputState>\r\n</IOPortData>\r\n");
    }

    public ArrayList<String> getLowPriorityRequests() {
        ArrayList<String> lowPriorityRequests = new ArrayList<>(1);
        lowPriorityRequests.add("/ISAPI/System/IO/inputs/" + nvrChannel + "/status"); // must stay in element 0.
        lowPriorityRequests.add("/ISAPI/System/Video/inputs/channels/" + nvrChannel + "01/motionDetection");
        lowPriorityRequests.add("/ISAPI/Smart/LineDetection/" + nvrChannel + "01");
        lowPriorityRequests.add("/ISAPI/Smart/AudioDetection/channels/" + nvrChannel + "01");
        lowPriorityRequests.add("/ISAPI/System/Video/inputs/channels/" + nvrChannel + "/overlays/text/1");
        lowPriorityRequests.add("/ISAPI/System/IO/inputs/" + nvrChannel);
        return lowPriorityRequests;
    }
}
