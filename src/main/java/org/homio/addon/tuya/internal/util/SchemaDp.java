package org.homio.addon.tuya.internal.util;

import static org.homio.api.util.CommonUtils.OBJECT_MAPPER;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import org.homio.addon.tuya.internal.cloud.dto.DeviceSchema;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Wrapper for the information of a single datapoint
 */
public class SchemaDp {

    private static final Map<String, String> REMOTE_LOCAL_TYPE_MAP = Map.of( //
        "Boolean", "bool", //
        "Enum", "enum", //
        "Integer", "value", //
        "Json", "string");

    public int dp;
    public @NotNull String type = "";
    public @NotNull String code = "";

    public @Nullable Integer dp2;
    public @Nullable Float min;
    public @Nullable Float max;
    public @Nullable List<String> range;
    public @Nullable Boolean writable;
    public @Nullable Boolean readable;
    public @Nullable String unit;
    public @Nullable Integer scale;
    public @Nullable Integer step;

    @SneakyThrows
    public static SchemaDp fromRemoteSchema(DeviceSchema.Description description) {
        SchemaDp schemaDp = new SchemaDp();
        schemaDp.code = description.code.replace("_v2", "");
        schemaDp.dp = description.dp_id;
        schemaDp.type = REMOTE_LOCAL_TYPE_MAP.getOrDefault(description.type, "raw"); // fallback to raw

        ObjectNode metadata = OBJECT_MAPPER.readValue(description.values, ObjectNode.class);

        schemaDp.unit = metadata.path("unit").asText();
        if ("enum".equalsIgnoreCase(schemaDp.type) && metadata.has("range")) {
            schemaDp.range = new ArrayList<>();
            for (JsonNode jsonNode : metadata.get("range")) {
                schemaDp.range.add(jsonNode.asText());
            }
        } else if ("value".equalsIgnoreCase(schemaDp.type) && metadata.has("min") && metadata.has("max")) {
            schemaDp.min = (float) metadata.get("min").asDouble();
            schemaDp.max = (float) metadata.get("max").asDouble();
            schemaDp.scale = metadata.has("scale") ? metadata.get("scale").asInt() : null;
            schemaDp.step = metadata.has("step") ? metadata.get("step").asInt() : null;
        }

        return schemaDp;
    }
}
