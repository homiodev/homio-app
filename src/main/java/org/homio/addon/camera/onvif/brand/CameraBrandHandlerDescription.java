package org.homio.addon.camera.onvif.brand;

import lombok.SneakyThrows;
import org.homio.addon.camera.onvif.impl.CameraBrandHandler;
import org.homio.addon.camera.onvif.impl.OnvifBrandHandler;
import org.homio.addon.camera.service.OnvifCameraService;

public class CameraBrandHandlerDescription {

    public static CameraBrandHandlerDescription DEFAULT_BRAND = new CameraBrandHandlerDescription(OnvifBrandHandler.class);

    private final Class<? extends BaseOnvifCameraBrandHandler> brandHandler;
    private final CameraBrandHandler cameraBrandHandler;

    @SneakyThrows
    public CameraBrandHandlerDescription(Class<? extends BaseOnvifCameraBrandHandler> brandHandler) {
        this.brandHandler = brandHandler;
        this.cameraBrandHandler = brandHandler.getDeclaredAnnotation(CameraBrandHandler.class);
    }

    public String getID() {
        return brandHandler.getSimpleName();
    }

    public String getName() {
        return cameraBrandHandler.value();
    }

    @SneakyThrows
    public BaseOnvifCameraBrandHandler newInstance(OnvifCameraService service) {
        return brandHandler.getConstructor(OnvifCameraService.class).newInstance(service);
    }
}
