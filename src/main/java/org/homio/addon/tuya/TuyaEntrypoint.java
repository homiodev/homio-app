package org.homio.addon.tuya;

import static org.homio.api.util.Constants.PRIMARY_DEVICE;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URL;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.homio.addon.tuya.internal.cloud.TuyaOpenAPI;
import org.homio.addon.tuya.service.TuyaDeviceService;
import org.homio.api.AddonEntrypoint;
import org.homio.api.EntityContext;
import org.homio.hquery.hardware.network.NetworkHardwareRepository;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TuyaEntrypoint implements AddonEntrypoint {

    private final EntityContext entityContext;

    public URL getAddonImageURL() {
        return getResource("images/tuya.png");
    }

    @Override
    public void init() {
        TuyaProjectEntity tuyaProjectEntity = ensureEntityExists(entityContext);
        TuyaOpenAPI.setProjectEntity(tuyaProjectEntity);
        tuyaProjectEntity.getService().initialize();
        for (TuyaDeviceEntity entity : entityContext.findAll(TuyaDeviceEntity.class)) {
            entity.getService().initialize();
        }
    }

    @Override
    public void destroy() {
        TuyaDeviceService.destroyAll();
    }

    public @NotNull TuyaProjectEntity ensureEntityExists(EntityContext entityContext) {
        TuyaProjectEntity entity = entityContext.getEntity(TuyaProjectEntity.class, PRIMARY_DEVICE);
        if (entity == null) {
            entity = new TuyaProjectEntity()
                .setEntityID(PRIMARY_DEVICE)
                .setName("Tuya primary project");
            if (entityContext.event().isInternetUp()) {
                Integer countryCode = getCountryCode(entityContext);
                if (countryCode != null) {
                    entity.setCountryCode(countryCode);
                }
            }
            entityContext.save(entity, false);
            entity.setJsonData("dis_del", true);
        }
        if (entity.getCountryCode() == null) {
            scheduleUpdateTuyaProjectOnInternetUp(entityContext);
        }
        return entity;
    }

    private void scheduleUpdateTuyaProjectOnInternetUp(EntityContext entityContext) {
        entityContext.event().runOnceOnInternetUp("create-tuya-project", () -> {
            Integer countryCode = getCountryCode(entityContext);
            if (countryCode != null) {
                TuyaProjectEntity projectEntity = entityContext.getEntityRequire(TuyaProjectEntity.class, PRIMARY_DEVICE);
                entityContext.save(projectEntity.setCountryCode(countryCode));
            }
        });
    }

    private Integer getCountryCode(EntityContext entityContext) {
        NetworkHardwareRepository networkHardwareRepository = entityContext.getBean(NetworkHardwareRepository.class);
        String ipAddress = networkHardwareRepository.getOuterIpAddress();
        JsonNode ipGeoLocation = networkHardwareRepository.getIpGeoLocation(ipAddress);
        String country = ipGeoLocation.path("country").asText();
        if (StringUtils.isNotEmpty(country)) {
            JsonNode countryInfo = networkHardwareRepository.getCountryInformation(country);
            return countryInfo.withArray("callingCodes").path(0).asInt();
        }
        return null;
    }
}