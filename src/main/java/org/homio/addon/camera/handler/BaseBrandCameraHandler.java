package org.homio.addon.camera.handler;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import org.homio.api.Context;
import org.homio.api.ui.field.action.v1.UIInputBuilder;

public interface BaseBrandCameraHandler {

    boolean isSupportOnvifEvents();

    void handleSetURL(ChannelPipeline pipeline, String httpRequestURL);

    void assembleActions(UIInputBuilder uiInputBuilder);

    default ChannelHandler asBootstrapHandler() {
        throw new RuntimeException("Unsupported bootstrap handler");
    }

    void pollCameraRunnable();

    void postInitializeCamera(Context context);

    String getUrlToKeepOpenForIdleStateEvent();

    default boolean isHasMotionAlarm() {
        return false;
    }

    default void setMotionAlarmThreshold(int threshold) {
        throw new IllegalStateException("setMotionAlarmThreshold must be implemented in sub class if isHasMotionAlarm is true");
    }

    default boolean isHasAudioAlarm() {
        return false;
    }

    default void setAudioAlarmThreshold(int threshold) {
        throw new IllegalStateException("setAudioAlarmThreshold must be implemented in sub class if isHasAudioAlarm is true");
    }
}
