package org.homio.addon.tuya;

import static org.homio.api.util.Constants.PRIMARY_DEVICE;

import com.fasterxml.jackson.databind.JsonNode;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import java.net.URL;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.homio.addon.tuya.internal.cloud.TuyaOpenAPI;
import org.homio.addon.tuya.internal.local.UdpDiscoveryListener;
import org.homio.addon.z2m.model.Z2MDeviceEntity;
import org.homio.addon.z2m.setting.ZigBeeEntityCompactModeSetting;
import org.homio.api.AddonEntrypoint;
import org.homio.api.EntityContext;
import org.homio.hquery.hardware.network.NetworkHardwareRepository;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class TuyaEntrypoint implements AddonEntrypoint {

    public static final EventLoopGroup eventLoopGroup = new NioEventLoopGroup();

    public static final @NotNull UdpDiscoveryListener udpDiscoveryListener = new UdpDiscoveryListener(eventLoopGroup);

    private final EntityContext entityContext;

    public URL getAddonImageURL() {
        return getResource("images/tuya.png");
    }

    @Override
    public void init() {
        entityContext.setting().listenValue(TuyaEntityCompactModeSetting.class, "tuya-compact-mode",
            (value) -> entityContext.ui().updateItems(TuyaDeviceEntity.class));
        TuyaProjectEntity tuyaProjectEntity = ensureEntityExists(entityContext);
        udpDiscoveryListener.setProjectEntityID(tuyaProjectEntity.getEntityID());
        try {
            udpDiscoveryListener.activate();
        } catch (Exception ex) {
            tuyaProjectEntity.setUdpMessage("Unable to start tuya udp discovery");
            log.error("Unable to start tuya udp discovery", ex);
            entityContext.bgp().builder("tuya-udp-restart")
                         .interval(Duration.ofSeconds(60))
                         .execute(context -> {
                             udpDiscoveryListener.activate();
                             context.cancel();
                             return null;
                         });
        }
        entityContext.setting().listenValue(ScanTuyaDevicesSetting.class, "scan-tuya", () ->
            tuyaProjectEntity.scanDevices(entityContext));

        TuyaOpenAPI.setProjectEntity(tuyaProjectEntity);
        udpDiscoveryListener.setProjectEntityID(tuyaProjectEntity.getEntityID());
    }

    @Override
    public void destroy() {
        log.warn("Destroy tuya entrypoint");
        udpDiscoveryListener.deactivate();
        eventLoopGroup.shutdownGracefully();
        // keep tuya project and all devices in db in case of recovery
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
