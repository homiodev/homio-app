package org.homio.addon.tasmota;

import static org.homio.api.model.Status.OFFLINE;
import static org.homio.api.model.Status.ONLINE;
import static org.homio.api.model.Status.UNKNOWN;
import static org.homio.api.model.endpoint.DeviceEndpoint.ENDPOINT_DEVICE_STATUS;
import static org.homio.api.model.endpoint.DeviceEndpoint.ENDPOINT_LAST_SEEN;
import static org.homio.api.util.JsonUtils.OBJECT_MAPPER;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.homio.addon.tasmota.TasmotaProjectService.MatchDeviceData;
import org.homio.api.Context;
import org.homio.api.model.Icon;
import org.homio.api.model.OptionModel;
import org.homio.api.model.Status;
import org.homio.api.model.device.ConfigDeviceDefinition;
import org.homio.api.model.device.ConfigDeviceDefinitionService;
import org.homio.api.model.device.ConfigDeviceEndpoint;
import org.homio.api.model.endpoint.DeviceEndpoint;
import org.homio.api.model.endpoint.DeviceEndpoint.EndpointType;
import org.homio.api.service.EntityService.ServiceInstance;
import org.homio.api.state.DecimalType;
import org.homio.api.state.OnOffType;
import org.homio.api.state.StringType;
import org.homio.api.util.Lang;
import org.jetbrains.annotations.NotNull;

public class ESPHomeDeviceService extends ServiceInstance<ESPHomeDeviceEntity> {

    public static final ConfigDeviceDefinitionService CONFIG_DEVICE_SERVICE =
            new ConfigDeviceDefinitionService("tasmota-devices.json");


    private final @Getter Map<String, TasmotaEndpoint> endpoints = new ConcurrentHashMap<>();
    private List<ConfigDeviceDefinition> models;
    private final @Getter ObjectNode attributes = OBJECT_MAPPER.createObjectNode();
    private @Getter JsonNode telemetry = OBJECT_MAPPER.createObjectNode();

    public ESPHomeDeviceService(@NotNull Context context, @NotNull ESPHomeDeviceEntity entity) {
        super(context, entity, false);
        attributes.set("modules", OBJECT_MAPPER.createObjectNode());
        attributes.set("gpios", OBJECT_MAPPER.createObjectNode());
        attributes.set("gpio", OBJECT_MAPPER.createObjectNode());

        context.ui().updateItem(entity);
        context.ui().toastr().success(Lang.getServerMessage("ENTITY_CREATED", "${%s}".formatted(entity.getTitle())));
    }

    public void put(String key, String value) {
        if (key.equals("LWT")) {
            setDeviceStatus("Online".equals(value) ? Status.ONLINE : Status.OFFLINE);
        }
        attributes.put(key, value);
    }

    private void setDeviceStatus(Status status) {
        entity.setStatus(status);
        endpoints.get(ENDPOINT_DEVICE_STATUS).setValue(new StringType(status.name()), true);
    }

    private void createRequireEndpoints() {
        addEndpointOptional(ENDPOINT_LAST_SEEN, key -> new TasmotaEndpoint(ENDPOINT_LAST_SEEN, EndpointType.number, entity));

        addEndpointOptional(ENDPOINT_DEVICE_STATUS, key ->
            new TasmotaEndpoint(ENDPOINT_DEVICE_STATUS, EndpointType.select, entity, builder ->
                builder.setRange(OptionModel.list(Status.set(ONLINE, OFFLINE, UNKNOWN)))));

        for (ConfigDeviceEndpoint endpoint : CONFIG_DEVICE_SERVICE.getDeviceEndpoints().values()) {
            addEndpointOptional(endpoint.getName(), key -> buildEndpoint(endpoint, key));
        }
    }

    private TasmotaEndpoint buildEndpoint(ConfigDeviceEndpoint endpoint, String key) {
        EndpointType endpointType = EndpointType.valueOf(endpoint.getMetadata().optString("type", "string"));
        return new TasmotaEndpoint(key, endpointType, entity, endpoint1 -> {
            String path = endpoint.getMetadata().optString("path", null);
            if (path != null) {
                String[] pathItems = path.split("/");
                endpoint1.setDataReader(payload -> {
                    for (String pathItem : pathItems) {
                        payload = payload.path(pathItem);
                    }
                    if (!payload.isMissingNode()) {
                        return endpointType.getNodeReader().apply(payload);
                    }
                    return null;
                });
            }
        });
    }

    @SneakyThrows
    public void mqttUpdate(JsonNode payload, MatchDeviceData data) {
        if ("tele".equals(data.prefix()) || "stat".equals(data.prefix())) {
            payload = handlePayload(data, payload);
            if (payload != null) {
                updateMqtt(payload);
            }
        }
    }

    private void addPowerEntrypoints(String key) {
        addEndpointOptional(key, s ->
            new TasmotaEndpoint(key, EndpointType.bool, entity, builder -> {
                builder.setIcon(new Icon("fas fa-star-half-stroke", "#C4BC45"));
                builder.setUpdateHandler(state ->
                    TasmotaProjectService.INSTANCE.publish(entity, key, "toggle"));
                builder.setDataReader(jsonNode -> {
                    if (jsonNode.has(key)) {
                        return OnOffType.of(jsonNode.get(key).asText());
                    }
                    return null;
                });
            }));
    }

    private void addMqttEntrypoints(Entry<String, JsonNode> entry, String path, Icon icon, Consumer<TasmotaEndpoint> endpointBuilder) {
        entry.getValue().fields().forEachRemaining(analogPins -> {
            String key = analogPins.getKey();
            addEndpointOptional(key, s ->
                new TasmotaEndpoint(key, EndpointType.number, entity, builder -> {
                    if (endpointBuilder != null) {
                        endpointBuilder.accept(builder);
                    }
                    builder.setIcon(icon);
                    builder.setDataReader(jsonNode -> {
                        if (jsonNode.path(path).has(key)) {
                            return new DecimalType(jsonNode.get(path).get(key).asDouble());
                        }
                        return null;
                    });
                }));
        });
    }

    private void updateMqtt(JsonNode payload) {
        payload.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            if (key.equals("ANALOG")) {
                addMqttEntrypoints(entry, "ANALOG", new Icon("fab fa-uniregistry", "#93C478"), builder -> {
                    builder.setMin(1F);
                    builder.setMax(1024F);
                });
            }
            if (key.equals("TEMPERATURE")) {
                addMqttEntrypoints(entry, "TEMPERATURE", new Icon("fas fa-temperature-three-quarters", "#429DC4"), builder -> {
                });
            }
            if (key.startsWith("POWER")) {
                addPowerEntrypoints(key);
            }
            attributes.set(key, entry.getValue());
        });

        for (TasmotaEndpoint endpoint : endpoints.values()) {
            endpoint.mqttUpdate(payload);
        }
    }

    private JsonNode handlePayload(MatchDeviceData data, JsonNode payload) {
        switch (data.reply()) {
            case "LOGGING":
                return null;
            case "STATUS8":
            case "STATUS10":
                payload = payload.get("StatusSNS");
            case "SENSOR":
                telemetry = payload;
                return payload;
        }

        // only if payload is object!!!
        if (payload.has("raw")) {
            return null;
        }

        List<String> keys = Lists.newArrayList(payload.fieldNames());
        String fk = keys.get(0);
        if ((data.reply().equals("RESULT") && fk.startsWith("Modules")) || data.reply().equals("MODULES")) {
            updatePartly("modules", payload);
            return null;
        } else if ((data.reply().equals("RESULT") && fk.equals("NAME")) || data.reply().equals("TEMPLATE")) {
            attributes.set("templates", payload);
            return null;
        } else if (data.reply().equals("RESULT") && fk.startsWith("GPIOs") || data.reply().equals("GPIOS")) {
            updatePartly("gpios", payload);
            return null;
        } else if (data.reply().equals("RESULT") && fk.startsWith("GPIO") || data.reply().equals("GPIO")) {
            payload.fields().forEachRemaining(item -> {
                if (!item.getKey().equals("GPIO")) {
                    ObjectNode gpio = (ObjectNode) attributes.get("gpio");
                    if (item.getValue().isTextual()) {
                        String gp_id = item.getValue().asText().split(" \\(")[0];
                        gpio.put(item.getKey(), gp_id);
                    } else if (item.getValue().isObject()) {
                        gpio.put(item.getKey(), item.getValue().fieldNames().next());
                    }
                }
            });
            return null;
        }
        return payload;
    }

    private void updatePartly(String key, JsonNode payload) {
        ObjectNode node = (ObjectNode) attributes.get(key);
        payload.fields().forEachRemaining(entry -> {
            if (entry.getValue().isObject()) {
                entry.getValue().fields().forEachRemaining(child -> {
                    node.set(child.getKey(), child.getValue());
                });
            } else {
                node.set(entry.getKey(), entry.getValue());
            }
        });
    }

    @Override
    protected void firstInitialize() {
        createOrUpdateDeviceGroup();
        createRequireEndpoints();
    }

    @Override
    protected void initialize() {

    }

    @Override
    public void destroy(boolean forRestart) throws Exception {
        downLinkQualityToZero();
    }

    /*private void addMqttListeners() {
        currentMQTTTopic = applianceModel.getMQTTTopic();
        String topic = getMqttFQDNTopic();

        coordinatorService.getMqttEntityService().addListener(topic, applianceModel.getIeeeAddress(), value -> {
            String payload = value == null ? "" : value.toString();
            if (!payload.isEmpty()) {
                try {
                    JSONObject jsonObject = new JSONObject(payload);
                    mqttUpdate(jsonObject);
                } catch (Exception ex) {
                    log.error("[{}]: Unable to parse json for entity: '{}' from: '{}'", coordinatorService.getEntityID(),
                            deviceEntity.getTitle(), payload);
                }
            }
        });

        coordinatorService.getMqttEntityService().addListener(topic + "/availability", applianceModel.getIeeeAddress(), payload -> {
            availability = payload == null ? null : payload.toString();
            context.event().fireEvent("zigbee-%s".formatted(applianceModel.getIeeeAddress()),
                new StringType(deviceEntity.getStatus().toString()));
            context.ui().updateItem(deviceEntity);
            if ("offline".equals(availability)) {
                downLinkQualityToZero();
            }
        });
    }*/

    /*public void publish(@NotNull String topic, @NotNull JSONObject payload) {
        if (topic.startsWith("bridge/'")) {
            coordinatorService.publish(topic, payload);
        } else {
            coordinatorService.publish(currentMQTTTopic + "/" + topic, payload);
        }
    }*/

    private void addEndpointOptional(String key, Function<String, TasmotaEndpoint> endpointProducer) {
        if (!endpoints.containsKey(key)) {
            TasmotaEndpoint endpoint = endpointProducer.apply(key);
            endpoint.mqttUpdate(attributes);
            endpoints.put(key, endpoint);
        }
    }

    private void createOrUpdateDeviceGroup() {
        context.var().createSubGroup("tasmota", entity.getIeeeAddress(), entity.getDeviceFullName(), builder ->
            builder.setIcon(entity.getEntityIcon()).setDescription(getGroupDescription()).setLocked(true));
    }

    public String getGroupDescription() {
        if (StringUtils.isEmpty(entity.getName()) || entity.getName().equals(entity.getIeeeAddress())) {
            return entity.getIeeeAddress();
        }
        return "${%s} [%s]".formatted(entity.getName(), entity.getIeeeAddress());
    }

    public @NotNull List<ConfigDeviceDefinition> findDevices() {
        if (models == null) {
            models = CONFIG_DEVICE_SERVICE.findDeviceDefinitionModels(null, endpoints.keySet());
        }
        return models;
    }

    private void downLinkQualityToZero() {
        Optional.ofNullable(endpoints.get(DeviceEndpoint.ENDPOINT_SIGNAL)).ifPresent(endpoint -> {
            if (!endpoint.getValue().stringValue().equals("0")) {
                endpoint.setValue(new DecimalType(0), false);
            }
        });
    }
}
