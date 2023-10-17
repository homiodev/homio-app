package org.homio.addon.camera.scanner;

import java.net.UnknownHostException;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.homio.addon.camera.entity.IpCameraEntity;
import org.homio.addon.camera.onvif.OnvifDiscovery;
import org.homio.api.EntityContext;
import org.homio.api.service.discovery.VideoStreamScanner;
import org.homio.api.util.Lang;
import org.homio.hquery.ProgressBar;
import org.springframework.stereotype.Component;

@Log4j2
@Component
public class OnvifWsDiscoveryCameraScanner implements VideoStreamScanner {

    @Override
    public String getName() {
        return "scan-ws-discovery-camera";
    }

    @Override
    public DeviceScannerResult scan(EntityContext entityContext, ProgressBar progressBar, String headerConfirmButtonKey) {
        OnvifDiscovery onvifDiscovery = new OnvifDiscovery(entityContext);
        DeviceScannerResult result = new DeviceScannerResult();
        try {
            Map<String, IpCameraEntity> existsCamera = entityContext.findAll(IpCameraEntity.class)
                                                                    .stream().collect(Collectors.toMap(IpCameraEntity::getIp, Function.identity()));

            onvifDiscovery.discoverCameras((brand, ipAddress, onvifPort, hardwareID) -> {
                if (!existsCamera.containsKey(ipAddress)) {
                    result.getNewCount().incrementAndGet();
                    handleDevice(headerConfirmButtonKey,
                            "onvif-" + ipAddress,
                            "Onvif", entityContext,
                            messages -> {
                                messages.add(Lang.getServerMessage("VIDEO_STREAM.ADDRESS", ipAddress));
                                messages.add(Lang.getServerMessage("VIDEO_STREAM.PORT", String.valueOf(onvifPort)));
                                messages.add(Lang.getServerMessage("VIDEO_STREAM.BRAND", brand.getName()));
                                if(!StringUtils.isEmpty(hardwareID)) {
                                    messages.add(Lang.getServerMessage("VIDEO_STREAM.HARDWARE", hardwareID));
                                }
                            },
                            () -> {
                                log.info("Confirm save onvif camera with ip address: <{}>", ipAddress);
                                entityContext.save(new IpCameraEntity()
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
