package org.homio.addon.tuya.service;

import static org.homio.addon.tuya.internal.cloud.TuyaOpenAPI.gson;
import static org.homio.api.util.Constants.PRIMARY_DEVICE;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.tuya.TuyaDeviceEntity;
import org.homio.addon.tuya.TuyaProjectEntity;
import org.homio.addon.tuya.internal.cloud.TuyaOpenAPI;
import org.homio.addon.tuya.internal.cloud.dto.DeviceSchema;
import org.homio.addon.tuya.internal.cloud.dto.FactoryInformation;
import org.homio.addon.tuya.internal.cloud.dto.TuyaDeviceDTO;
import org.homio.addon.tuya.internal.util.SchemaDp;
import org.homio.api.EntityContext;
import org.homio.api.service.scan.BaseItemsDiscovery.DeviceScannerResult;
import org.homio.api.service.scan.ItemDiscoverySupport;
import org.homio.hquery.ProgressBar;
import org.springframework.stereotype.Service;

/**
 * The {@link TuyaDiscoveryService} implements the discovery service for Tuya devices from the cloud
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class TuyaDiscoveryService implements ItemDiscoverySupport {

    private final EntityContext entityContext;

    @Override
    public String getName() {
        return "scan-tuya-devices";
    }

    @Override
    public DeviceScannerResult scan(EntityContext entityContext, ProgressBar progressBar, String headerConfirmButtonKey) {
        DeviceScannerResult result = new DeviceScannerResult();
        Set<String> existedDevices = entityContext.findAll(TuyaDeviceEntity.class).stream().map(TuyaDeviceEntity::getIeeeAddress).collect(Collectors.toSet());
        try {
            TuyaProjectEntity tuyaProjectEntity = entityContext.getEntityRequire(TuyaProjectEntity.class, PRIMARY_DEVICE);
            TuyaProjectService tuyaProjectService = tuyaProjectEntity.getService();
            TuyaOpenAPI api = tuyaProjectService.getApi();
            if (!api.isConnected()) {
                log.warn("Tried to start scan but API for bridge '{}' is not connected.",
                    tuyaProjectService.getEntity().getTitle());
            } else {
                processDeviceResponse(List.of(), tuyaProjectService, 0, result, existedDevices);
            }
        } catch (Exception ex) {
            log.error("Error scan tuya devices", ex);
            entityContext.ui().sendErrorMessage(ex);
        }

        return result;
    }

    @SneakyThrows
    private void processDeviceResponse(List<TuyaDeviceDTO> deviceList,
        TuyaProjectService tuyaProjectService, int page, DeviceScannerResult result, Set<String> existedDevices) {
        deviceList.forEach(device -> processDevice(device, tuyaProjectService.getApi(), result, existedDevices));
        if (page == 0 || deviceList.size() == 100) {
            int nextPage = page + 1;
            List<TuyaDeviceDTO> nextDeviceList = tuyaProjectService.getAllDevices(nextPage);
            processDeviceResponse(nextDeviceList, tuyaProjectService, nextPage, result, existedDevices);
        }
    }

    private void processDevice(TuyaDeviceDTO device, TuyaOpenAPI api, DeviceScannerResult result, Set<String> existedDevices) {
        if (!existedDevices.contains(device.id)) {
            TuyaDeviceEntity entity = new TuyaDeviceEntity().setEntityID(device.id);
            updateTuyaDeviceEntity(device, api, entity);
            entityContext.save(entity);
            result.getNewCount().incrementAndGet();
        } else {
            result.getExistedCount().incrementAndGet();
        }
    }

    @SneakyThrows
    public static void updateTuyaDeviceEntity(TuyaDeviceDTO device, TuyaOpenAPI api, TuyaDeviceEntity entity) {
        List<FactoryInformation> infoList = api.getFactoryInformation(List.of(device.id), entity);
        String deviceMac = infoList.stream()
                                   .filter(fi -> fi.id.equals(device.id))
                                   .findAny()
                                   .map(fi -> fi.mac)
                                   .orElse("");

        entity.setCategory(device.category);
        entity.setMac(Objects.requireNonNull(deviceMac).replaceAll("(..)(?!$)", "$1:"));
        entity.setLocalKey(device.localKey);
        entity.setName(device.productName);
        entity.setIeeeAddress(device.id);
        entity.setJsonData("uuid", device.uuid);
        entity.setJsonData("model", device.model);
        entity.setProductId(device.productId);

        DeviceSchema schema = api.getDeviceSchema(device.id, entity);
        List<SchemaDp> schemaDps = new ArrayList<>();
        schema.functions.forEach(description -> addUniqueSchemaDp(description, schemaDps));
        schema.status.forEach(description -> addUniqueSchemaDp(description, schemaDps));
        entity.setSchema(schemaDps);
    }

    private static void addUniqueSchemaDp(DeviceSchema.Description description, List<SchemaDp> schemaDps) {
        if (description.dp_id == 0 || schemaDps.stream().anyMatch(schemaDp -> schemaDp.id == description.dp_id)) {
            // dp is missing or already present, skip it
            return;
        }
        // some devices report the same function code for different dps
        // we add an index only if this is the case
        String originalCode = description.code;
        int index = 1;
        while (schemaDps.stream().anyMatch(schemaDp -> schemaDp.code.equals(description.code))) {
            description.code = originalCode + "_" + index;
        }

        schemaDps.add(SchemaDp.fromRemoteSchema(gson, description));
    }
}