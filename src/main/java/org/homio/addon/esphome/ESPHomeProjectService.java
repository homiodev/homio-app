package org.homio.addon.esphome;

import static org.homio.api.model.endpoint.DeviceEndpoint.ENDPOINT_LAST_SEEN;
import static org.homio.api.util.CommonUtils.getErrorMessage;
import static org.homio.api.util.JsonUtils.OBJECT_MAPPER;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pivovarit.function.ThrowingBiConsumer;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Level;
import org.homio.api.Context;
import org.homio.api.ContextService.MQTTEntityService;
import org.homio.api.model.Icon;
import org.homio.api.service.EntityService.ServiceInstance;
import org.homio.api.state.DecimalType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Getter
public class ESPHomeProjectService extends ServiceInstance<ESPHomeProjectEntity> {

    public static ESPHomeProjectService INSTANCE;

    private MQTTEntityService mqttEntityService;
    private Map<String, ESPHomeDeviceEntity> existedDevices = new HashMap<>();
    private final Set<String> lwts = new ConcurrentSkipListSet<>();

    public ESPHomeProjectService(@NotNull Context context, @NotNull ESPHomeProjectEntity entity) {
        super(context, entity, true);
        INSTANCE = this;
    }

    public void dispose(@Nullable Exception ignore) {
        updateNotificationBlock();
    }

    public void publish(ESPHomeDeviceEntity entity, String key, String value) {
        String command = cmndTopic(entity, key);
        mqttEntityService.publish(command, value.getBytes());
    }

    @Override
    protected void initialize() {
        entity.setStatusOnline();
    }

    @Override
    protected void firstInitialize() {
        context.var().createGroup("tasmota", "Tasmota", builder ->
            builder.setLocked(true).setIcon(new Icon(TASMOTA_ICON, TASMOTA_COLOR)));

        mqttEntityService = entity.getMqttEntityService();
        existedDevices = context.db().findAll(ESPHomeDeviceEntity.class)
                   .stream()
                   .collect(Collectors.toMap(ESPHomeDeviceEntity::getIeeeAddress, t -> t));
        addMqttTopicListener((topic, payload) -> {
            MatchDeviceData data = findDevice(topic);
            if (data != null) {
                data.entity.getService().getEndpoints().get(ENDPOINT_LAST_SEEN)
                           .setValue(new DecimalType(System.currentTimeMillis()), true);

                if (topic.endsWith("LWT")) {
                    String msg = payload.get("raw").asText("Offline");
                    data.entity.getService().put("LWT", msg);
                    if ("Online".equals(msg)) {
                        initialQuery(data.entity);
                    }
                } else {
                    // forward the message for processing
                    data.entity.getService().mqttUpdate(payload, data);
                }
            }
            if (topic.endsWith("LWT")) {
                lwts.add(topic);
                log.info("[{}]: DISCOVERY: LWT from an unknown device {}", entityID, topic);
                for (String pattern : entity.getPatterns()) {
                    Matcher matcher = Pattern.compile(pattern.replace("%topic%", "(?<topic>.*)")
                                                             .replace("%prefix%", "(?<prefix>.*?)") + ".*$").matcher(topic);
                    if (matcher.matches()) {
                        String possible_topic = matcher.group("topic");
                        if (!possible_topic.equals("tele") && !possible_topic.equals("stat")) {
                            String possible_topic_cmnd = (pattern.replace("%prefix%", "cmnd").replace("%topic%", possible_topic) + "FullTopic");
                            log.info("[{}]: DISCOVERY: Asking an unknown device for FullTopic at {}", entityID, possible_topic_cmnd);
                            mqttEntityService.publish(possible_topic_cmnd);
                        }
                    }
                }
            } else if (topic.endsWith("RESULT") || topic.endsWith("FULLTOPIC")) {
                if (!payload.has("FullTopic")) {
                    return;
                }
                String full_topic = payload.get("FullTopic").asText();
                ParsedTopic parsed = parseTopic(full_topic, topic);
                if (parsed == null) {
                    return;
                }
                log.info("[{}]: DISCOVERY: topic {} is matched by fulltopic {}", entityID, topic, full_topic);
                MatchDeviceData deviceData = findDevice(parsed.topic);
                if (deviceData != null) {
                    if (!deviceData.entity.getFullTopic().equals(full_topic)) {
                        context.db().save(deviceData.entity.setFullTopic(full_topic));
                    }
                    deviceData.entity.getService().put("FullTopic", full_topic);
                } else {
                    log.info("[{}]: DISCOVERY: Discovered topic={} with fulltopic={}", entityID, parsed.topic, full_topic);
                    ESPHomeDeviceEntity device = new ESPHomeDeviceEntity();
                    device.setIeeeAddress(parsed.topic);
                    device.setFullTopic(full_topic);
                    existedDevices.put(parsed.topic, context.db().save(device));
                    log.info("[{}]: DISCOVERY: Sending initial query to topic {}", entityID, parsed.topic);
                    initialQuery(device);
                    String tele_topic = tele_topic(device, "LWT");
                    lwts.remove(tele_topic);
                    device.getService().put("LWT", "Online");
                }
            }
        });
        initialize();
    }

    public static String cmndTopic(ESPHomeDeviceEntity entity, @Nullable String command) {
        if (StringUtils.isNotEmpty(command)) {
            return build_topic(entity, "cmnd") + "/" + command;
        }
        return build_topic(entity, "cmnd");
    }

    public String tele_topic(ESPHomeDeviceEntity entity, String endpoint) {
        if (StringUtils.isNotEmpty(endpoint)) {
            return build_topic(entity, "tele") + "/" + endpoint;
        }
        return build_topic(entity, "tele");
    }

    private static String build_topic(ESPHomeDeviceEntity entity, String prefix) {
        return entity.getFullTopic()
                     .replace("%prefix%", prefix)
                     .replace("%topic%", entity.getIeeeAddress())
                     .replaceAll("/+$", "");
    }

    private @Nullable MatchDeviceData findDevice(String topic) {
        for (ESPHomeDeviceEntity entity : existedDevices.values()) {
            MatchDeviceData data = matchDevice(entity, topic);
            if (data != null) {
                return data;
            }
        }
        return null;
    }

    private MatchDeviceData matchDevice(ESPHomeDeviceEntity entity, String topic) {
        if (entity.getIeeeAddress().equals(topic)) {
            return new MatchDeviceData(entity, "", "");
        }
        ParsedTopic parsedTopic = parseTopic(entity.getFullTopic(), topic);
        if (parsedTopic != null && parsedTopic.topic.equals(entity.getIeeeAddress())) {
            return new MatchDeviceData(entity, parsedTopic.reply(), parsedTopic.prefix());
        }
        return null;
    }

    public record MatchDeviceData(ESPHomeDeviceEntity entity, String reply, String prefix) {}

    public void initialQuery(ESPHomeDeviceEntity device) {
        for (Entry<String, String> command : INITIAL_COMMANDS.entrySet()) {
            String cmd = cmndTopic(device, command.getKey());
            mqttEntityService.publish(cmd, command.getValue().getBytes());
        }
    }

    public static @Nullable ParsedTopic parseTopic(String fullTopic, String topic) {
        Pattern pattern = Pattern.compile(fullTopic
            .replace("%topic%", "(.*?)")
            .replace("%prefix%", "(.*?)") + "(.*)$");
        Matcher matcher = pattern.matcher(topic);
        if (matcher.matches()) {
            return new ParsedTopic(matcher.group(1), matcher.group(2), matcher.group(3));
        }
        return null;
    }

    public record ParsedTopic(String prefix, @NotNull String topic, String reply) {

    }

    @Override
    public void destroy(boolean forRestart) {
        this.dispose(null);
    }

    private void addMqttTopicListener(ThrowingBiConsumer<String, ObjectNode, Exception> handler) {
        mqttEntityService.addListener(Set.of("tele/#", "stat/#", "cmnd/#", "+/tele/#", "+/stat/#", "+/cmnd/#"),
            "tasmota", (realTopic, value) -> {
            log.log(Level.INFO, "[{}]: Tasmota {}: {}", entityID, realTopic, value);
            String payload = value == null ? "" : value;
            if (!payload.isEmpty()) {
                ObjectNode node;
                try {
                    node = OBJECT_MAPPER.readValue(payload, ObjectNode.class);
                } catch (Exception ex) {
                    node = OBJECT_MAPPER.createObjectNode().put("raw", payload);
                }
                try {
                    handler.accept(realTopic, node);
                } catch (Exception ex) {
                    log.error("[{}]: Unable to handle mqtt payload: {}. Msg: {}", entityID, payload, getErrorMessage(ex));
                }
            }
        });
    }
}
