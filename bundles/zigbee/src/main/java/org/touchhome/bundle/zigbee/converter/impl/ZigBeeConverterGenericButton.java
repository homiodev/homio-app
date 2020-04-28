package org.touchhome.bundle.zigbee.converter.impl;

import com.zsmartsystems.zigbee.CommandResult;
import com.zsmartsystems.zigbee.ZigBeeEndpoint;
import com.zsmartsystems.zigbee.zcl.*;
import com.zsmartsystems.zigbee.zcl.clusters.ZclScenesCluster;
import lombok.extern.log4j.Log4j2;
import org.touchhome.bundle.zigbee.converter.ZigBeeBaseChannelConverter;
import org.touchhome.bundle.zigbee.model.ButtonState;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import static java.lang.Integer.parseInt;
import static java.lang.Integer.toHexString;

/**
 * Generic converter for buttons (e.g., from remote controls).
 * <p>
 * This converter needs to be configured with the ZigBee commands that are triggered by the button presses. This is done
 * by channel properties that specify the endpoint, the cluster, the command ID, and (optionally) a command parameter.
 * <p>
 * As the configuration is done via channel properties, this converter is usable via static thing types only.
 */
@Log4j2
@ZigBeeConverter(name = "button", serverClusters = {ZclScenesCluster.CLUSTER_ID})
public class ZigBeeConverterGenericButton extends ZigBeeBaseChannelConverter
        implements ZclCommandListener, ZclAttributeListener {

    private static final String CLUSTER = "cluster_id";
    private static final String COMMAND = "command_id";
    private static final String PARAM_NAME = "parameter_name";
    private static final String PARAM_VALUE = "parameter_value";
    private static final String ATTRIBUTE_ID = "attribute_id";
    private static final String ATTRIBUTE_VALUE = "attribute_value";

    private Map<ButtonState.ButtonPressType, EventSpec> handledEvents = new EnumMap<>(ButtonState.ButtonPressType.class);
    private Set<ZclCluster> clientClusters = new HashSet<>();
    private Set<ZclCluster> serverClusters = new HashSet<>();

    private static String getParameterName(String parameterType, ButtonState.ButtonPressType buttonPressType) {
        return String.format("zigbee_%s_%s", buttonPressType, parameterType);
    }

    private static int parseId(String id) throws NumberFormatException {
        if (id.startsWith("0x")) {
            return parseInt(id.substring(2), 16);
        } else {
            return parseInt(id);
        }
    }

    @Override
    public synchronized boolean initializeConverter() {
        for (ButtonState.ButtonPressType buttonPressType : ButtonState.ButtonPressType.values()) {
            /*TODO: EventSpec eventSpec = parseEventSpec(channel.getProperties(), buttonPressType);
            if (eventSpec != null) {
                handledEvents.put(buttonPressType, eventSpec);
            }*/
        }

        if (handledEvents.isEmpty()) {
            log.error("{}: No command is specified for any of the possible button press types in channel {}",
                    endpoint.getIeeeAddress(), zigBeeConverterEndpoint);
            return false;
        }

        boolean allBindsSucceeded = true;

        for (EventSpec eventSpec : handledEvents.values()) {
            allBindsSucceeded &= eventSpec.bindCluster();
        }

        return allBindsSucceeded;
    }

    @Override
    public void disposeConverter() {
        for (ZclCluster clientCluster : clientClusters) {
            log.debug("{}: Closing client cluster {}", endpoint.getIeeeAddress(), endpoint.getEndpointId(), clientCluster.getClusterId());
            clientCluster.removeCommandListener(this);
        }

        for (ZclCluster serverCluster : serverClusters) {
            log.debug("{}: Closing server cluster {}", endpoint.getIeeeAddress(), endpoint.getEndpointId(), serverCluster.getClusterId());
            serverCluster.removeAttributeListener(this);
        }
    }

    @Override
    public boolean acceptEndpoint(ZigBeeEndpoint endpoint) {
        // This converter is used only for zigbeeRequireEndpoints specified in static thing types, and cannot be used to construct
        // zigbeeRequireEndpoints based on an endpoint alone.
        return false;
    }

    @Override
    public boolean commandReceived(ZclCommand command) {
        ButtonState.ButtonPressType buttonPressType = getButtonPressType(command);
        if (buttonPressType != null) {
            log.debug("{}: Matching ZigBee command for press type {} received: {}", endpoint.getIeeeAddress(), endpoint.getEndpointId(),
                    buttonPressType, command);
            updateChannelState(new ButtonState(buttonPressType));
            return true;
        }
        return false;
    }

    @Override
    public void attributeUpdated(ZclAttribute attribute, Object value) {
        ButtonState.ButtonPressType buttonPressType = getButtonPressType(attribute, value);
        if (buttonPressType != null) {
            log.debug("{}: Matching ZigBee attribute for press type {} received: {}", endpoint.getIeeeAddress(), endpoint.getEndpointId(),
                    buttonPressType, attribute);
            updateChannelState(new ButtonState(buttonPressType));
        }
    }

    private ButtonState.ButtonPressType getButtonPressType(ZclAttribute attribute, Object value) {
        return getButtonPressType(cs -> cs.matches(attribute, value));
    }

    private ButtonState.ButtonPressType getButtonPressType(ZclCommand command) {
        return getButtonPressType(cs -> cs.matches(command));
    }

    private ButtonState.ButtonPressType getButtonPressType(Predicate<EventSpec> predicate) {
        for (Entry<ButtonState.ButtonPressType, EventSpec> entry : handledEvents.entrySet()) {
            if (predicate.test(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    private EventSpec parseEventSpec(Map<String, String> properties, ButtonState.ButtonPressType pressType) {
        String clusterProperty = properties.get(getParameterName(CLUSTER, pressType));

        if (clusterProperty == null) {
            return null;
        }

        int clusterId;

        try {
            clusterId = parseId(clusterProperty);
        } catch (NumberFormatException e) {
            log.warn("{}: Could not parse cluster property {}", endpoint.getIeeeAddress(), endpoint.getEndpointId(), clusterProperty);
            return null;
        }

        boolean hasCommand = properties.containsKey(getParameterName(COMMAND, pressType));
        boolean hasAttribute = properties.containsKey(getParameterName(ATTRIBUTE_ID, pressType));

        if (hasCommand && hasAttribute) {
            log.warn("{}: Only one of command or attribute can be used", endpoint.getIeeeAddress());
            return null;
        }

        if (hasCommand) {
            return parseCommandSpec(clusterId, properties, pressType);
        } else {
            return parseAttributeReportSpec(clusterId, properties, pressType);
        }
    }

    private AttributeReportSpec parseAttributeReportSpec(int clusterId, Map<String, String> properties,
                                                         ButtonState.ButtonPressType pressType) {
        String attributeIdProperty = properties.get(getParameterName(ATTRIBUTE_ID, pressType));
        String attributeValue = properties.get(getParameterName(ATTRIBUTE_VALUE, pressType));

        if (attributeIdProperty == null) {
            log.warn("{}: Missing attribute id", endpoint.getIeeeAddress());
            return null;
        }

        Integer attributeId;

        try {
            attributeId = parseId(attributeIdProperty);
        } catch (NumberFormatException e) {
            log.warn("{}: Could not parse attribute property {}", endpoint.getIeeeAddress(), endpoint.getEndpointId(), attributeIdProperty);
            return null;
        }

        if (attributeValue == null) {
            log.warn("{}: No attribute value for attribute {} specified", endpoint.getIeeeAddress(), attributeId);
            return null;
        }

        return new AttributeReportSpec(clusterId, attributeId, attributeValue);
    }

    private CommandSpec parseCommandSpec(int clusterId, Map<String, String> properties, ButtonState.ButtonPressType pressType) {
        String commandProperty = properties.get(getParameterName(COMMAND, pressType));
        String commandParameterName = properties.get(getParameterName(PARAM_NAME, pressType));
        String commandParameterValue = properties.get(getParameterName(PARAM_VALUE, pressType));

        if (commandProperty == null) {
            log.warn("{}: Missing command", endpoint.getIeeeAddress());
            return null;
        }

        Integer commandId;

        try {
            commandId = parseId(commandProperty);
        } catch (NumberFormatException e) {
            log.warn("{}: Could not parse command property {}", endpoint.getIeeeAddress(), endpoint.getEndpointId(), commandProperty);
            return null;
        }

        if ((commandParameterName != null && commandParameterValue == null)
                || (commandParameterName == null && commandParameterValue != null)) {
            log.warn("{}: When specifiying a command parameter, both name and value must be specified",
                    endpoint.getIeeeAddress());
            return null;
        }

        return new CommandSpec(clusterId, commandId, commandParameterName, commandParameterValue);
    }

    private abstract class EventSpec {
        private final int clusterId;

        EventSpec(int clusterId) {
            this.clusterId = clusterId;
        }

        int getClusterId() {
            return clusterId;
        }

        abstract boolean matches(ZclCommand command);

        abstract boolean matches(ZclAttribute attribute, Object value);

        abstract boolean bindCluster();

        boolean bindCluster(String clusterType, Collection<ZclCluster> existingClusters, int clusterId,
                            Function<Integer, ZclCluster> getClusterById, Consumer<ZclCluster> registrationFunction) {
            if (existingClusters.stream().anyMatch(c -> c.getClusterId() == clusterId)) {
                // bind to each output cluster only once
                return true;
            }

            ZclCluster cluster = getClusterById.apply(clusterId);
            if (cluster == null) {
                log.error("{}: Error opening {} cluster {} on endpoint {}", endpoint.getIeeeAddress(), endpoint.getEndpointId(), clusterType,
                        clusterId, endpoint.getEndpointId());
                return false;
            }

            try {
                CommandResult bindResponse = bind(cluster).get();
                if (!bindResponse.isSuccess()) {
                    log.error("{}: Error 0x{} setting {} binding for cluster {}", endpoint.getIeeeAddress(), endpoint.getEndpointId(),
                            toHexString(bindResponse.getStatusCode()), clusterType, clusterId);
                }
            } catch (InterruptedException | ExecutionException e) {
                log.error("{}: Exception setting {} binding to cluster {}", endpoint.getIeeeAddress(), endpoint.getEndpointId(), clusterType,
                        clusterId, e);
            }

            registrationFunction.accept(cluster);
            existingClusters.add(cluster);
            return true;
        }
    }

    protected final class AttributeReportSpec extends EventSpec {
        private final Integer attributeId;
        private final String attributeValue;

        AttributeReportSpec(int clusterId, Integer attributeId, String attributeValue) {
            super(clusterId);
            this.attributeId = attributeId;
            this.attributeValue = attributeValue;
        }

        @Override
        boolean matches(ZclCommand command) {
            return false;
        }

        @Override
        boolean matches(ZclAttribute attribute, Object value) {
            if (attributeId == null) {
                return false;
            }
            boolean attributeIdMatches = attribute.getId() == attributeId;
            boolean attributeValueMatches = Objects.equals(Objects.toString(value), attributeValue);
            return attributeIdMatches && attributeValueMatches;
        }

        @Override
        boolean bindCluster() {
            return bindCluster("server", serverClusters, getClusterId(), endpoint::getInputCluster,
                    cluster -> cluster.addAttributeListener(ZigBeeConverterGenericButton.this));
        }
    }

    private final class CommandSpec extends EventSpec {
        private final Integer commandId;
        private final String commandParameterName;
        private final String commandParameterValue;

        private CommandSpec(int clusterId, Integer commandId, String commandParameterName,
                            String commandParameterValue) {
            super(clusterId);
            this.commandId = commandId;
            this.commandParameterName = commandParameterName;
            this.commandParameterValue = commandParameterValue;
        }

        private boolean matchesParameter(ZclCommand command) {
            String capitalizedParameterName = commandParameterName.substring(0, 1).toUpperCase()
                    + commandParameterName.substring(1);
            try {
                Method propertyGetter = command.getClass().getMethod("get" + capitalizedParameterName);
                Object result = propertyGetter.invoke(command);
                return Objects.equals(result.toString(), commandParameterValue);
            } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException
                    | InvocationTargetException e) {
                log.warn("{}: Could not read parameter {} for command {}", endpoint.getIeeeAddress(), endpoint.getEndpointId(),
                        commandParameterName, command, e);
                return false;
            }
        }

        @Override
        boolean matches(ZclCommand command) {
            if (commandId == null) {
                return false;
            }
            boolean commandIdMatches = command.getCommandId().intValue() == commandId;
            return commandIdMatches
                    && (commandParameterName == null || commandParameterValue == null || matchesParameter(command));
        }

        @Override
        boolean matches(ZclAttribute attribute, Object value) {
            return false;
        }

        @Override
        boolean bindCluster() {
            return bindCluster("client", clientClusters, getClusterId(), endpoint::getOutputCluster,
                    cluster -> cluster.addCommandListener(ZigBeeConverterGenericButton.this));
        }
    }
}
