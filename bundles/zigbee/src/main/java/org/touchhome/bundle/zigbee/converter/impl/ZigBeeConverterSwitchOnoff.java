package org.touchhome.bundle.zigbee.converter.impl;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.ZigBeeCommand;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.*;
import com.zsmartsystems.zigbee.zcl.clusters.ZclOnOffCluster;
import com.zsmartsystems.zigbee.zcl.clusters.onoff.*;
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType;
import lombok.extern.log4j.Log4j2;
import org.touchhome.bundle.api.link.DeviceChannelLinkType;
import org.touchhome.bundle.zigbee.converter.ZigBeeBaseChannelConverter;
import org.touchhome.bundle.zigbee.converter.impl.config.ZclOnOffSwitchConfig;
import org.touchhome.bundle.zigbee.model.OnOffType;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This channel supports changes through attribute updates, and also through received commands. This allows a switch
 * that is not connected to a load to send commands, or a switch that is connected to a load to send status (or both!).
 */
@Log4j2
@ZigBeeConverter(name = "zigbee:switch_onoff",
        linkType = DeviceChannelLinkType.Boolean,
        serverClusters = {ZclOnOffCluster.CLUSTER_ID},
        clientClusters = {ZclOnOffCluster.CLUSTER_ID})
public class ZigBeeConverterSwitchOnoff extends ZigBeeBaseChannelConverter
        implements ZclAttributeListener, ZclCommandListener {

    private final AtomicBoolean currentOnOffState = new AtomicBoolean(true);
    private ZclOnOffCluster clusterOnOffClient;
    private ZclOnOffCluster clusterOnOffServer;
    private ZclAttribute attributeServer;
    private ZclOnOffSwitchConfig configOnOff;
    private ScheduledExecutorService updateScheduler;
    private ScheduledFuture<?> updateTimer = null;

    @Override
    public boolean initializeDevice() {
        pollingPeriod = REPORTING_PERIOD_DEFAULT_MAX;
        ZclOnOffCluster clientCluster = (ZclOnOffCluster) endpoint.getOutputCluster(ZclOnOffCluster.CLUSTER_ID);
        ZclOnOffCluster serverCluster = (ZclOnOffCluster) endpoint.getInputCluster(ZclOnOffCluster.CLUSTER_ID);
        if (clientCluster == null && serverCluster == null) {
            log.error("{}: Error opening device on/off controls", endpoint.getIeeeAddress());
            return false;
        }

        if (serverCluster != null) {
            try {
                CommandResult bindResponse = bind(serverCluster).get();
                if (bindResponse.isSuccess()) {
                    updateServerPoolingPeriod(serverCluster, ZclOnOffCluster.ATTR_ONOFF, false);
                } else {
                    log.debug("{}: Error 0x{} setting server binding", endpoint.getIeeeAddress(), Integer.toHexString(bindResponse.getStatusCode()));
                    pollingPeriod = POLLING_PERIOD_HIGH;
                }
            } catch (InterruptedException | ExecutionException e) {
                log.error("{}: Exception setting reporting ", endpoint.getIeeeAddress(), e);
            }
        }

        if (clientCluster != null) {
            try {
                CommandResult bindResponse = bind(clientCluster).get();
                if (!bindResponse.isSuccess()) {
                    log.error("{}: Error 0x{} setting client binding", endpoint.getIeeeAddress(),
                            Integer.toHexString(bindResponse.getStatusCode()));
                }
            } catch (InterruptedException | ExecutionException e) {
                log.error("{}: Exception setting binding ", endpoint.getIeeeAddress(), e);
            }
        }

        return true;
    }

    @Override
    public boolean initializeConverter() {
        updateScheduler = Executors.newSingleThreadScheduledExecutor();

        clusterOnOffClient = (ZclOnOffCluster) endpoint.getOutputCluster(ZclOnOffCluster.CLUSTER_ID);
        clusterOnOffServer = (ZclOnOffCluster) endpoint.getInputCluster(ZclOnOffCluster.CLUSTER_ID);
        if (clusterOnOffClient == null && clusterOnOffServer == null) {
            log.error("{}: Error opening device on/off controls", endpoint.getIeeeAddress());
            return false;
        }


        if (clusterOnOffServer != null) {
            // Add the listener
            clusterOnOffServer.addAttributeListener(this);
            configOnOff = new ZclOnOffSwitchConfig();
            configOnOff.initialize(clusterOnOffServer);
        }

        if (clusterOnOffClient != null) {
            // Add the command listener
            clusterOnOffClient.addCommandListener(this);
        }

        if (clusterOnOffServer != null) {
            // Add the listener
            clusterOnOffServer.addAttributeListener(this);
            attributeServer = clusterOnOffServer.getAttribute(ZclOnOffCluster.ATTR_ONOFF);
        }

        return true;
    }

    @Override
    public void disposeConverter() {
        log.debug("{}: Closing device on/off cluster", endpoint.getIeeeAddress());

        if (clusterOnOffClient != null) {
            clusterOnOffClient.removeCommandListener(this);
        }
        if (clusterOnOffServer != null) {
            clusterOnOffServer.removeAttributeListener(this);
        }

        stopOffTimer();
        updateScheduler.shutdownNow();
    }

    @Override
    public int getPollingPeriod() {
        if (clusterOnOffServer != null) {
            return zigBeeDevice.getZigBeeDeviceEntity().getPoolingPeriod();
        }
        return Integer.MAX_VALUE;
    }

    @Override
    protected void handleRefresh() {
        if (attributeServer != null) {
            attributeServer.readValue(0);
        }
    }

    @Override
    public Future<CommandResult> handleCommand(final ZigBeeCommand command) {
        if (clusterOnOffServer == null) {
            log.warn("{}: OnOff converter is not linked to a server and cannot accept commands", endpoint.getIeeeAddress());
            return null;
        }

        if (command instanceof ZclOnOffCommand) {
            return clusterOnOffServer.sendCommand((ZclOnOffCommand) command);
        }
        return null;
    }

    @Override
    public boolean acceptEndpoint(ZigBeeEndpoint endpoint) {
        if (endpoint.getInputCluster(ZclOnOffCluster.CLUSTER_ID) == null
                && endpoint.getOutputCluster(ZclOnOffCluster.CLUSTER_ID) == null) {
            log.trace("{}: OnOff cluster not found", endpoint.getIeeeAddress());
            return false;
        }

        return true;
    }

    @Override
    public String getDescription() {
        switch (this.endpoint.getEndpointId()) {
            case 1:
                return "On/Off (Left button)";
            case 2:
                return "On/Off (Right button)";
            case 3:
                return "On/Off (Both button)";
            default:
                return "On/Off";
        }
    }

    @Override
    public void updateConfiguration() {
        if (clusterOnOffServer == null) {
            return;
        }
        try {
            updateServerPoolingPeriod(clusterOnOffServer, ZclOnOffCluster.ATTR_ONOFF, true);
        } catch (InterruptedException | ExecutionException e) {
            log.debug("{}: OnOff exception setting reporting", endpoint.getIeeeAddress(), e);
        }

        configOnOff.updateConfiguration();
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute, Object val) {
        log.debug("{}/{}: ZigBee attribute reports {}", endpoint.getIeeeAddress(), endpoint.getEndpointId(), attribute);
        if (attribute.getCluster() == ZclClusterType.ON_OFF && attribute.getId() == ZclOnOffCluster.ATTR_ONOFF) {
            Boolean value = (Boolean) val;
            if (value != null && value) {
                updateChannelState(OnOffType.ON);
            } else {
                updateChannelState(OnOffType.OFF);
            }
        }
    }

    @Override
    public boolean commandReceived(ZclCommand command) {
        log.debug("{}: ZigBee command received {}", endpoint.getIeeeAddress(), endpoint.getEndpointId(), command);
        if (command instanceof OnCommand) {
            currentOnOffState.set(true);
            updateChannelState(OnOffType.ON);
            clusterOnOffClient.sendDefaultResponse(command, ZclStatus.SUCCESS);
            return true;
        }
        if (command instanceof OnWithTimedOffCommand) {
            currentOnOffState.set(true);
            updateChannelState(OnOffType.ON);
            OnWithTimedOffCommand timedCommand = (OnWithTimedOffCommand) command;
            clusterOnOffClient.sendDefaultResponse(command, ZclStatus.SUCCESS);
            startOffTimer(timedCommand.getOnTime() * 100);
            return true;
        }
        if (command instanceof OffCommand || command instanceof OffWithEffectCommand) {
            currentOnOffState.set(false);
            updateChannelState(OnOffType.OFF);
            clusterOnOffClient.sendDefaultResponse(command, ZclStatus.SUCCESS);
            return true;
        }
        if (command instanceof ToggleCommand) {
            currentOnOffState.set(!currentOnOffState.get());
            updateChannelState(currentOnOffState.get() ? OnOffType.ON : OnOffType.OFF);
            clusterOnOffClient.sendDefaultResponse(command, ZclStatus.SUCCESS);
            return true;
        }

        return false;
    }

    private void stopOffTimer() {
        if (updateTimer != null) {
            updateTimer.cancel(true);
            updateTimer = null;
        }
    }

    private void startOffTimer(int delay) {
        stopOffTimer();

        updateTimer = updateScheduler.schedule(() -> {
            log.debug("{}: OnOff auto OFF timer expired", endpoint.getIeeeAddress());
            updateChannelState(OnOffType.OFF);
            updateTimer = null;
        }, delay, TimeUnit.MILLISECONDS);
    }
}
