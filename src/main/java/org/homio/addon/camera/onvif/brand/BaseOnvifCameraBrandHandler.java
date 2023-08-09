package org.homio.addon.camera.onvif.brand;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import java.nio.charset.StandardCharsets;
import lombok.Getter;
import org.homio.addon.camera.entity.OnvifCameraEntity;
import org.homio.addon.camera.entity.VideoActionsContext;
import org.homio.addon.camera.handler.BaseBrandCameraHandler;
import org.homio.addon.camera.service.OnvifCameraService;
import org.homio.addon.camera.ui.CameraActionBuilder;
import org.homio.api.EntityContext;
import org.homio.api.state.State;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.springframework.http.MediaType;

public abstract class BaseOnvifCameraBrandHandler extends ChannelDuplexHandler implements VideoActionsContext, BaseBrandCameraHandler {

  protected final int nvrChannel;
  protected final String username;
  protected final String password;
  protected final String ip;
  protected final String entityID;
  @Getter
  private final OnvifCameraService service;

  public BaseOnvifCameraBrandHandler(OnvifCameraService service) {
    this.service = service;

    OnvifCameraEntity entity = service.getEntity();
    this.entityID = entity.getEntityID();
    this.nvrChannel = entity.getNvrChannel();
    this.username = entity.getUser();
    this.password = entity.getPassword().asString();
    this.ip = entity.getIp();
  }

  public EntityContext getEntityContext() {
    return service.getEntityContext();
  }

  @Override
  public OnvifCameraEntity getEntity() {
    return service.getEntity();
  }

  @Override
  public boolean isSharable() {
    return true;
  }

  public State getAttribute(String name) {
    return service.getAttributes().getOrDefault(name, null);
  }

  public int boolToInt(boolean on) {
    return on ? 1 : 0;
  }

  public void assembleActions(UIInputBuilder uiInputBuilder) {
    CameraActionBuilder.assembleActions(this, uiInputBuilder);
  }

  protected void setAttribute(String key, State state) {
    service.setAttribute(key, state);
  }

  protected void setAttributeRequest(String key, State state) {
    service.setAttributeRequest(key, state);
  }

  protected State getAttributeRequest(String key) {
    return service.getRequestAttributes().get(key);
  }

  public void pollCameraRunnable() {

  }

  public void initialize(EntityContext entityContext) {

  }

  public void runOncePerMinute(EntityContext entityContext) {

  }

  public String getUrlToKeepOpenForIdleStateEvent() {
    return "";
  }

  public void handleSetURL(ChannelPipeline pipeline, String httpRequestURL) {

  }

  protected FullHttpRequest buildFullHttpRequest(String httpPutURL, String xml, HttpMethod httpMethod, MediaType mediaType) {
    FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, new HttpMethod(httpMethod.name()), httpPutURL);
    request.headers().set(HttpHeaderNames.HOST, getEntity().getIp());
    request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
    request.headers().add(HttpHeaderNames.CONTENT_TYPE, mediaType.toString());
    ByteBuf bbuf = Unpooled.copiedBuffer(xml, StandardCharsets.UTF_8);
    request.headers().set(HttpHeaderNames.CONTENT_LENGTH, bbuf.readableBytes());
    request.content().clear().writeBytes(bbuf);
    return request;
  }

  public String updateURL(String url) {
    return url;
  }

  public boolean isSupportOnvifEvents() {
    return false;
  }

  @Override
  public ChannelHandler asBootstrapHandler() {
    return this;
  }
}
