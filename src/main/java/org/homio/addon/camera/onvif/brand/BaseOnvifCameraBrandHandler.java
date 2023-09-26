package org.homio.addon.camera.onvif.brand;

import static org.homio.addon.camera.CameraConstants.ENDPOINT_AUDIO_THRESHOLD;
import static org.homio.addon.camera.CameraConstants.ENDPOINT_MOTION_THRESHOLD;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.ReferenceCountUtil;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.homio.addon.camera.entity.OnvifCameraEntity;
import org.homio.addon.camera.entity.CameraActionsContext;
import org.homio.addon.camera.handler.BaseBrandCameraHandler;
import org.homio.addon.camera.service.OnvifCameraService;
import org.homio.addon.camera.service.CameraDeviceEndpoint;
import org.homio.addon.camera.ui.CameraActionBuilder;
import org.homio.api.EntityContext;
import org.homio.api.state.State;
import org.homio.api.ui.field.action.HasDynamicUIFields;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.MediaType;

@NoArgsConstructor
public abstract class BaseOnvifCameraBrandHandler extends ChannelDuplexHandler
    implements
    CameraActionsContext,
    BaseBrandCameraHandler,
    HasDynamicUIFields {

    protected final Logger log = LogManager.getLogger();

    protected int nvrChannel;
    protected String username;
    protected String password;
    protected String ip;
    protected String entityID;
    protected @Getter OnvifCameraService service;

    public BaseOnvifCameraBrandHandler(OnvifCameraService service) {
        this.service = service;

        OnvifCameraEntity entity = getEntity();
        this.entityID = entity.getEntityID();
        this.nvrChannel = entity.getNvrChannel();
        this.username = entity.getUser();
        this.password = entity.getPassword().asString();
        this.ip = entity.getIp();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg != null && !msg.toString().isEmpty()) {
            log.debug("[{}]: Camera response: {}", entityID, msg);
        }
        ReferenceCountUtil.release(msg);
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

    public @Nullable String getSnapshotUri() {
        return null;
    }

    public @Nullable String getMjpegUri() {
        return null;
    }

    public abstract void onCameraConnected();

    @Override
    public void assembleUIFields(@NotNull UIFieldBuilder uiFieldBuilder) {

    }

    protected void setAttribute(@NotNull String key, @NotNull State state) {
        service.setAttribute(key, state);
        switch (key) {
            case ENDPOINT_MOTION_THRESHOLD -> getEntityContext().updateDelayed(getEntity(), entity -> {
                entity.setMotionThreshold(state.intValue());
            });
            case ENDPOINT_AUDIO_THRESHOLD -> getEntityContext().updateDelayed(getEntity(), entity -> {
                entity.setAudioThreshold(state.intValue());
            });
        }
    }

    protected void setAttributeRequest(String key, State state) {
        service.setAttributeRequest(key, state);
    }

    protected State getAttributeRequest(String key) {
        return service.getRequestAttributes().get(key);
    }

    public void pollCameraRunnable() {
    }

    public void postInitializeCamera(EntityContext entityContext) {

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

    public Optional<CameraDeviceEndpoint> getEndpoint(String endpointID) {
        return Optional.ofNullable(service.getEndpoints().get(endpointID));
    }

    public CameraDeviceEndpoint getEndpointRequire(String endpointID) {
        return Optional.ofNullable(service.getEndpoints().get(endpointID))
                       .orElseThrow(() -> new IllegalStateException("Unable to find camera endpoint: " + endpointID));
    }

    public boolean setEndpointVisible(String endpointID, boolean visible) {
        CameraDeviceEndpoint endpoint = getEndpointRequire(endpointID);
        if (endpoint.isVisibleEndpoint() != visible) {
            endpoint.setVisibleEndpoint(visible);
            return true;
        }
        return false;
    }
}
