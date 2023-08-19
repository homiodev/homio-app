package org.homio.addon.camera.handler;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import org.homio.api.EntityContext;
import org.homio.api.ui.field.action.v1.UIInputBuilder;

import java.util.function.Consumer;
import java.util.function.Supplier;

public interface BaseBrandCameraHandler {

    boolean isSupportOnvifEvents();

    void handleSetURL(ChannelPipeline pipeline, String httpRequestURL);

    void assembleActions(UIInputBuilder uiInputBuilder);

    default ChannelHandler asBootstrapHandler() {
        throw new RuntimeException("Unsupported bootstrap handler");
    }

    void pollCameraRunnable();

    void postInitializeCamera(EntityContext entityContext);

    String getUrlToKeepOpenForIdleStateEvent();

    default Consumer<Boolean> getIRLedHandler() {
        return null;
    }

    default Supplier<Boolean> getIrLedValueHandler() {
        return null;
    }
}
