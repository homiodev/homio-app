package org.touchhome.app.videoStream.scanner;

import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.touchhome.app.videoStream.entity.OnvifCameraEntity;
import org.touchhome.app.videoStream.onvif.OnvifDiscovery;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.Lang;
import org.touchhome.bundle.api.model.ProgressBar;
import org.touchhome.bundle.api.service.scan.BaseItemsDiscovery;

import java.net.UnknownHostException;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Log4j2
@Component
public class OnvifCameraScanner implements VideoStreamScanner {

    @Override
    public String getName() {
        return "scan-onvif-camera";
    }

    @Override
    public BaseItemsDiscovery.DeviceScannerResult scan(EntityContext entityContext, ProgressBar progressBar, String headerConfirmButtonKey) {
        OnvifDiscovery onvifDiscovery = new OnvifDiscovery();
        BaseItemsDiscovery.DeviceScannerResult result = new BaseItemsDiscovery.DeviceScannerResult();
        try {
            Map<String, OnvifCameraEntity> existsCamera = entityContext.findAll(OnvifCameraEntity.class)
                    .stream().collect(Collectors.toMap(OnvifCameraEntity::getIp, Function.identity()));

            onvifDiscovery.discoverCameras((cameraType, ipAddress, onvifPort) -> {
                if (!existsCamera.containsKey(ipAddress)) {
                    result.getNewCount().incrementAndGet();
                    handleDevice(headerConfirmButtonKey,
                            "onvif-" + ipAddress,
                            cameraType.name() + "/" + ipAddress, entityContext,
                            messages ->
                                    messages.add(Lang.getServerMessage("VIDEO_STREAM.PORT", "PORT", String.valueOf(onvifPort))),
                            () -> {
                                log.info("Confirm save onvif camera with ip address: <{}>", ipAddress);
                                entityContext.save(new OnvifCameraEntity().setIp(ipAddress).setOnvifPort(onvifPort).setCameraType(cameraType));
                            });
                } else {
                    result.getExistedCount().incrementAndGet();
                }
            });
        } catch (UnknownHostException | InterruptedException e) {
            log.warn("IpCamera Discovery has an issue discovering the network settings to find cameras with. Try setting up the camera manually.");
        }
        return result;
    }
}
