package org.touchhome.app.service.video;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.touchhome.app.model.entity.CommonVideoStreamEntity;
import org.touchhome.app.service.RtspStreamScanner;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.state.State;
import org.touchhome.bundle.api.state.StringType;
import org.touchhome.bundle.api.video.BaseVideoService;
import org.touchhome.bundle.api.video.BaseVideoStreamServerHandler;
import org.touchhome.bundle.camera.rtsp.message.sdp.SdpMessage;
import org.touchhome.common.exception.ServerException;

public class CommonVideoService extends BaseVideoService<CommonVideoStreamEntity> {

  private VideoSourceType videoSourceType;

  public CommonVideoService(EntityContext entityContext, CommonVideoStreamEntity entity) {
    super(entity, entityContext, true);
  }

  @Override
  public void testService() {

  }

  @Override
  public void testVideoOnline() {
    videoSourceType = VideoSourceType.UNKNOWN;
    String ieeeAddress = getEntity().getIeeeAddress();
    if (ieeeAddress != null) {
      if (ieeeAddress.endsWith("m3u8")) {
        videoSourceType = VideoSourceType.HLS;
      } else if (ieeeAddress.startsWith("rtsp://")) {
        videoSourceType = VideoSourceType.RTSP;
      }
    }
  }

  @Override
  protected void initialize0() {
    if (getEntity().getIeeeAddress() == null) {
      throw new ServerException("Url must be not null in entity: " + getEntity());
    }
    super.initialize0();
  }

  @Override
  public String getRtspUri(String profile) {
    return getEntity().getIeeeAddress();
  }

  @Override
  public String getFFMPEGInputOptions(String profile) {
    return videoSourceType.ffmpegInputOptions;
  }

  @Override
  protected BaseVideoStreamServerHandler createVideoStreamServerHandler() {
    return new RtspCameraStreamHandler(this);
  }

  @Override
  protected void streamServerStarted() {

  }

  @Override
  public Map<String, State> getAttributes() {
    Map<String, State> map = new HashMap<>(super.getAttributes());
    SdpMessage sdpMessage = RtspStreamScanner.rtspUrlToSdpMessage.get(getEntity().getIeeeAddress());
    if (sdpMessage != null) {
      map.put("RTSP Description Message", new StringType(sdpMessage.toString()));
    }
    return map;
  }

  @RequiredArgsConstructor
  public enum VideoSourceType {
    HLS(""),
    RTSP("-rtsp_transport tcp -timeout " + TimeUnit.SECONDS.toMicros(10)),
    UNKNOWN("");

    private final String ffmpegInputOptions;
  }

  private class RtspCameraStreamHandler extends BaseVideoStreamServerHandler<CommonVideoService> {

    public RtspCameraStreamHandler(CommonVideoService service) {
      super(service);
    }

    @Override
    protected void handleLastHttpContent(byte[] incomingJpeg) {
    }

    @Override
    protected boolean streamServerReceivedPostHandler(HttpRequest httpRequest) {
      return false;
    }

    @Override
    protected void handlerChildRemoved(ChannelHandlerContext ctx) {
    }
  }
}
