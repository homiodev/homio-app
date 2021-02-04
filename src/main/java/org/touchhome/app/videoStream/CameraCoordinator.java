package org.touchhome.app.videoStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.touchhome.app.videoStream.entity.BaseVideoCameraEntity;
import org.touchhome.app.videoStream.handler.BaseCameraHandler;
import org.touchhome.app.videoStream.rtsp.message.sdp.SdpMessage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Component
public class CameraCoordinator {

    @Value("${camera.initialServerPort:9200}")
    private int initialServerPort;

    private Map<String, BaseCameraHandler<? extends BaseVideoCameraEntity>> cameraEntityToCameraHandlerMap = new ConcurrentHashMap<>();

    private Map<String, SdpMessage> rtspUrlToSdpMessage = new ConcurrentHashMap<>();

    public BaseCameraHandler computeBaseCameraHandlerIfAbsent(String key, Function<String, BaseCameraHandler<BaseVideoCameraEntity>> mappingFunction) {
        return cameraEntityToCameraHandlerMap.computeIfAbsent(key, mappingFunction);
    }

    public SdpMessage getSdpMessage(String key) {
        return key == null ? null : rtspUrlToSdpMessage.get(key);
    }

    public void setSdpMessage(String key, SdpMessage sdpMessage) {
        rtspUrlToSdpMessage.put(key, sdpMessage);
    }

    public int findNextServerPort() {
        int port = initialServerPort;
        while (isPortBusy(port)) {
            port++;
        }
        return port;
    }

    private boolean isPortBusy(int port) {
        return cameraEntityToCameraHandlerMap.values().stream().anyMatch(c -> c.getServerPort() == port);
    }

    public void removeSpdMessage(String key) {
        rtspUrlToSdpMessage.remove(key);
    }

    public BaseCameraHandler removeCameraHandler(String key) {
        return cameraEntityToCameraHandlerMap.remove(key);
    }
}
