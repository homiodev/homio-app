package org.homio.addon.tuya;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URL;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.homio.addon.tuya.service.TuyaDeviceService;
import org.homio.api.AddonEntrypoint;
import org.homio.api.EntityContext;
import org.homio.hquery.hardware.network.NetworkHardwareRepository;
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
        ensureEntityExists(entityContext);
    }

    @Override
    public void destroy() {
        TuyaDeviceService.destroyAll();
    }

    public static void ensureEntityExists(EntityContext entityContext) {
        TuyaProjectEntity entity = entityContext.getEntity(TuyaProjectEntity.DEFAULT_ENTITY_ID);
        if (entity == null) {
            entity = new TuyaProjectEntity()
                .setEntityID(TuyaProjectEntity.DEFAULT_ENTITY_ID)
                .setName("Tuya project");
            entity.setJsonData("dis_del", true);
            if(entityContext.event().isInternetUp()) {
                entity.setCountryCode(getCountryCode(entityContext));
            } else {
                scheduleUpdateTuyaProjectOnInternetUp(entityContext);
            }
            entityContext.save(entity);
        } else if(entity.getCountryCode() == null) {
            scheduleUpdateTuyaProjectOnInternetUp(entityContext);
        }
    }

    private static void scheduleUpdateTuyaProjectOnInternetUp(EntityContext entityContext) {
        entityContext.event().runOnceOnInternetUp("create-tuya-project", () -> {
            Integer countryCode = getCountryCode(entityContext);
            if (countryCode != null) {
                TuyaProjectEntity projectEntity = entityContext.getEntityRequire(TuyaProjectEntity.DEFAULT_ENTITY_ID);
                entityContext.save(projectEntity.setCountryCode(countryCode));
            }
        });
    }

    private static Integer getCountryCode(EntityContext entityContext) {
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
