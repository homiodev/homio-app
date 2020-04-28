package org.touchhome.bundle.zigbee;

import com.zsmartsystems.zigbee.*;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.touchhome.bundle.api.model.DeviceStatus;
import org.touchhome.bundle.api.notification.NotificationType;
import org.touchhome.bundle.zigbee.converter.ZigBeeBaseChannelConverter;
import org.touchhome.bundle.zigbee.converter.impl.ZigBeeConverterEndpoint;
import org.touchhome.bundle.zigbee.model.State;
import org.touchhome.bundle.zigbee.model.ZigBeeDeviceEntity;
import org.touchhome.bundle.zigbee.requireEndpoint.RequireEndpoint;
import org.touchhome.bundle.zigbee.requireEndpoint.ZigbeeRequireEndpoints;
import org.touchhome.bundle.zigbee.setting.ZigbeeStatusSetting;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

@Log4j2
public class ZigBeeDevice implements ZigBeeNetworkNodeListener, ZigBeeAnnounceListener {

    /**
     * The map of all the zigBeeConverterEndpoints defined for this thing
     */
    @Getter
    private final Map<ZigBeeConverterEndpoint, ZigBeeBaseChannelConverter> zigBeeConverterEndpoints = new ConcurrentHashMap<>();

    private final Object pollingSync = new Object();
    @Getter
    private final IeeeAddress nodeIeeeAddress;
    private ScheduledFuture<?> pollingJob = null;
    @Getter
    @Setter
    private int pollingPeriod = 86400;
    @Getter
    private ZigBeeNodeDescription zigBeeNodeDescription;
    private ZigBeeDiscoveryService discoveryService;
    @Getter
    private ZigBeeDeviceEntity zigBeeDeviceEntity;

    private Thread nodeDiscoveryThread;
    private BiConsumer<ZigBeeDeviceEntity, ZigBeeDeviceEntity> devicePropertiesUpdateListener;

    ZigBeeDevice(ZigBeeDiscoveryService discoveryService, IeeeAddress nodeIeeeAddress) {
        log.info("{}: Creating zigBee device", nodeIeeeAddress);
        this.discoveryService = discoveryService;

        this.zigBeeNodeDescription = new ZigBeeNodeDescription(nodeIeeeAddress);
        this.nodeIeeeAddress = nodeIeeeAddress;

        this.discoveryService.getCoordinatorHandlers().addNetworkNodeListener(this);
        this.discoveryService.getCoordinatorHandlers().addAnnounceListener(this);

        tryInitializeDevice(discoveryService.getEntityContext().getSettingValue(ZigbeeStatusSetting.class));

        // register listener for reset timer if any updates from any endpoint
        this.discoveryService.getDeviceUpdateListener().addIeeeAddressListener(this.nodeIeeeAddress.toString(), state ->
        {
            discoveryService.getZigBeeIsAliveTracker().resetTimer(this);
            updateStatus(DeviceStatus.ONLINE, "");
        });
    }

    void tryInitializeDevice(DeviceStatus coordinatorStatus) {
        if (coordinatorStatus != DeviceStatus.ONLINE) {
            log.trace("{}: Coordinator is unknown or not online.", nodeIeeeAddress);
            zigBeeNodeDescription.setNodeInitialized(false);
            updateStatus(DeviceStatus.OFFLINE, "Coordinator unknown status");
            stopPolling();
        } else if (!zigBeeNodeDescription.isNodeInitialized() && isInitializeFinished()) {
            log.debug("{}: Coordinator is ONLINE. Starting device initialisation.", nodeIeeeAddress);
            this.discoveryService.getCoordinatorHandlers().rediscoverNode(nodeIeeeAddress);
            initialiseZigBeeNode();
        }
    }

    public void initialiseZigBeeNode() {
        if (!isInitializeFinished()) {
            throw new IllegalStateException("Node <" + nodeIeeeAddress + "> initialization already started");
        }
        this.zigBeeNodeDescription.setNodeInitializationStatus(ZigBeeNodeDescription.NodeInitializationStatus.WaitForStart);
        this.discoveryService.getScheduler().schedule(this::doNodeInitialisation, 10, TimeUnit.MILLISECONDS);
    }

    private synchronized void doNodeInitialisation() {
        this.zigBeeNodeDescription.setNodeInitializationStatus(ZigBeeNodeDescription.NodeInitializationStatus.Started);
        try {
            log.info("{}: Initialize zigBee device", nodeIeeeAddress);
            ZigBeeNode node = this.discoveryService.getCoordinatorHandlers().getNode(nodeIeeeAddress);
            if (node == null) {
                log.debug("{}: Node not found", nodeIeeeAddress);
                updateStatus(DeviceStatus.OFFLINE, "zigbee.error.OFFLINE_NODE_NOT_FOUND");
                return;
            }

            // Check if discovery is complete and we know all the services the node supports
            if (!node.isDiscovered()) {
                log.debug("{}: Node has not finished discovery", nodeIeeeAddress);
                updateStatus(DeviceStatus.OFFLINE, "zigbee.error.OFFLINE_DISCOVERY_INCOMPLETE");
                return;
            }

            log.debug("{}: Start initialising ZigBee channels", nodeIeeeAddress);

            zigBeeConverterEndpoints.clear();

            // update node description in thread or not
            this.updateNodeDescription(node);

            this.addDevicePropertiesUpdateListener();

            List<ZigBeeConverterEndpoint> zigBeeConverterEndpoints = new ArrayList<>();
            // Dynamically create the zigBeeConverterEndpoints from the device
            // Process all the endpoints for this device and add all zigBeeConverterEndpoints as derived from the supported clusters
            for (ZigBeeEndpoint endpoint : this.discoveryService.getCoordinatorHandlers().getNodeEndpoints(nodeIeeeAddress)) {
                log.debug("{}: Checking endpoint {} zigBeeConverterEndpoints", nodeIeeeAddress, endpoint.getEndpointId());
                zigBeeConverterEndpoints.addAll(discoveryService.getZigBeeChannelConverterFactory().getZigBeeConverterEndpoints(endpoint));
            }
            log.debug("{}: Dynamically created {} zigBeeConverterEndpoints", nodeIeeeAddress, zigBeeConverterEndpoints.size());

            if (zigBeeNodeDescription.getModelIdentifier() != null) {
                zigBeeConverterEndpoints.addAll(findMissingRequireEndpointClusters(zigBeeConverterEndpoints));
            }
            for (ZigBeeConverterEndpoint zigBeeConverterEndpoint : zigBeeConverterEndpoints) {
                ZigBeeEndpoint endpoint = node.getEndpoint(zigBeeConverterEndpoint.getEndpointId());
                if (endpoint == null) {
                    int profileId = ZigBeeProfileType.ZIGBEE_HOME_AUTOMATION.getKey();
                    log.debug("{}: Creating statically defined device endpoint {} with profile {}", nodeIeeeAddress,
                            zigBeeConverterEndpoint.getEndpointId(), ZigBeeProfileType.getByValue(profileId));
                    endpoint = new ZigBeeEndpoint(node, zigBeeConverterEndpoint.getEndpointId());
                    endpoint.setProfileId(profileId);
                    node.addEndpoint(endpoint);
                }
            }

            if (!createZigBeeChannelConverters(zigBeeConverterEndpoints)) {
                log.error("{}: Unable to create zigbee converters", nodeIeeeAddress);
                return;
            }

            if (this.zigBeeConverterEndpoints.isEmpty()) {
                log.warn("{}: No supported clusters found", nodeIeeeAddress);
                updateStatus(DeviceStatus.OFFLINE, "zigbee.error.NO_CLUSTER_FOUND");
                return;
            }

            int expectedUpdatePeriod = getExpectedUpdatePeriod(this.zigBeeConverterEndpoints.values());
            if (!ZigbeeRequireEndpoints.get().isDisablePooling(zigBeeNodeDescription.getModelIdentifier())
                    && expectedUpdatePeriod != Integer.MAX_VALUE) {
                expectedUpdatePeriod = (expectedUpdatePeriod * 2) + 30;
                log.debug("{}: Setting ONLINE/OFFLINE timeout interval to: {}", nodeIeeeAddress, expectedUpdatePeriod);
                this.discoveryService.getZigBeeIsAliveTracker().addHandler(this, expectedUpdatePeriod);
            } else {
                log.debug("{}: Pooling track disabled. Not found clusters with reporting", nodeIeeeAddress);
            }

            // Update the binding table.
            // We're not doing anything with the information here, but we want it up to date so it's ready for use later.
            try {
                ZigBeeStatus zigBeeStatus = node.updateBindingTable().get();
                if (zigBeeStatus != ZigBeeStatus.SUCCESS) {
                    log.debug("{}: Error getting binding table. Actual status: <{}>", nodeIeeeAddress, zigBeeStatus);
                }
            } catch (InterruptedException | ExecutionException e) {
                log.error("{}: Exception getting binding table ", nodeIeeeAddress, e);
            }
            zigBeeNodeDescription.setNodeInitialized(true);
            zigBeeNodeDescription.setChannels(this.zigBeeConverterEndpoints);

            updateStatus(DeviceStatus.ONLINE, null);

            startPolling();

            log.debug("{}: Done initialising ZigBee device", nodeIeeeAddress);

            // Save the network state
            this.discoveryService.getCoordinatorHandlers().serializeNetwork(node.getIeeeAddress());
        } finally {
            this.zigBeeNodeDescription.setNodeInitializationStatus(ZigBeeNodeDescription.NodeInitializationStatus.Finished);
        }
    }

    private void updateNodeDescription(ZigBeeNode node) {
        this.zigBeeDeviceEntity = this.discoveryService.getEntityContext().getEntity(ZigBeeDeviceEntity.PREFIX + node.getIeeeAddress());
        if (zigBeeDeviceEntity == null || zigBeeDeviceEntity.getModelIdentifier() == null) {
            startDiscoveryNodeDescription(node, null, true);
        } else {
            startDiscoveryNodeDescription(node, zigBeeDeviceEntity.getModelIdentifier(), false);
        }
    }

    private void addDevicePropertiesUpdateListener() {
        // for remove old one if exists
        this.discoveryService.getEntityContext().removeEntityUpdateListener(this.zigBeeDeviceEntity.getEntityID(), this.devicePropertiesUpdateListener);
        this.devicePropertiesUpdateListener = (zb, old) -> {
            this.zigBeeDeviceEntity = zb;
            if (!zb.getPoolingPeriod().equals(old.getPoolingPeriod())
                    || !zb.getReportingTimeMin().equals(old.getReportingTimeMin())
                    || !zb.getReportingTimeMax().equals(old.getReportingTimeMax())) {
                getZigBeeConverterEndpoints().values().forEach(ZigBeeBaseChannelConverter::updateConfiguration);
            }
        };
        this.discoveryService.getEntityContext().addEntityUpdateListener(this.zigBeeDeviceEntity.getEntityID(), this.devicePropertiesUpdateListener);
    }

    private Collection<ZigBeeConverterEndpoint> findMissingRequireEndpointClusters(List<ZigBeeConverterEndpoint> zigBeeConverterEndpoints) {
        List<ZigBeeConverterEndpoint> endpoints = new ArrayList<>();
        ZigbeeRequireEndpoints.get().getRequireEndpoints(zigBeeNodeDescription.getModelIdentifier()).forEach(requireEndpoint -> {
            if (getRequireEndpoint(zigBeeConverterEndpoints, requireEndpoint) == null) {
                log.info("Add zigbee node <{}> missed require endpoint: <{}>", nodeIeeeAddress, requireEndpoint);
                endpoints.addAll(discoveryService.getZigBeeChannelConverterFactory().createConverterEndpoint(requireEndpoint, nodeIeeeAddress.toString()));
            }
        });

        return endpoints;
    }

    private ZigBeeConverterEndpoint getRequireEndpoint(List<ZigBeeConverterEndpoint> zigBeeConverterEndpoints, RequireEndpoint requireEndpoint) {
        return zigBeeConverterEndpoints.stream().filter(requireEndpoint::match).findAny().orElse(null);
    }

    private boolean createZigBeeChannelConverters(List<ZigBeeConverterEndpoint> zigBeeConverterEndpoints) {
        try {
            // Create the channel map to simplify processing incoming events
            for (ZigBeeConverterEndpoint zigBeeConverterEndpoint : zigBeeConverterEndpoints) {
                ZigBeeBaseChannelConverter handler = createZigBeeBaseChannelConverter(zigBeeConverterEndpoint);
                if (handler == null) {
                    log.debug("{}: No handler found for {}", nodeIeeeAddress, zigBeeConverterEndpoint);
                    continue;
                }

                if (!handler.initializeDevice()) {
                    log.info("{}: failed to initialise device converter <{}>", nodeIeeeAddress, zigBeeConverterEndpoint.toUUID());
                }

                if (!handler.initializeConverter()) {
                    log.info("{}: Channel {} failed to initialise converter", nodeIeeeAddress, zigBeeConverterEndpoint);
                    continue;
                }

                this.zigBeeConverterEndpoints.put(zigBeeConverterEndpoint, handler);

                if (handler.getPollingPeriod() < pollingPeriod) {
                    pollingPeriod = handler.getPollingPeriod();
                }
            }
        } catch (Exception e) {
            log.error("{}: Exception creating zigBeeConverterEndpoints ", nodeIeeeAddress, e);
            updateStatus(DeviceStatus.OFFLINE, "zigbee.error.HANDLER_INITIALIZING_ERROR");
            return false;
        }
        log.debug("{}: Channel initialisation complete", nodeIeeeAddress);
        return true;
    }

    private void updateStatus(DeviceStatus deviceStatus, String deviceStatusMessage) {
        if (this.zigBeeNodeDescription.getDeviceStatus() != deviceStatus) {
            this.zigBeeNodeDescription.setDeviceStatus(deviceStatus);
            this.zigBeeNodeDescription.setDeviceStatusMessage(deviceStatusMessage);
            this.discoveryService.getEntityContext().sendNotification(
                    "Zigbee device status",
                    this.nodeIeeeAddress.toString() + " - " + deviceStatus,
                    NotificationType.info);
        }
    }

    private int getExpectedUpdatePeriod(Collection<ZigBeeBaseChannelConverter> zigBeeBaseChannelConverters) {
        int minInterval = Integer.MAX_VALUE;
        for (ZigBeeBaseChannelConverter channelConverter : zigBeeBaseChannelConverters) {
            minInterval = Math.min(minInterval, channelConverter.getMinPoolingInterval());
        }
        return minInterval;
    }

    void aliveTimeoutReached() {
        updateStatus(DeviceStatus.OFFLINE, "zigbee.error.ALIVE_TIMEOUT_REACHED");
    }

    private ZigBeeBaseChannelConverter createZigBeeBaseChannelConverter(ZigBeeConverterEndpoint zigBeeConverterEndpoint) {
        ZigBeeNode node = this.discoveryService.getCoordinatorHandlers().getNode(nodeIeeeAddress);
        return discoveryService.getZigBeeChannelConverterFactory().createConverter(this,
                zigBeeConverterEndpoint, this.discoveryService.getCoordinatorHandlers(), node.getIeeeAddress());
    }

    void dispose() {
        log.debug("{}: Handler dispose.", nodeIeeeAddress);

        stopPolling();

        if (nodeIeeeAddress != null) {
            if (this.discoveryService.getCoordinatorHandlers() != null) {
                this.discoveryService.getCoordinatorHandlers().removeNetworkNodeListener(this);
                this.discoveryService.getCoordinatorHandlers().removeAnnounceListener(this);
            }
        }

        for (ZigBeeBaseChannelConverter channel : zigBeeConverterEndpoints.values()) {
            channel.disposeConverter();
        }
        zigBeeConverterEndpoints.clear();

        this.discoveryService.getZigBeeIsAliveTracker().removeHandler(this);

        zigBeeNodeDescription.setNodeInitialized(false);
    }

    private void stopPolling() {
        synchronized (pollingSync) {
            if (pollingJob != null) {
                pollingJob.cancel(true);
                pollingJob = null;
                log.debug("{}: Polling stopped", nodeIeeeAddress);
            }
        }
    }

    /**
     * Start polling channel updates
     */
    private void startPolling() {
        Runnable pollingRunnable = getPoolingThread();

        synchronized (pollingSync) {
            stopPolling();
            pollingPeriod = Math.max(pollingPeriod, 5);
            pollingPeriod = Math.min(pollingPeriod, 86400);

            // Polling starts almost immediately to get an immediate refresh
            // Add some random element to the period so that all things aren't synchronised
            int pollingPeriodMs = pollingPeriod * 1000 + new Random().nextInt(pollingPeriod * 100);
            pollingJob = this.discoveryService.getScheduler().scheduleAtFixedRate(pollingRunnable, new Random().nextInt(pollingPeriodMs),
                    pollingPeriodMs, TimeUnit.MILLISECONDS);
            log.debug("{}: Polling initialised at {}ms", nodeIeeeAddress, pollingPeriodMs);
        }
    }

    public Runnable getPoolingThread() {
        return () -> {
            try {
                log.info("{}: Polling started", nodeIeeeAddress);

                for (ZigBeeConverterEndpoint zigBeeConverterEndpoint : zigBeeConverterEndpoints.keySet()) {
                    log.debug("{}: Polling {}", nodeIeeeAddress, zigBeeConverterEndpoint);
                    ZigBeeBaseChannelConverter converter = zigBeeConverterEndpoints.get(zigBeeConverterEndpoint);
                    if (converter == null) {
                        log.debug("{}: Polling aborted as no converter found for {}", nodeIeeeAddress,
                                zigBeeConverterEndpoint);
                    } else {
                        converter.fireHandleRefresh();
                    }
                }
                log.info("{}: Polling done", nodeIeeeAddress);
            } catch (Exception e) {
                log.warn("{}: Polling aborted due to exception ", nodeIeeeAddress, e);
            }
        };
    }

    @Override
    public void deviceStatusUpdate(ZigBeeNodeStatus deviceStatus, Integer networkAddress, IeeeAddress ieeeAddress) {
        // A node has joined - or come back online
        if (!nodeIeeeAddress.equals(ieeeAddress)) {
            return;
        }
        // Use this to update channel information - eg bulb state will likely change when the device was powered off/on.
        startPolling();
    }

    @Override
    public void nodeAdded(ZigBeeNode node) {
        nodeUpdated(node);
    }

    @Override
    public void nodeUpdated(ZigBeeNode node) {
        if (!node.getIeeeAddress().equals(nodeIeeeAddress) || zigBeeNodeDescription.isNodeInitialized()) {
            return;
        }
        log.debug("{}: Node has been updated. Fire initialize it.", nodeIeeeAddress);
        if (isInitializeFinished()) {
            initialiseZigBeeNode();
        }
    }

    @Override
    public void nodeRemoved(ZigBeeNode node) {
        if (!node.getIeeeAddress().equals(nodeIeeeAddress)) {
            return;
        }
        updateStatus(DeviceStatus.OFFLINE, "zigbee.error.REMOVED_BY_DONGLE");
    }

    @Override
    public String toString() {
        return "ZigBeeDevice{" +
                "nodeIeeeAddress=" + nodeIeeeAddress +
                ", zigBeeNodeDescription=" + zigBeeNodeDescription +
                '}';
    }

    public void updateValue(ZigBeeConverterEndpoint zigBeeConverterEndpoint, State state, boolean pooling) {
        this.discoveryService.getDeviceUpdateListener().updateValue(this, zigBeeConverterEndpoint.toUUID(), state, pooling);
    }

    public void discoveryNodeDescription(String savedModelIdentifier) {
        ZigBeeNode node = this.discoveryService.getCoordinatorHandlers().getNode(nodeIeeeAddress);
        if (node == null) {
            throw new IllegalStateException("Unable to find node: <" + nodeIeeeAddress + ">");
        }
        startDiscoveryNodeDescription(node, savedModelIdentifier, false);
    }

    @SneakyThrows
    private void startDiscoveryNodeDescription(ZigBeeNode node, String savedModelIdentifier, boolean waitResponse) {
        if (nodeDiscoveryThread != null && nodeDiscoveryThread.isAlive()) {
            throw new IllegalStateException("ACTION.ALREADY_STARTED");
        }
        nodeDiscoveryThread = new Thread(() -> {
            this.zigBeeNodeDescription.updateFromNode(node);
            if (this.zigBeeNodeDescription.getModelIdentifier() == null) {
                this.zigBeeNodeDescription.setModelIdentifier(savedModelIdentifier);
            }
        });
        nodeDiscoveryThread.start();
        if (waitResponse) {
            nodeDiscoveryThread.join();
        }
    }

    public long getChannelCount(int clusterId) {
        return zigBeeNodeDescription.isNodeInitialized() ? zigBeeNodeDescription.getChannels().stream().filter(c -> c.getChannelUUID().getClusterId() == clusterId).count() : -1;
    }

    private boolean isInitializeFinished() {
        return zigBeeNodeDescription.getNodeInitializationStatus() == null || zigBeeNodeDescription.getNodeInitializationStatus().finished();
    }

    public ZigBeeConverterEndpoint getEndpointByClusterName(String clusterName) {
        return zigBeeConverterEndpoints.keySet().stream().filter(f -> f.getClusterName().equals(clusterName)).findAny().orElse(null);
    }
}
