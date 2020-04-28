package org.touchhome.bundle.zigbee.workspace;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Component;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.scratch.WorkspaceEventListener;
import org.touchhome.bundle.zigbee.ZigBeeDevice;
import org.touchhome.bundle.zigbee.ZigBeeDeviceStateUUID;
import org.touchhome.bundle.zigbee.model.State;
import org.touchhome.bundle.zigbee.setting.ZigbeeLogEventsButtonsSetting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

@Log4j2
@Getter
@Component
@RequiredArgsConstructor
public final class ZigBeeDeviceUpdateValueListener implements WorkspaceEventListener {

    private final EntityContext entityContext;

    private final Map<Integer, Pair<Function<ScratchDeviceState, String>, Boolean>> deviceStateDescribeEventHandlerByClusterID = new HashMap<>();
    private final Map<String, Pair<Function<ScratchDeviceState, String>, Boolean>> deviceStateDescribeEventHandlerByClusterName = new HashMap<>();
    private final Map<String, Pair<Function<ScratchDeviceState, String>, Boolean>> deviceStateDescribeEventHandlerByModelIdentifier = new HashMap<>();

    private final Map<String, List<Consumer<ScratchDeviceState>>> miDeviceListeners = new HashMap<>();
    private final Map<String, List<Consumer<ScratchDeviceState>>> ieeeAddressListeners = new HashMap<>();
    private final Map<ZigBeeDeviceStateUUID, LinkDescription> linkListeners = new HashMap<>();

    private final Map<String, Map<ZigBeeDeviceStateUUID, State>> lastDeviceStates = new HashMap<>();
    private final Map<ZigBeeDeviceStateUUID, ScratchDeviceState> deviceStates = new HashMap<>();

    private final Map<String, Holder> warehouse = new HashMap<>();

    public void updateValue(ZigBeeDevice zigBeeDevice, ZigBeeDeviceStateUUID uuid, State state, boolean pooling) {
        String ieeeAddress = uuid.getIeeeAddress();
        lastDeviceStates.putIfAbsent(ieeeAddress, new HashMap<>());
        lastDeviceStates.get(ieeeAddress).put(uuid, state);

        ScratchDeviceState scratchDeviceState = new ScratchDeviceState(zigBeeDevice, uuid, state);
        deviceStates.put(uuid, scratchDeviceState);

        // update links
        LinkDescription linkDescription = linkListeners.get(uuid);
        if (linkDescription != null) {
            linkDescription.listener.accept(scratchDeviceState);
        }

        // fire listeners for each tab
        for (Holder holder : warehouse.values()) {
            fireListeners(holder, zigBeeDevice, uuid, pooling);
        }

        // call ieeeAddress listener
        ieeeAddressListeners.get(uuid.getIeeeAddress()).forEach(h -> h.accept(scratchDeviceState));

        // call model identifier listeners
        if (zigBeeDevice.getZigBeeNodeDescription().getModelIdentifier() != null && !miDeviceListeners.isEmpty()) {
            for (Map.Entry<String, List<Consumer<ScratchDeviceState>>> entry : miDeviceListeners.entrySet()) {
                if (zigBeeDevice.getZigBeeNodeDescription().getModelIdentifier().startsWith(entry.getKey())) {
                    for (Consumer<ScratchDeviceState> consumer : entry.getValue()) {
                        consumer.accept(scratchDeviceState);
                    }
                }
            }
        }

        logZigbeeEvent(zigBeeDevice, uuid, scratchDeviceState);
    }

    private void fireListeners(Holder holder, ZigBeeDevice zigBeeDevice, ZigBeeDeviceStateUUID uuid, boolean pooling) {
        ScratchDeviceState scratchDeviceState = deviceStates.get(uuid);

        if (zigBeeDevice.getZigBeeNodeDescription().isNodeInitialized() && !pooling) {
            List<Consumer<ScratchDeviceState>> consumers = holder.deviceListeners.get(uuid);
            if (consumers != null) {
                for (Consumer<ScratchDeviceState> consumer : consumers) {
                    consumer.accept(scratchDeviceState);
                }
            }
        }
    }

    private void logZigbeeEvent(ZigBeeDevice zigBeeDevice, ZigBeeDeviceStateUUID uuid, ScratchDeviceState scratchDeviceState) {
        if (entityContext.getSettingValue(ZigbeeLogEventsButtonsSetting.class)) {
            if (logZigbeeEvent(scratchDeviceState, deviceStateDescribeEventHandlerByClusterID.get(uuid.getClusterId()))) {
                return;
            }
            if (logZigbeeEvent(scratchDeviceState, deviceStateDescribeEventHandlerByClusterName.get(uuid.getClusterName()))) {
                return;
            }
            String modelIdentifier = zigBeeDevice.getZigBeeNodeDescription().getModelIdentifier();
            if (modelIdentifier != null) {
                for (Map.Entry<String, Pair<Function<ScratchDeviceState, String>, Boolean>> entry :
                        deviceStateDescribeEventHandlerByModelIdentifier.entrySet()) {
                    if (modelIdentifier.startsWith(entry.getKey())) {
                        logZigBeeEvent(entry.getValue(), scratchDeviceState);
                        return;
                    }
                }
            }
            logZigBeeEvent(Pair.of(state -> "Event", true), scratchDeviceState);
        }
    }

    private boolean logZigbeeEvent(ScratchDeviceState scratchDeviceState, Pair<Function<ScratchDeviceState, String>, Boolean> handler) {
        if (handler != null) {
            logZigBeeEvent(handler, scratchDeviceState);
            return true;
        }
        return false;
    }

    private void logZigBeeEvent(Pair<Function<ScratchDeviceState, String>, Boolean> consumer, ScratchDeviceState scratchDeviceState) {
        String value = consumer.getKey().apply(scratchDeviceState);
        if (consumer.getValue()) {
            value += ": " + scratchDeviceState.getState() + " (" + scratchDeviceState.getUuid().getClusterName() + ")";
        }
        log.info("ZigBee <{}>, event: {}", scratchDeviceState.getZigBeeDevice().getNodeIeeeAddress(), value);
    }

    void addDescribeHandlerByClusterId(Integer clusterID, Function<ScratchDeviceState, String> consumer, boolean logState) {
        deviceStateDescribeEventHandlerByClusterID.put(clusterID, Pair.of(consumer, logState));
    }

    public void addDescribeHandlerByClusterName(String clusterName, Function<ScratchDeviceState, String> consumer, boolean logState) {
        deviceStateDescribeEventHandlerByClusterName.put(clusterName, Pair.of(consumer, logState));
    }

    public void addDescribeHandlerByModel(String modelIdentifier, Function<ScratchDeviceState, String> consumer, boolean logState) {
        deviceStateDescribeEventHandlerByModelIdentifier.put(modelIdentifier, Pair.of(consumer, logState));
    }

    public ScratchDeviceState getDeviceState(ZigBeeDeviceStateUUID zigBeeDeviceStateUUID, ZigBeeDeviceStateUUID... options) {
        ScratchDeviceState deviceState = deviceStates.get(zigBeeDeviceStateUUID);
        for (ZigBeeDeviceStateUUID stateUUID : options) {
            ScratchDeviceState optionalState = deviceStates.get(stateUUID);
            if (deviceState == null || (optionalState != null && deviceState.getDate() < optionalState.getDate())) {
                deviceState = optionalState;
            }
        }
        return deviceState;
    }

    public void addListener(ZigBeeDeviceStateUUID zigBeeDeviceStateUUID, Consumer<ScratchDeviceState> listener) {
        Holder holder = warehouse.get(Thread.currentThread().getName());
        holder.deviceListeners.putIfAbsent(zigBeeDeviceStateUUID, new ArrayList<>());
        holder.deviceListeners.get(zigBeeDeviceStateUUID).add(listener);
    }

    public void addIeeeAddressListener(String ieeeAddress, Consumer<ScratchDeviceState> listener) {
        ieeeAddressListeners.putIfAbsent(ieeeAddress, new ArrayList<>());
        ieeeAddressListeners.get(ieeeAddress).add(listener);
    }

    void addModelIdentifierListener(String modelIdentifier, Consumer<ScratchDeviceState> listener) {
        miDeviceListeners.putIfAbsent(modelIdentifier, new ArrayList<>());
        miDeviceListeners.get(modelIdentifier).add(listener);
    }

    void addLinkListener(ZigBeeDeviceStateUUID zigBeeDeviceStateUUID, String varId, String description, Consumer<ScratchDeviceState> listener) {
        linkListeners.put(zigBeeDeviceStateUUID, new LinkDescription(varId, description, listener));
    }

    @Override
    public void release(String id) {
        linkListeners.clear();
        warehouse.putIfAbsent(id, new Holder());
        warehouse.get(id).deviceListeners.clear();
    }

    public Map<ZigBeeDeviceStateUUID, State> getDeviceStates(String ieeeAddress) {
        return this.lastDeviceStates.get(ieeeAddress);
    }

    @Getter
    @AllArgsConstructor
    public static class LinkDescription {
        String varId;
        String description;
        Consumer<ScratchDeviceState> listener;
    }

    private class Holder {
        private Map<ZigBeeDeviceStateUUID, List<Consumer<ScratchDeviceState>>> deviceListeners = new ConcurrentHashMap<>();
    }
}
