package org.touchhome.app.videoStream.scanner;

import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.touchhome.app.videoStream.entity.UsbCameraEntity;
import org.touchhome.app.videoStream.ffmpeg.FFmpegVideoDevice;
import org.touchhome.app.videoStream.ffmpeg.FfmpegInputDeviceHardwareRepository;
import org.touchhome.app.videoStream.setting.CameraFFMPEGInstallPathOptions;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.Lang;
import org.touchhome.bundle.api.model.ProgressBar;
import org.touchhome.bundle.api.service.scan.BaseItemsDiscovery;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Log4j2
@Component
public class UsbCameraScanner implements VideoStreamScanner {
    @Override
    public String getName() {
        return "scan-usb-camera";
    }

    @Override
    public BaseItemsDiscovery.DeviceScannerResult scan(EntityContext entityContext, ProgressBar progressBar, String headerConfirmButtonKey) {
        FfmpegInputDeviceHardwareRepository repository = entityContext.getBean(FfmpegInputDeviceHardwareRepository.class);
        String ffmpegPath = entityContext.setting().getValue(CameraFFMPEGInstallPathOptions.class, Paths.get("ffmpeg")).toString();
        List<FFmpegVideoDevice> foundUsbVideoCameraDevices = new ArrayList<>();
        BaseItemsDiscovery.DeviceScannerResult result = new BaseItemsDiscovery.DeviceScannerResult();

        for (String deviceName : repository.getVideoDevices(ffmpegPath)) {
            foundUsbVideoCameraDevices.add(repository.createVideoInputDevice(ffmpegPath, deviceName).setName(deviceName));
        }
        Map<String, UsbCameraEntity> existsUsbCamera = entityContext.findAll(UsbCameraEntity.class).stream().collect(Collectors.toMap(UsbCameraEntity::getName, Function.identity()));

        // search if new devices not found and send confirm to ui
        for (FFmpegVideoDevice foundUsbVideoDevice : foundUsbVideoCameraDevices) {
            if (!existsUsbCamera.containsKey(foundUsbVideoDevice.getName())) {
                result.getNewCount().incrementAndGet();
                handleDevice(headerConfirmButtonKey, foundUsbVideoDevice.getName(), foundUsbVideoDevice.getName(), entityContext,
                        messages -> messages.add(Lang.getServerMessage("NEW_DEVICE.NAME", "NAME", foundUsbVideoDevice.getName())),
                        () -> {
                            log.info("Confirm save usb camera: <{}>", foundUsbVideoDevice.getName());
                            entityContext.save(new UsbCameraEntity().setName(foundUsbVideoDevice.getName())
                                    .setIeeeAddress(foundUsbVideoDevice.getName()));
                        });
            } else {
                result.getExistedCount().incrementAndGet();
            }
        }

        return result;
    }
}
