package org.homio.addon.camera.scanner;

import de.onvif.soap.BadCredentialException;
import de.onvif.soap.OnvifDeviceState;
import java.net.ConnectException;
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
import org.homio.addon.camera.entity.IpCameraEntity;
import org.homio.addon.camera.onvif.OnvifDiscovery;
import org.homio.addon.camera.onvif.brand.CameraBrandHandlerDescription;
import org.homio.addon.camera.setting.CameraScanPortRangeSetting;
import org.homio.addon.camera.setting.onvif.ScanOnvifHttpDefaultPasswordAuthSetting;
import org.homio.addon.camera.setting.onvif.ScanOnvifHttpDefaultUserAuthSetting;
import org.homio.addon.camera.setting.onvif.ScanOnvifHttpMaxPingTimeoutSetting;
import org.homio.addon.camera.setting.onvif.ScanOnvifPortsSetting;
import org.homio.api.Context;
import org.homio.api.ContextNetwork;
import org.homio.api.service.discovery.VideoStreamScanner;
import org.homio.api.util.CommonUtils;
import org.homio.api.util.HardwareUtils;
import org.homio.api.util.Lang;
import org.homio.hquery.ProgressBar;
import org.homio.hquery.hardware.network.NetworkHardwareRepository;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class OnvifCameraHttpScanner implements VideoStreamScanner {

    private static final int THREAD_COUNT = 8;
    private final Context context;
    private DeviceScannerResult result;
    private String headerConfirmButtonKey;

    @Override
    public String getName() {
        return "scan-onvif-http-stream";
    }

    @SneakyThrows
    @Override
    public synchronized DeviceScannerResult scan(Context context,
                                                                    ProgressBar progressBar,
                                                                    String headerConfirmButtonKey) {
        return executeScan(context, progressBar, headerConfirmButtonKey);
    }

    public DeviceScannerResult executeScan(Context context, ProgressBar progressBar,
        String headerConfirmButtonKey) {
        this.headerConfirmButtonKey = headerConfirmButtonKey;
        this.result = new DeviceScannerResult();
        List<IpCameraEntity> allSavedCameraEntities = context.db().findAll(IpCameraEntity.class);
        Map<String, IpCameraEntity> existsCameraByIpPort = allSavedCameraEntities.stream().collect(
            Collectors.toMap(e -> e.getIp() + ":" + e.getOnvifPort(), Function.identity()));

        String user = context.setting().getValue(ScanOnvifHttpDefaultUserAuthSetting.class);
        String password = context.setting().getValue(ScanOnvifHttpDefaultPasswordAuthSetting.class);
        NetworkHardwareRepository networkHardwareRepository = context.getBean(NetworkHardwareRepository.class);

        Set<Integer> ports = context.setting().getValue(ScanOnvifPortsSetting.class);
        int pingTimeout = context.setting().getValue(ScanOnvifHttpMaxPingTimeoutSetting.class);
        Set<String> ipRangeList = context.setting().getValue(CameraScanPortRangeSetting.class);

        Map<String, Callable<Integer>> tasks = new HashMap<>();
        for (String ipRange : ipRangeList) {
            tasks.putAll(networkHardwareRepository.buildPingIpAddressTasks(ipRange, log::debug, ports, pingTimeout, (ipAddress, port) ->
                buildCameraTask(existsCameraByIpPort, user, password, ipAddress, port)));
        }

        context.event().runOnceOnInternetUp("scan-onvif-camera", () -> {
            List<Integer> availableOnvifCameras = context.bgp().runInBatchAndGet("scan-onvif-http-batch-result",
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

    private void buildCameraTask(Map<String, IpCameraEntity> existsCameraByIpPort, String user, String password,
                                 String ipAddress, Integer port) {
        String host = ipAddress + ":" + port;

        // first check if camera already saved and pass its credentials
        if (existsCameraByIpPort.containsKey(host)) {
            user = existsCameraByIpPort.get(host).getUser();
            password = existsCameraByIpPort.get(host).getPassword().asString();
        }

        log.info("Onvif ip alive: <{}>. Fetching camera capabilities", host);
        OnvifDeviceState onvifDeviceState = new OnvifDeviceState("-");
        onvifDeviceState.updateParameters(ipAddress, port, user, password);
        if (!tryFindCameraFromDb(ipAddress, port, existsCameraByIpPort)) {
            try {
                testCamera(ipAddress, 80, onvifDeviceState);
                foundDeviceServices(onvifDeviceState, false);
            } catch (BadCredentialException bex) {
                log.warn("Onvif camera <{}> got fault auth response: <{}>", host, bex.getMessage());
                foundDeviceServices(onvifDeviceState, true);
            } catch (Exception ex) {
                log.error("Onvif camera <{}> got fault response: <{}>", host, ex.getMessage());
            }
        }
    }

    private static void testCamera(String ipAddress, Integer port, OnvifDeviceState onvifDeviceState) throws ConnectException {
        ContextNetwork.ping(ipAddress, port);
        // check for Authentication validation
        onvifDeviceState.getInitialDevices().getDeviceInformation();
    }

    private boolean tryFindCameraFromDb(String ipAddress, Integer port, Map<String, IpCameraEntity> existsCameraByIpPort) {
        log.info("Onvif camera got fault auth response. Checking user/pwd from other all saved cameras. Maybe ip address has " +
                "been changed");
        for (IpCameraEntity entity : existsCameraByIpPort.values()) {
            OnvifDeviceState onvifDeviceState = new OnvifDeviceState("-");
            onvifDeviceState.updateParameters(ipAddress, port,
                    entity.getUser(), entity.getPassword().asString());
            try {
                testCamera(ipAddress, entity.getRestPort(), onvifDeviceState);
                if (Objects.equals(entity.getIeeeAddress(), onvifDeviceState.getIEEEAddress(false))) {
                    updateCameraIpPortName(onvifDeviceState, entity);
                    return true;
                }
            } catch (Exception ignore) {
            }
        }
        return false;
    }

    private void foundDeviceServices(OnvifDeviceState onvifDeviceState, boolean requireAuth) {
        log.info("Scan found onvif camera: <{}>", onvifDeviceState.getHOST_IP());
        result.getNewCount().incrementAndGet();

        CameraBrandHandlerDescription brand = OnvifDiscovery.getBrandFromLoginPage(onvifDeviceState.getIp(), context);
        String name = Lang.getServerMessage("NEW_DEVICE.ONVIF_CAMERA") + onvifDeviceState.getHOST_IP();
        // get IEEEAddress call init() internally
        handleDevice(headerConfirmButtonKey,
                "onvif-http-" + onvifDeviceState.getHOST_IP(),
            name, context,
                messages -> {
                    messages.add(Lang.getServerMessage("VIDEO_STREAM.ADDRESS", onvifDeviceState.getHOST_IP()));
                    if (requireAuth) {
                        messages.add(Lang.getServerMessage("VIDEO_STREAM.REQUIRE_AUTH"));
                    } else {
                        messages.add(Lang.getServerMessage("VIDEO_STREAM.NAME", fetchCameraName(onvifDeviceState)));
                        messages.add(Lang.getServerMessage("VIDEO_STREAM.MODEL", fetchCameraModel(onvifDeviceState)));
                    }
                    messages.add(Lang.getServerMessage("VIDEO_STREAM.BRAND", brand.getName()));
                },
                () -> {
                    log.info("Saving onvif camera with host: <{}>", onvifDeviceState.getHOST_IP());
                    IpCameraEntity entity = new IpCameraEntity().setCameraType(brand.getID());
                    entity.setInfo(onvifDeviceState, requireAuth);
                    context.db().save(entity);
                });
    }

    private void updateCameraIpPortName(OnvifDeviceState onvifDeviceState, IpCameraEntity existedCamera) {
        try {
            existedCamera.tryUpdateData(context, onvifDeviceState.getIp(), onvifDeviceState.getOnvifPort());
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
            log.warn("[{}]: Error during fetch Onvif camera name: {}", onvifDeviceState.getEntityID(), CommonUtils.getErrorMessage(ex));
            return "Unknown name";
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
