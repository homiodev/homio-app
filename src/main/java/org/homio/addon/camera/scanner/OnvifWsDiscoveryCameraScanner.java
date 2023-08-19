package org.homio.addon.camera.scanner;

import lombok.extern.log4j.Log4j2;
import org.homio.addon.camera.entity.OnvifCameraEntity;
import org.homio.addon.camera.onvif.OnvifDiscovery;
import org.homio.api.EntityContext;
import org.homio.api.service.scan.BaseItemsDiscovery;
import org.homio.api.service.scan.VideoStreamScanner;
import org.homio.api.util.Lang;
import org.homio.hquery.ProgressBar;
import org.springframework.stereotype.Component;

import java.net.UnknownHostException;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Log4j2
@Component
public class OnvifWsDiscoveryCameraScanner implements VideoStreamScanner {

    @Override
    public String getName() {
        return "scan-ws-discovery-camera";
    }

    @Override
    public BaseItemsDiscovery.DeviceScannerResult scan(EntityContext entityContext, ProgressBar progressBar, String headerConfirmButtonKey) {
        OnvifDiscovery onvifDiscovery = new OnvifDiscovery(entityContext);
        BaseItemsDiscovery.DeviceScannerResult result = new BaseItemsDiscovery.DeviceScannerResult();
        try {
            Map<String, OnvifCameraEntity> existsCamera = entityContext.findAll(OnvifCameraEntity.class)
                    .stream().collect(Collectors.toMap(OnvifCameraEntity::getIp, Function.identity()));

            onvifDiscovery.discoverCameras((brand, ipAddress, onvifPort) -> {
                if (!existsCamera.containsKey(ipAddress)) {
                    result.getNewCount().incrementAndGet();
                    handleDevice(headerConfirmButtonKey,
                            "onvif-" + ipAddress,
                            brand + "/" + ipAddress, entityContext,
                            messages -> {
                                messages.add(Lang.getServerMessage("VIDEO_STREAM.PORT", String.valueOf(onvifPort)));
                                messages.add(Lang.getServerMessage("VIDEO_STREAM.BRAND", brand.getName()));
                            },
                            () -> {
                                log.info("Confirm save onvif camera with ip address: <{}>", ipAddress);
                                entityContext.save(new OnvifCameraEntity()
                                        .setIp(ipAddress)
                                        .setOnvifPort(onvifPort)
                                        .setCameraType(brand.getID()));
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
