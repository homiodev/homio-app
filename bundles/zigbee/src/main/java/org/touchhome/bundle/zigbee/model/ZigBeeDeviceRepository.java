package org.touchhome.bundle.zigbee.model;

import com.zsmartsystems.zigbee.IeeeAddress;
import org.springframework.stereotype.Repository;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.json.Option;
import org.touchhome.bundle.api.link.DeviceChannelLinkType;
import org.touchhome.bundle.api.link.HasWorkspaceVariableLinkAbility;
import org.touchhome.bundle.api.model.BaseEntity;
import org.touchhome.bundle.api.repository.AbstractRepository;
import org.touchhome.bundle.api.scratch.Scratch3Block;
import org.touchhome.bundle.zigbee.*;
import org.touchhome.bundle.zigbee.converter.ZigBeeBaseChannelConverter;
import org.touchhome.bundle.zigbee.converter.impl.ZigBeeConverterEndpoint;
import org.touchhome.bundle.zigbee.setting.ZigbeeCoordinatorHandlerSetting;
import org.touchhome.bundle.zigbee.workspace.Scratch3ZigbeeBlock;
import org.touchhome.bundle.zigbee.workspace.Scratch3ZigbeeExtensionBlocks;
import org.touchhome.bundle.zigbee.workspace.ZigBeeDeviceUpdateValueListener;

import java.util.*;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;

@Repository
public class ZigBeeDeviceRepository extends AbstractRepository<ZigBeeDeviceEntity> implements HasWorkspaceVariableLinkAbility {

    private final ZigBeeBundleContext zigbeeBundleContext;
    private final EntityContext entityContext;
    private final ZigBeeDeviceUpdateValueListener zigBeeDeviceUpdateValueListener;
    private final List<Scratch3Block> zigbeeBlocks;

    public ZigBeeDeviceRepository(ZigBeeBundleContext zigbeeBundleContext, EntityContext entityContext,
                                  ZigBeeDeviceUpdateValueListener zigBeeDeviceUpdateValueListener,
                                  List<Scratch3ZigbeeExtensionBlocks> scratch3ZigbeeExtensionBlocks) {
        super(ZigBeeDeviceEntity.class, ZigBeeDeviceEntity.PREFIX);
        this.zigBeeDeviceUpdateValueListener = zigBeeDeviceUpdateValueListener;
        this.zigbeeBundleContext = zigbeeBundleContext;
        this.entityContext = entityContext;
        this.zigbeeBlocks = scratch3ZigbeeExtensionBlocks.stream().flatMap(map -> map.getBlocksMap().values().stream())
                .collect(Collectors.toList());
    }

    @Override
    public void createVariable(String entityID, String varGroup, String varName, String key) {
        ZigBeeDeviceEntity zigBeeDeviceEntity = entityContext.getEntity(entityID);
        List<Map.Entry<ZigBeeConverterEndpoint, ZigBeeBaseChannelConverter>> availableLinks = getAvailableLinks(zigBeeDeviceEntity.getZigBeeDevice());
        for (Map.Entry<ZigBeeConverterEndpoint, ZigBeeBaseChannelConverter> availableLink : availableLinks) {
            ZigBeeConverterEndpoint converterEndpoint = availableLink.getKey();
            if (converterEndpoint.toUUID().asKey().equals(key)) {
                this.createVariableLink(converterEndpoint, zigBeeDeviceEntity.getZigBeeDevice(), varGroup, varName);
            }
        }
    }

    @Override
    public void updateEntityAfterFetch(ZigBeeDeviceEntity entity) {
        ZigBeeDevice device = zigbeeBundleContext.getCoordinatorHandler().getZigBeeDevices().get(entity.getIeeeAddress());
        entity.setZigBeeDevice(device);
        if (device != null) {
            ZigBeeNodeDescription zigBeeNodeDescription = device.getZigBeeNodeDescription();
            if (entity.getModelIdentifier() == null && zigBeeNodeDescription.getModelIdentifier() != null) {
                save(entity.setModelIdentifier(zigBeeNodeDescription.getModelIdentifier()));
            } else if (entity.getModelIdentifier() != null && zigBeeNodeDescription.getModelIdentifier() == null) {
                zigBeeNodeDescription.setModelIdentifier(entity.getModelIdentifier());
            }

            entity.setZigBeeNodeDescription(zigBeeNodeDescription);
        }

        gatherAvailableLinks(entity, device);
    }

    private void gatherAvailableLinks(ZigBeeDeviceEntity entity, ZigBeeDevice device) {
        List<Map<Option, String>> links = new ArrayList<>();
        for (Map.Entry<ZigBeeConverterEndpoint, ZigBeeBaseChannelConverter> availableLinkEntry : getAvailableLinks(device)) {
            ZigBeeConverterEndpoint converterEndpoint = availableLinkEntry.getKey();
            ZigBeeDeviceStateUUID uuid = converterEndpoint.toUUID();

            Map<Option, String> map = new HashMap<>();
            DeviceChannelLinkType deviceChannelLinkType = availableLinkEntry.getKey().getZigBeeConverter().linkType();

            ZigBeeDeviceUpdateValueListener.LinkDescription linkDescription = zigBeeDeviceUpdateValueListener.getLinkListeners().get(uuid);

            if (linkDescription != null) {
                BaseEntity variableEntity = entityContext.getEntity(deviceChannelLinkType.getEntityPrefix() + linkDescription.getVarId());
                if (variableEntity != null) {
                    map.put(Option.key(linkDescription.getDescription()), variableEntity.getTitle());
                }
            } else {
                String name = defaultIfEmpty(availableLinkEntry.getValue().getDescription(), converterEndpoint.getClusterDescription());
                map.put(Option.of(uuid.asKey(), name), "");
            }
            links.add(map);
        }
        entity.setAvailableLinks(links);
    }

    @Override
    public ZigBeeDeviceEntity deleteByEntityID(String entityID) {
        ZigBeeDeviceEntity entity = super.deleteByEntityID(entityID);
        ZigBeeCoordinatorHandler zigBeeCoordinatorHandler = entityContext.getSettingValue(ZigbeeCoordinatorHandlerSetting.class);
        zigBeeCoordinatorHandler.removeNode(new IeeeAddress(entity.getIeeeAddress()));

        return entity;
    }

    private void createVariableLink(ZigBeeConverterEndpoint zigBeeConverterEndpoint, ZigBeeDevice zigBeeDevice, String varGroup, String varName) {
        for (Scratch3Block scratch3Block : this.zigbeeBlocks) {
            if (scratch3Block instanceof Scratch3ZigbeeBlock) {
                Scratch3ZigbeeBlock scratch3ZigbeeBlock = (Scratch3ZigbeeBlock) scratch3Block;
                if (scratch3ZigbeeBlock.matchLink(zigBeeConverterEndpoint, zigBeeDevice)) {
                    scratch3ZigbeeBlock.getZigBeeLinkGenerator().handle(zigBeeConverterEndpoint, zigBeeDevice, varGroup, varName);
                    return;
                }
            }
        }
        throw new RuntimeException("Unable to create variable link. Cluster not match.");
    }

    private List<Map.Entry<ZigBeeConverterEndpoint, ZigBeeBaseChannelConverter>> getAvailableLinks(ZigBeeDevice zigBeeDevice) {
        return zigBeeDevice == null ? Collections.emptyList() : zigBeeDevice.getZigBeeConverterEndpoints().entrySet()
                .stream().filter(c -> c.getKey().getZigBeeConverter().linkType() != DeviceChannelLinkType.None)
                .collect(Collectors.toList());
    }
}
