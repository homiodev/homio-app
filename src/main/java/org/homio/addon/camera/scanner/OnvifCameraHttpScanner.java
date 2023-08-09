package org.homio.addon.camera.scanner;

import de.onvif.soap.BadCredentialException;
import de.onvif.soap.OnvifDeviceState;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.camera.entity.OnvifCameraEntity;
import org.homio.addon.camera.onvif.OnvifDiscovery;
import org.homio.addon.camera.onvif.brand.CameraBrandHandlerDescription;
import org.homio.addon.camera.setting.CameraScanPortRangeSetting;
import org.homio.addon.camera.setting.onvif.ScanOnvifHttpDefaultPasswordAuthSetting;
import org.homio.addon.camera.setting.onvif.ScanOnvifHttpDefaultUserAuthSetting;
import org.homio.addon.camera.setting.onvif.ScanOnvifHttpMaxPingTimeoutSetting;
import org.homio.addon.camera.setting.onvif.ScanOnvifPortsSetting;
import org.homio.api.EntityContext;
import org.homio.api.service.scan.BaseItemsDiscovery;
import org.homio.api.service.scan.VideoStreamScanner;
import org.homio.api.util.CommonUtils;
import org.homio.api.util.Lang;
import org.homio.hquery.ProgressBar;
import org.homio.hquery.hardware.network.NetworkHardwareRepository;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class OnvifCameraHttpScanner implements VideoStreamScanner {

    private static final int THREAD_COUNT = 8;
    private final EntityContext entityContext;
    private BaseItemsDiscovery.DeviceScannerResult result;
    private String headerConfirmButtonKey;

    @Override
    public String getName() {
        return "scan-onvif-http-stream";
    }

    @SneakyThrows
    @Override
    public synchronized BaseItemsDiscovery.DeviceScannerResult scan(EntityContext entityContext,
        ProgressBar progressBar,
        String headerConfirmButtonKey) {
        return executeScan(entityContext, progressBar, headerConfirmButtonKey, false);
    }

    public BaseItemsDiscovery.DeviceScannerResult executeScan(EntityContext entityContext, ProgressBar progressBar,
        String headerConfirmButtonKey, boolean rediscoverIpAddresses) {
        this.headerConfirmButtonKey = headerConfirmButtonKey;
        this.result = new BaseItemsDiscovery.DeviceScannerResult();
        List<OnvifCameraEntity> allSavedCameraEntities = entityContext.findAll(OnvifCameraEntity.class);
        Map<String, OnvifCameraEntity> existsCameraByIpPort = allSavedCameraEntities.stream()
                                                                                    .collect(Collectors.toMap(e -> e.getIp() + ":" + e.getOnvifPort(),
                                                                                        Function.identity()));

        String user = entityContext.setting().getValue(ScanOnvifHttpDefaultUserAuthSetting.class);
        String password = entityContext.setting().getValue(ScanOnvifHttpDefaultPasswordAuthSetting.class);
        NetworkHardwareRepository networkHardwareRepository = entityContext.getBean(NetworkHardwareRepository.class);

        Set<Integer> ports = entityContext.setting().getValue(ScanOnvifPortsSetting.class);
        int pingTimeout = entityContext.setting().getValue(ScanOnvifHttpMaxPingTimeoutSetting.class);
        Set<String> ipRangeList = entityContext.setting().getValue(CameraScanPortRangeSetting.class);

        Map<String, Callable<Integer>> tasks = new HashMap<>();
        for (String ipRange : ipRangeList) {
            tasks.putAll(networkHardwareRepository.buildPingIpAddressTasks(ipRange, log::info, ports, pingTimeout, (ipAddress, port) ->
                buildCameraTask(rediscoverIpAddresses, allSavedCameraEntities, existsCameraByIpPort, user, password,
                    ipAddress, port)));
        }

        entityContext.event().runOnceOnInternetUp("scan-onvif-camera", () -> {
            List<Integer> availableOnvifCameras = entityContext.bgp().runInBatchAndGet("scan-onvif-http-batch-result",
                Duration.ofSeconds(2L * pingTimeout * tasks.size()), THREAD_COUNT, tasks,
                completedTaskCount -> {
                    if (progressBar != null) {
                        progressBar.progress(100 / (float) tasks.size() * completedTaskCount,
                            "Onvif http stream done " + completedTaskCount + "/" + tasks.size() + " tasks");
                    }
                });
            log.info("Found {} onvif cameras", availableOnvifCameras.stream().filter(Objects::nonNull).count());
        });

        return result;
    }

    private void buildCameraTask(boolean rediscoverIpAddresses, List<OnvifCameraEntity> allSavedCameraEntities,
        Map<String, OnvifCameraEntity> existsCameraByIpPort, String user, String password,
        String ipAddress, Integer port) {
        String host = ipAddress + ":" + port;

        // first check if camera already saved and pass it's credentials
        if (existsCameraByIpPort.containsKey(host)) {
            user = existsCameraByIpPort.get(host).getUser();
            password = existsCameraByIpPort.get(host).getPassword().asString();
        }

        log.info("Onvif ip alive: <{}>. Fetching camera capabilities", host);
        OnvifDeviceState onvifDeviceState = new OnvifDeviceState("-");
        onvifDeviceState.updateParameters(ipAddress, port, 0, user, password);
        try {
            onvifDeviceState.checkForErrors();
            foundDeviceServices(onvifDeviceState, false, rediscoverIpAddresses, existsCameraByIpPort);
        } catch (BadCredentialException bex) {
            if (!tryFindCameraFromDb(allSavedCameraEntities, ipAddress, port, existsCameraByIpPort)) {
                log.warn("Onvif camera <{}> got fault auth response: <{}>", host, bex.getMessage());
                foundDeviceServices(onvifDeviceState, true, rediscoverIpAddresses, existsCameraByIpPort);
            }
        } catch (Exception ex) {
            log.error("Onvif camera <{}> got fault response: <{}>", host, ex.getMessage());
        }
    }

    private boolean tryFindCameraFromDb(List<OnvifCameraEntity> allSavedCameraEntities, String ipAddress, Integer port,
        Map<String, OnvifCameraEntity> existsCameraByIpPort) {
        log.info("Onvif camera got fault auth response. Checking user/pwd from other all saved cameras. Maybe ip address has " +
            "been changed");
        for (OnvifCameraEntity entity : allSavedCameraEntities) {
            OnvifDeviceState onvifDeviceState = new OnvifDeviceState("-");
            onvifDeviceState.updateParameters(ipAddress, port, 0,
                entity.getUser(), entity.getPassword().asString());
            try {
                onvifDeviceState.checkForErrors();
                foundDeviceServices(onvifDeviceState, false, false, existsCameraByIpPort);
                return true;
            } catch (Exception ignore) {
            }
        }
        return false;
    }

    private void foundDeviceServices(OnvifDeviceState onvifDeviceState, boolean requireAuth,
        boolean rediscoverIpAddresses, Map<String, OnvifCameraEntity> existsCameraByIpPort) {
        log.info("Scan found onvif camera: <{}>", onvifDeviceState.getHOST_IP());

        OnvifCameraEntity existedCamera = existsCameraByIpPort.get(onvifDeviceState.getIp() +
            ":" + onvifDeviceState.getOnvifPort());
        if (existedCamera != null) {
            updateCameraIpPortName(onvifDeviceState, existedCamera);
            result.getExistedCount().incrementAndGet();
            return;
        } /*else if (!rediscoverIpAddresses) {
            return;
        }*/
        result.getNewCount().incrementAndGet();

        CameraBrandHandlerDescription brand = OnvifDiscovery.getBrandFromLoginPage(onvifDeviceState.getIp(), entityContext);
        handleDevice(headerConfirmButtonKey,
            "onvif-http-" + onvifDeviceState.getHOST_IP(),
            onvifDeviceState.getHOST_IP(), entityContext,
            messages -> {
                messages.add(Lang.getServerMessage("VIDEO_STREAM.ADDRESS", onvifDeviceState.getHOST_IP()));
                if (requireAuth) {
                    messages.add(Lang.getServerMessage("VIDEO_STREAM.REQUIRE_AUTH"));
                }
                messages.add(Lang.getServerMessage("VIDEO_STREAM.NAME", fetchCameraName(onvifDeviceState)));
                messages.add(Lang.getServerMessage("VIDEO_STREAM.MODEL", fetchCameraModel(onvifDeviceState)));
                messages.add(Lang.getServerMessage("VIDEO_STREAM.BRAND", brand.getName()));
            },
            () -> {
                log.info("Saving onvif camera with host: <{}>", onvifDeviceState.getHOST_IP());
                OnvifCameraEntity entity = new OnvifCameraEntity()
                    .setIp(onvifDeviceState.getIp())
                    .setOnvifPort(onvifDeviceState.getOnvifPort())
                    .setCameraType(brand.getID());
                entity.setUser(onvifDeviceState.getUsername());
                entity.setPassword(onvifDeviceState.getPassword());
                if (!requireAuth) {
                    entity.setName(onvifDeviceState.getInitialDevices().getName())
                          .setIeeeAddress(onvifDeviceState.getIEEEAddress());
                }

                entityContext.save(entity);
            });
    }

    private void updateCameraIpPortName(OnvifDeviceState onvifDeviceState, OnvifCameraEntity existedCamera) {
        try {
            existedCamera.tryUpdateData(entityContext, onvifDeviceState.getIp(), onvifDeviceState.getOnvifPort(),
                onvifDeviceState.getInitialDevices().getName());
        } catch (Exception ex) {
            log.error("Error while trying update camera: <{}>", CommonUtils.getErrorMessage(ex));
        }
    }

    private String fetchCameraName(OnvifDeviceState onvifDeviceState) {
        try {
            return onvifDeviceState.getInitialDevices().getName();
        } catch (BadCredentialException ex) {
            return "Require auth to fetch name";
        } catch (Exception ex) {
            return "Unknown name: " + CommonUtils.getErrorMessage(ex);
        }
    }

    private String fetchCameraModel(OnvifDeviceState onvifDeviceState) {
        try {
            return onvifDeviceState.getInitialDevices().getDeviceInformation().getModel();
        } catch (BadCredentialException ex) {
            return "Require auth to fetch model";
        } catch (Exception ex) {
            return "Unknown model: " + CommonUtils.getErrorMessage(ex);
        }
    }
}
