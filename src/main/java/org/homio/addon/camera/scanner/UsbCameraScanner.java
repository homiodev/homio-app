package org.homio.addon.camera.scanner;

import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;
import static org.homio.api.entity.HasJsonData.LIST_DELIMITER;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.camera.entity.UsbCameraEntity;
import org.homio.api.EntityContext;
import org.homio.api.EntityContextMedia.VideoInputDevice;
import org.homio.api.service.discovery.VideoStreamScanner;
import org.homio.api.util.Lang;
import org.homio.hquery.ProgressBar;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class UsbCameraScanner implements VideoStreamScanner {

    @Override
    public String getName() {
        return "scan-usb-camera";
    }

    @Override
    public DeviceScannerResult scan(EntityContext entityContext, ProgressBar progressBar,
                                                       String headerConfirmButtonKey) {
        DeviceScannerResult result = new DeviceScannerResult();
        List<VideoInputDevice> foundUsbVideoCameraDevices = new ArrayList<>();

        for (String deviceName : entityContext.media().getVideoDevices()) {
            foundUsbVideoCameraDevices.add(entityContext.media().createVideoInputDevice(deviceName).setName(deviceName));
        }
        Map<String, UsbCameraEntity> existsUsbCamera = entityContext.findAll(UsbCameraEntity.class).stream()
                .collect(Collectors.toMap(UsbCameraEntity::getIeeeAddress, Function.identity()));

        // search if new devices not found and send confirm to ui
        for (VideoInputDevice foundUsbVideoDevice : foundUsbVideoCameraDevices) {
            if (!existsUsbCamera.containsKey(foundUsbVideoDevice.getName())) {
                result.getNewCount().incrementAndGet();
                String name = Lang.getServerMessage("NEW_DEVICE.USB_CAMERA") + foundUsbVideoDevice.getName();
                handleDevice(headerConfirmButtonKey, foundUsbVideoDevice.getName(), name, entityContext,
                        messages -> messages.add(Lang.getServerMessage("NEW_DEVICE.NAME", foundUsbVideoDevice.getName())),
                        () -> {
                            log.info("Confirm save usb camera: <{}>", foundUsbVideoDevice.getName());
                            UsbCameraEntity entity = new UsbCameraEntity();
                            entity.setName(foundUsbVideoDevice.getName());
                            entity.setStreamResolutions(String.join(LIST_DELIMITER, foundUsbVideoDevice.getResolutionSet()));
                            entity.setIeeeAddress(defaultIfEmpty(foundUsbVideoDevice.getName(), String.valueOf(System.currentTimeMillis())));
                            entityContext.save(entity);
                        });
            } else {
                result.getExistedCount().incrementAndGet();
            }
        }

        return result;
    }
}
