package org.touchhome.bundle.zigbee;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.zsmartsystems.zigbee.IeeeAddress;
import com.zsmartsystems.zigbee.ZigBeeNode;
import com.zsmartsystems.zigbee.zcl.ZclAttribute;
import com.zsmartsystems.zigbee.zcl.clusters.ZclBasicCluster;
import com.zsmartsystems.zigbee.zcl.clusters.ZclOtaUpgradeCluster;
import com.zsmartsystems.zigbee.zdo.field.NeighborTable;
import com.zsmartsystems.zigbee.zdo.field.NodeDescriptor;
import com.zsmartsystems.zigbee.zdo.field.PowerDescriptor;
import com.zsmartsystems.zigbee.zdo.field.RoutingTable;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;
import org.touchhome.bundle.api.model.DeviceStatus;
import org.touchhome.bundle.zigbee.converter.ZigBeeBaseChannelConverter;
import org.touchhome.bundle.zigbee.converter.impl.ZigBeeConverterEndpoint;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.zsmartsystems.zigbee.zcl.clusters.ZclBasicCluster.*;

@Log4j2
@Getter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ZigBeeNodeDescription {

    private final String ieeeAddress;

    @Setter
    @JsonIgnore
    private Integer expectedUpdateInterval;
    @Setter
    @JsonIgnore
    private Long expectedUpdateIntervalTimer;

    public String getMaxTimeoutBeforeOfflineNode() {
        return expectedUpdateInterval == null ? "Not set" : TimeUnit.SECONDS.toMinutes(expectedUpdateInterval) + "min";
    }

    public String getTimeoutBeforeOfflineNode() {
        if (expectedUpdateIntervalTimer == null) {
            return "Not set";
        }
        String min = String.valueOf(TimeUnit.SECONDS.toMinutes(expectedUpdateInterval - (System.currentTimeMillis() - expectedUpdateIntervalTimer) / 1000));
        return "Expired in: " + min + "min";
    }

    private NodeDescriptor.LogicalType logicalType;
    private Integer networkAddress;
    private NodeDescriptor nodeDescription;
    private PowerDescriptor powerDescriptor;
    private Set<Integer> associatedDevices;
    private Date lastUpdateTime;
    private Set<NeighborTable> neighbors;
    private Collection<RoutingTable> routes;
    private String dateCode;
    private Integer zclVersion;
    private Integer stackVersion;
    @Setter
    private String modelIdentifier;
    private Integer hwVersion;
    private Integer appVersion;
    private String manufacturer;
    private String firmwareVersion;

    @Setter
    private String deviceStatusMessage;
    @Setter
    private DeviceStatus deviceStatus = DeviceStatus.UNKNOWN;
    private FetchInfoStatus fetchInfoStatus = FetchInfoStatus.UNKNOWN;

    private Collection<ChannelDescription> channels;

    @Setter
    private boolean nodeInitialized;

    @Setter
    private NodeInitializationStatus nodeInitializationStatus;

    public enum NodeInitializationStatus {
        WaitForStart, Started, Finished;

        public boolean finished() {
            return this == Finished;
        }
    }

    ZigBeeNodeDescription(IeeeAddress ieeeAddress) {
        this.ieeeAddress = ieeeAddress.toString();
    }

    @SneakyThrows
    void updateFromNode(ZigBeeNode node) {
        log.info("Starting fetch info from ZigbeeNode: <{}>", node.getIeeeAddress().toString());
        this.fetchInfoStatus = FetchInfoStatus.STARTED;
        this.logicalType = node.getLogicalType();
        this.networkAddress = node.getNetworkAddress();
        this.powerDescriptor = node.getPowerDescriptor();
        this.nodeDescription = node.getNodeDescriptor();
        this.associatedDevices = node.getAssociatedDevices();
        this.lastUpdateTime = node.getLastUpdateTime();
        this.neighbors = node.getNeighbors();
        this.routes = node.getRoutes();

        this.addPropertiesFromOtaCluster(node);

        ZclBasicCluster basicCluster = (ZclBasicCluster) node.getEndpoints().stream()
                .map(ep -> ep.getInputCluster(ZclBasicCluster.CLUSTER_ID)).filter(Objects::nonNull).findFirst()
                .orElse(null);

        if (basicCluster == null) {
            this.fetchInfoStatus = FetchInfoStatus.NOT_COMPLETED;
            log.warn("Fetch info from ZigbeeNode: <{}> not completed", node.getIeeeAddress().toString());
            return;
        }

        basicCluster.readAttributes(Arrays.asList(ATTR_MANUFACTURERNAME, ATTR_MODELIDENTIFIER, ATTR_HWVERSION,
                ATTR_APPLICATIONVERSION, ATTR_STACKVERSION, ATTR_ZCLVERSION, ATTR_DATECODE)).get();

        this.manufacturer = (String) basicCluster.getAttribute(ATTR_MANUFACTURERNAME).readValue(Long.MAX_VALUE);
        this.modelIdentifier = (String) basicCluster.getAttribute(ATTR_MODELIDENTIFIER).readValue(Long.MAX_VALUE);
        this.hwVersion = (Integer) basicCluster.getAttribute(ATTR_HWVERSION).readValue(Long.MAX_VALUE);
        this.appVersion = (Integer) basicCluster.getAttribute(ATTR_APPLICATIONVERSION).readValue(Long.MAX_VALUE);
        this.stackVersion = (Integer) basicCluster.getAttribute(ATTR_STACKVERSION).readValue(Long.MAX_VALUE);
        this.zclVersion = (Integer) basicCluster.getAttribute(ATTR_ZCLVERSION).readValue(Long.MAX_VALUE);
        this.dateCode = (String) basicCluster.getAttribute(ATTR_DATECODE).readValue(Long.MAX_VALUE);

        log.info("Finished fetch info from ZigbeeNode: <{}>", node.getIeeeAddress().toString());
        this.fetchInfoStatus = FetchInfoStatus.FINISHED;
    }

    private void addPropertiesFromOtaCluster(ZigBeeNode node) {
        ZclOtaUpgradeCluster otaCluster = (ZclOtaUpgradeCluster) node.getEndpoints().stream()
                .map(ep -> ep.getOutputCluster(ZclOtaUpgradeCluster.CLUSTER_ID)).filter(Objects::nonNull).findFirst()
                .orElse(null);

        if (otaCluster != null) {
            log.debug("{}: ZigBee node property discovery using OTA cluster on endpoint {}", node.getIeeeAddress(),
                    otaCluster.getZigBeeAddress());

            ZclAttribute attribute = otaCluster.getAttribute(ZclOtaUpgradeCluster.ATTR_CURRENTFILEVERSION);
            Object fileVersion = attribute.readValue(Long.MAX_VALUE);
            if (fileVersion != null) {
                this.firmwareVersion = String.format("0x%08X", fileVersion);
            } else {
                log.debug("{}: Could not get OTA firmware version from device", node.getIeeeAddress());
            }
        } else {
            log.debug("{}: Node doesn't support OTA cluster", node.getIeeeAddress());
        }
    }

    void setChannels(Map<ZigBeeConverterEndpoint, ZigBeeBaseChannelConverter> zigBeeConverterEndpoints) {
        this.channels = zigBeeConverterEndpoints.entrySet().stream().map(e -> new ChannelDescription(e.getKey(), e.getValue())).collect(Collectors.toList());
    }

    public enum FetchInfoStatus {
        UNKNOWN, STARTED, FINISHED, NOT_COMPLETED
    }

    @Getter
    public static class ChannelDescription {

        private final String name;
        private final int pollingPeriod;
        private final int minimalReportingPeriod;
        private final ZigBeeDeviceStateUUID channelUUID;

        ChannelDescription(ZigBeeConverterEndpoint zigBeeConverterEndpoint, ZigBeeBaseChannelConverter channel) {
            this.name = channel.getClass().getSimpleName();
            this.pollingPeriod = channel.getPollingPeriod();
            this.minimalReportingPeriod = channel.getMinimalReportingPeriod();
            this.channelUUID = zigBeeConverterEndpoint.toUUID();
        }
    }
}
