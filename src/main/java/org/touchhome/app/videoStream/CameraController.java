package org.touchhome.app.videoStream;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.touchhome.app.videoStream.entity.BaseVideoCameraEntity;
import org.touchhome.app.videoStream.entity.RtspVideoStreamEntity;
import org.touchhome.app.videoStream.handler.BaseCameraHandler;
import org.touchhome.app.videoStream.scanner.RtspStreamScanner;
import org.touchhome.app.videoStream.ui.RestartHandlerOnChange;
import org.touchhome.bundle.api.EntityContext;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Log4j2
@RestController
@RequestMapping("/rest/camera")
@RequiredArgsConstructor
public class CameraController {

    private final EntityContext entityContext;
    private final CameraCoordinator cameraCoordinator;
    private final RtspStreamScanner rtspStreamScanner;

    public void init() {
        // listen if camera entity removed but run
        entityContext.event().addEntityRemovedListener(BaseVideoCameraEntity.class, "camera-remove-listener", cameraEntity -> {
            cameraCoordinator.removeSpdMessage(cameraEntity.getIeeeAddress());
            BaseCameraHandler handler = cameraCoordinator.removeCameraHandler(cameraEntity.getEntityID());
            if (handler != null) {
                handler.dispose();
            }
        });
        // lister start/stop status, changes
        entityContext.event().addEntityUpdateListener(BaseVideoCameraEntity.class, "camera-change-listener", (cameraEntity, oldCameraEntity) -> {
            BaseCameraHandler cameraHandler = cameraEntity.getCameraHandler();
            if (cameraEntity.isStart() && !cameraHandler.isStarted()) {
                cameraHandler.initialize(cameraEntity);
            } else if (!cameraEntity.isStart() && cameraHandler.isStarted()) {
                cameraHandler.dispose();
            } else if (detectIfRequireRestartHandler(oldCameraEntity, cameraEntity)) {
                cameraHandler.restart("Restart camera handler", cameraEntity);
            } else {
                cameraHandler.setCameraEntity(cameraEntity); // to avoid optimistic lock
            }
        });

        for (BaseVideoCameraEntity cameraEntity : entityContext.findAll(BaseVideoCameraEntity.class)) {
            if (cameraEntity.isStart()) {
                cameraEntity.getCameraHandler().initialize(cameraEntity);
            }
        }

        // send rtsp 'DESCRIPTION' once per hour
        entityContext.bgp().schedule("check-rtsp-urls", 60, TimeUnit.MINUTES, () -> {
            rtspStreamScanner.scan(entityContext.findAll(RtspVideoStreamEntity.class));
        }, false);
    }

    @SneakyThrows
    private static boolean detectIfRequireRestartHandler(Object oldCameraEntity, Object cameraEntity) {
        if (oldCameraEntity == null) { // in case if updated by delayed
            return false;
        }
        Method[] methods = MethodUtils.getMethodsWithAnnotation(cameraEntity.getClass(), RestartHandlerOnChange.class, true, false);
        for (Method method : methods) {
            Object newValue = MethodUtils.invokeMethod(cameraEntity, method.getName());
            Object oldValue = MethodUtils.invokeMethod(oldCameraEntity, method.getName());
            if (!Objects.equals(newValue, oldValue)) {
                return true;
            }
        }
        return false;
    }
}
