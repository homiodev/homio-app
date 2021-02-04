package org.touchhome.app.videoStream.onvif.util;

import io.netty.channel.ChannelDuplexHandler;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.touchhome.app.videoStream.entity.OnvifCameraEntity;
import org.touchhome.app.videoStream.ui.CameraAction;
import org.touchhome.app.videoStream.handler.OnvifCameraHandler;
import org.touchhome.bundle.api.measure.State;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Log4j2
public abstract class CameraTypeHandler extends ChannelDuplexHandler {
    private static Map<String, List<CameraAction>> actions = new HashMap<>();

    protected final OnvifCameraHandler onvifCameraHandler;
    protected final int nvrChannel;

    protected final String username;
    protected final String password;

    @Getter
    protected final Map<String, State> attributes = new ConcurrentHashMap<>();

    public CameraTypeHandler(OnvifCameraHandler onvifCameraHandler) {
        this.onvifCameraHandler = onvifCameraHandler;
        this.nvrChannel = 0;
        this.username = "";
        this.password = "";
    }

    protected State getState(String name) {
        return attributes.getOrDefault(name, null);
    }

    public int boolToInt(boolean on) {
        return on ? 1 : 0;
    }

    public CameraTypeHandler(OnvifCameraEntity onvifCameraEntity) {
        this.onvifCameraHandler = (OnvifCameraHandler) onvifCameraEntity.getCameraHandler();
        this.nvrChannel = onvifCameraEntity.getNvrChannel();
        this.username = onvifCameraEntity.getUser();
        this.password = onvifCameraEntity.getPassword();
    }

    public List<CameraAction> getCameraActions() {
        return actions.computeIfAbsent(getClass().getSimpleName(), key -> CameraAction.assemble(this, this));
    }

    public abstract List<String> getLowPriorityRequests();
}
