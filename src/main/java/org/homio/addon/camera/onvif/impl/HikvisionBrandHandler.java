package org.homio.addon.camera.onvif.impl;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.homio.addon.camera.onvif.brand.BaseOnvifCameraBrandHandler;
import org.homio.addon.camera.onvif.brand.BrandCameraHasMotionAlarm;
import org.homio.addon.camera.onvif.util.ChannelTracking;
import org.homio.addon.camera.onvif.util.Helper;
import org.homio.addon.camera.onvif.util.IpCameraBindingConstants;
import org.homio.addon.camera.service.OnvifCameraService;
import org.homio.addon.camera.ui.UIVideoAction;
import org.homio.addon.camera.ui.UIVideoActionGetter;
import org.homio.api.EntityContext;
import org.homio.api.state.OnOffType;
import org.homio.api.state.State;
import org.homio.api.state.StringType;
import org.springframework.http.MediaType;

/**
 * responsible for handling commands, which are sent to one of the channels.
 */
@Log4j2
@CameraBrandHandler("Hikvision")
public class HikvisionBrandHandler extends BaseOnvifCameraBrandHandler implements BrandCameraHasMotionAlarm {

  private int lineCount, vmdCount, leftCount, takenCount, faceCount, pirCount, fieldCount;
  private boolean checkAlarmInput;

  public HikvisionBrandHandler(OnvifCameraService service) {
    super(service);
  }

  // This handles the incoming http replies back from the camera.
  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) {
    if (msg == null || ctx == null) {
      return;
    }
    OnvifCameraService service = getService();
    try {
      int debounce = 3;
      String content = msg.toString();
      log.debug("[{}]: HTTP Result back from camera is \t:{}:", entityID, content);
      if (content.contains("--boundary")) {// Alarm checking goes in here//
        if (content.contains("<EventNotificationAlert version=\"")) {
          if (content.contains("hannelID>" + nvrChannel + "</")) {// some camera use c or <dynChannelID>
            if (content.contains("<eventType>linedetection</eventType>")) {
                service.motionDetected(true, IpCameraBindingConstants.CHANNEL_LINE_CROSSING_ALARM);
                lineCount = debounce;
            }
            if (content.contains("<eventType>fielddetection</eventType>")) {
                service.motionDetected(true, IpCameraBindingConstants.CHANNEL_FIELD_DETECTION_ALARM);
                fieldCount = debounce;
            }
            if (content.contains("<eventType>VMD</eventType>")) {
                service.motionDetected(true, IpCameraBindingConstants.CHANNEL_MOTION_ALARM);
                vmdCount = debounce;
            }
            if (content.contains("<eventType>facedetection</eventType>")) {
                setAttribute(IpCameraBindingConstants.CHANNEL_FACE_DETECTED, OnOffType.ON);
                faceCount = debounce;
            }
            if (content.contains("<eventType>unattendedBaggage</eventType>")) {
                setAttribute(IpCameraBindingConstants.CHANNEL_ITEM_LEFT, OnOffType.ON);
                leftCount = debounce;
            }
            if (content.contains("<eventType>attendedBaggage</eventType>")) {
                setAttribute(IpCameraBindingConstants.CHANNEL_ITEM_TAKEN, OnOffType.ON);
                takenCount = debounce;
            }
            if (content.contains("<eventType>PIR</eventType>")) {
                service.motionDetected(true, IpCameraBindingConstants.CHANNEL_PIR_ALARM);
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
            service.storeHttpReply(
                "/ISAPI/System/Video/inputs/channels/" + nvrChannel + "01/motionDetection", content);
            if (content.contains("<enabled>true</enabled>")) {
                setAttribute(IpCameraBindingConstants.CHANNEL_ENABLE_MOTION_ALARM, OnOffType.ON);
            } else if (content.contains("<enabled>false</enabled>")) {
                setAttribute(IpCameraBindingConstants.CHANNEL_ENABLE_MOTION_ALARM, OnOffType.OFF);
            }
            break;
          case "IOInputPort version=":
            service.storeHttpReply("/ISAPI/System/IO/inputs/" + nvrChannel, content);
            if (content.contains("<enabled>true</enabled>")) {
                setAttribute(IpCameraBindingConstants.CHANNEL_ENABLE_EXTERNAL_ALARM_INPUT, OnOffType.ON);
            } else if (content.contains("<enabled>false</enabled>")) {
                setAttribute(IpCameraBindingConstants.CHANNEL_ENABLE_EXTERNAL_ALARM_INPUT, OnOffType.OFF);
            }
            if (content.contains("<triggering>low</triggering>")) {
                setAttribute(IpCameraBindingConstants.CHANNEL_TRIGGER_EXTERNAL_ALARM_INPUT, OnOffType.OFF);
            } else if (content.contains("<triggering>high</triggering>")) {
                setAttribute(IpCameraBindingConstants.CHANNEL_TRIGGER_EXTERNAL_ALARM_INPUT, OnOffType.ON);
            }
            break;
          case "LineDetection":
            service.storeHttpReply("/ISAPI/Smart/LineDetection/" + nvrChannel + "01", content);
            if (content.contains("<enabled>true</enabled>")) {
                setAttribute(IpCameraBindingConstants.CHANNEL_ENABLE_LINE_CROSSING_ALARM, OnOffType.ON);
            } else if (content.contains("<enabled>false</enabled>")) {
                setAttribute(IpCameraBindingConstants.CHANNEL_ENABLE_LINE_CROSSING_ALARM, OnOffType.OFF);
            }
            break;
          case "TextOverlay version=":
            service.storeHttpReply(
                "/ISAPI/System/Video/inputs/channels/" + nvrChannel + "/overlays/text/1", content);
            String text = Helper.fetchXML(content, "<enabled>true</enabled>", "<displayText>");
              setAttribute(IpCameraBindingConstants.CHANNEL_TEXT_OVERLAY, new StringType(text));
            break;
          case "AudioDetection version=":
            service.storeHttpReply("/ISAPI/Smart/AudioDetection/channels/" + nvrChannel + "01",
                content);
            if (content.contains("<enabled>true</enabled>")) {
                setAttribute(IpCameraBindingConstants.CHANNEL_ENABLE_AUDIO_ALARM, OnOffType.ON);
            } else if (content.contains("<enabled>false</enabled>")) {
                setAttribute(IpCameraBindingConstants.CHANNEL_ENABLE_AUDIO_ALARM, OnOffType.OFF);
            }
            break;
          case "IOPortStatus version=":
            if (content.contains("<ioState>active</ioState>")) {
                setAttribute(IpCameraBindingConstants.CHANNEL_EXTERNAL_ALARM_INPUT, OnOffType.ON);
            } else if (content.contains("<ioState>inactive</ioState>")) {
                setAttribute(IpCameraBindingConstants.CHANNEL_EXTERNAL_ALARM_INPUT, OnOffType.OFF);
            }
            break;
          case "FieldDetection version=":
            service.storeHttpReply("/ISAPI/Smart/FieldDetection/" + nvrChannel + "01", content);
            if (content.contains("<enabled>true</enabled>")) {
                setAttribute(IpCameraBindingConstants.CHANNEL_ENABLE_FIELD_DETECTION_ALARM, OnOffType.ON);
            } else if (content.contains("<enabled>false</enabled>")) {
                setAttribute(IpCameraBindingConstants.CHANNEL_ENABLE_FIELD_DETECTION_ALARM, OnOffType.OFF);
            }
            break;
          case "ResponseStatus version=":
            ////////////////// External Alarm Input ///////////////
            if (content.contains("<requestURL>/ISAPI/System/IO/inputs/" + nvrChannel + "/status</requestURL>")) {
              // Stops checking the external alarm if camera does not have feature.
              if (content.contains("<statusString>Invalid Operation</statusString>")) {
                checkAlarmInput = false;
                log.debug("[{}]: Stopping checks for alarm inputs as camera appears to be missing this feature.", entityID);
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
              log.debug("[{}]: Unhandled reply-{}.", entityID, content);
            }
            break;
        }
      }
    } finally {
      ReferenceCountUtil.release(msg);
    }
  }

    @UIVideoActionGetter(IpCameraBindingConstants.CHANNEL_TEXT_OVERLAY)
  public State getTextOverlay() {
        return getAttribute(IpCameraBindingConstants.CHANNEL_TEXT_OVERLAY);
  }

  public void hikSendXml(String httpPutURL, String xml) {
    log.debug("[{}]: Body for PUT:{} is going to be:{}", entityID, httpPutURL, xml);
    FullHttpRequest fullHttpRequest = buildFullHttpRequest(httpPutURL, xml, HttpMethod.PUT, MediaType.APPLICATION_XML);
    getService().sendHttpPUT(httpPutURL, fullHttpRequest);
  }

  public void hikChangeSetting(String httpGetPutURL, String removeElement, String replaceRemovedElementWith) {
    OnvifCameraService service = getService();

    ChannelTracking localTracker = service.channelTrackingMap.get(httpGetPutURL);
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
          + body.substring(elementIndexEnd + removeElement.length() + 3, body.length());
      log.debug("[{}]: Body for this PUT is going to be:{}", entityID, body);
      localTracker.setReply(body);
      FullHttpRequest fullHttpRequest = buildFullHttpRequest(httpGetPutURL, body, HttpMethod.PUT, MediaType.APPLICATION_XML);
      service.sendHttpPUT(httpGetPutURL, fullHttpRequest);
    }
  }

    @UIVideoAction(name = IpCameraBindingConstants.CHANNEL_TEXT_OVERLAY, order = 100, icon = "fas fa-paragraph")
  public void setTextOverlay(String command) {
    log.debug("[{}]: Changing text overlay to {}", entityID, command);
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

    @UIVideoActionGetter(IpCameraBindingConstants.CHANNEL_ENABLE_EXTERNAL_ALARM_INPUT)
  public State getEnableExternalAlarmInput() {
        return getAttribute(IpCameraBindingConstants.CHANNEL_ENABLE_EXTERNAL_ALARM_INPUT);
  }

    @UIVideoAction(name = IpCameraBindingConstants.CHANNEL_ENABLE_EXTERNAL_ALARM_INPUT, order = 250, icon = "fas fa-external-link-square-alt")
  public void setEnableExternalAlarmInput(boolean on) {
    log.debug("[{}]: Changing enabled state of the external input 1 to {}", entityID, on);
    hikChangeSetting("/ISAPI/System/IO/inputs/" + nvrChannel, "enabled", "<enabled>" + on + "</enabled>");
  }

    @UIVideoActionGetter(IpCameraBindingConstants.CHANNEL_TRIGGER_EXTERNAL_ALARM_INPUT)
  public State getTriggerExternalAlarmInput() {
        return getAttribute(IpCameraBindingConstants.CHANNEL_TRIGGER_EXTERNAL_ALARM_INPUT);
  }

    @UIVideoAction(name = IpCameraBindingConstants.CHANNEL_TRIGGER_EXTERNAL_ALARM_INPUT, order = 300, icon = "fas fa-external-link-alt")
  public void setTriggerExternalAlarmInput(boolean on) {
    log.debug("[{}]: Changing triggering state of the external input 1 to {}", entityID, on);
    hikChangeSetting("/ISAPI/System/IO/inputs/" + nvrChannel, "triggering",
        "<triggering>" + (on ? "high" : "low") + "</triggering>");
  }

    @UIVideoAction(name = IpCameraBindingConstants.CHANNEL_ENABLE_PIR_ALARM, order = 120, icon = "fas fa-compress-alt")
  public void enablePirAlarm(boolean on) {
    hikChangeSetting("/ISAPI/WLAlarm/PIR", "enabled", "<enabled>" + on + "</enabled>");
  }

    @UIVideoActionGetter(IpCameraBindingConstants.CHANNEL_ENABLE_LINE_CROSSING_ALARM)
  public State getEnableLineCrossingAlarm() {
        return getAttribute(IpCameraBindingConstants.CHANNEL_ENABLE_LINE_CROSSING_ALARM);
  }

  @Override
  public void setMotionAlarmThreshold(int threshold) {
    hikChangeSetting("/ISAPI/WLAlarm/PIR", "enabled", "<enabled>" + (threshold > 0) + "</enabled>");
  }

    @UIVideoAction(name = IpCameraBindingConstants.CHANNEL_ENABLE_LINE_CROSSING_ALARM, order = 150, icon = "fas fa-grip-lines-vertical")
  public void setEnableLineCrossingAlarm(boolean on) {
    hikChangeSetting("/ISAPI/Smart/LineDetection/" + nvrChannel + "01", "enabled",
        "<enabled>" + on + "</enabled>");
  }

    @UIVideoAction(name = IpCameraBindingConstants.CHANNEL_ENABLE_MOTION_ALARM, order = 14, icon = "fas fa-running")
  public void enableMotionAlarm(boolean on) {
    hikChangeSetting("/ISAPI/System/Video/inputs/channels/" + nvrChannel + "01/motionDetection",
        "enabled", "<enabled>" + on + "</enabled>");
  }

    @UIVideoActionGetter(IpCameraBindingConstants.CHANNEL_ENABLE_FIELD_DETECTION_ALARM)
  public State getEnableFieldDetectionAlarm() {
        return getAttribute(IpCameraBindingConstants.CHANNEL_ENABLE_FIELD_DETECTION_ALARM);
  }

    @UIVideoAction(name = IpCameraBindingConstants.CHANNEL_ENABLE_FIELD_DETECTION_ALARM, order = 140, icon = "fas fa-shield-alt")
  public void setEnableFieldDetectionAlarm(boolean on) {
    hikChangeSetting("/ISAPI/Smart/FieldDetection/" + nvrChannel + "01", "enabled",
        "<enabled>" + on + "</enabled>");
  }

    @UIVideoAction(name = IpCameraBindingConstants.CHANNEL_ACTIVATE_ALARM_OUTPUT, order = 45, icon = "fas fa-bell")
  public void activateAlarmOutput(boolean on) {
    hikSendXml("/ISAPI/System/IO/outputs/" + nvrChannel + "/trigger",
        "<IOPortData version=\"1.0\" xmlns=\"http://www.hikvision.com/ver10/XMLSchema\">\r\n    <outputState>" +
            (on ? "high" : "low") + "</outputState>\r\n</IOPortData>\r\n");
  }

  // This does debouncing of the alarms
  void countDown() {
    if (lineCount > 1) {
      lineCount--;
    } else if (lineCount == 1) {
        setAttribute(IpCameraBindingConstants.CHANNEL_LINE_CROSSING_ALARM, OnOffType.OFF);
        lineCount--;
    }
    if (vmdCount > 1) {
      vmdCount--;
    } else if (vmdCount == 1) {
        setAttribute(IpCameraBindingConstants.CHANNEL_MOTION_ALARM, OnOffType.OFF);
        vmdCount--;
    }
    if (leftCount > 1) {
      leftCount--;
    } else if (leftCount == 1) {
        setAttribute(IpCameraBindingConstants.CHANNEL_ITEM_LEFT, OnOffType.OFF);
        leftCount--;
    }
    if (takenCount > 1) {
      takenCount--;
    } else if (takenCount == 1) {
        setAttribute(IpCameraBindingConstants.CHANNEL_ITEM_TAKEN, OnOffType.OFF);
        takenCount--;
    }
    if (faceCount > 1) {
      faceCount--;
    } else if (faceCount == 1) {
        setAttribute(IpCameraBindingConstants.CHANNEL_FACE_DETECTED, OnOffType.OFF);
        faceCount--;
    }
    if (pirCount > 1) {
      pirCount--;
    } else if (pirCount == 1) {
        setAttribute(IpCameraBindingConstants.CHANNEL_PIR_ALARM, OnOffType.OFF);
        pirCount--;
    }
    if (fieldCount > 1) {
      fieldCount--;
    } else if (fieldCount == 1) {
        setAttribute(IpCameraBindingConstants.CHANNEL_FIELD_DETECTION_ALARM, OnOffType.OFF);
        fieldCount--;
    }
    if (fieldCount == 0 && pirCount == 0 && faceCount == 0 && takenCount == 0 && leftCount == 0 && vmdCount == 0
        && lineCount == 0) {
        getService().motionDetected(false, IpCameraBindingConstants.CHANNEL_MOTION_ALARM);
    }
  }

  @Override
  public void runOncePerMinute(EntityContext entityContext) {
    OnvifCameraService service = getService();
    if (checkAlarmInput) {
      service.sendHttpGET("/ISAPI/System/IO/inputs/" + nvrChannel + "/status"); // must stay in element 0.
    }
    service.sendHttpGET("/ISAPI/System/Video/inputs/channels/" + nvrChannel + "01/motionDetection");
    service.sendHttpGET("/ISAPI/Smart/LineDetection/" + nvrChannel + "01");
    service.sendHttpGET("/ISAPI/Smart/AudioDetection/channels/" + nvrChannel + "01");
    service.sendHttpGET("/ISAPI/System/Video/inputs/channels/" + nvrChannel + "/overlays/text/1");
    service.sendHttpGET("/ISAPI/System/IO/inputs/" + nvrChannel);
  }

  @Override
  public void pollCameraRunnable() {
    OnvifCameraService service = getService();
    if (service.streamIsStopped("/ISAPI/Event/notification/alertStream")) {
      log.info("[{}]: The alarm stream was not running for camera {}, re-starting it now",
          entityID, getEntity().getIp());
      service.sendHttpGET("/ISAPI/Event/notification/alertStream");
    }
  }

  @Override
  public void initialize(EntityContext entityContext) {
    OnvifCameraService service = getService();

    if (StringUtils.isEmpty(service.getMjpegUri())) {
      service.setMjpegUri("/ISAPI/Streaming/channels/" + getEntity().getNvrChannel() + "02" + "/httppreview");
    }
    if (StringUtils.isEmpty(service.getSnapshotUri())) {
      service.setSnapshotUri("/ISAPI/Streaming/channels/" + getEntity().getNvrChannel() + "01" + "/picture");
    }
  }

  @Override
  public String getUrlToKeepOpenForIdleStateEvent() {
    return "/ISAPI/Event/notification/alertStream";
  }
}
