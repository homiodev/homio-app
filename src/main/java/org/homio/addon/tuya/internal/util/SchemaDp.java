package org.homio.addon.tuya.internal.util;

import static org.homio.api.util.CommonUtils.OBJECT_MAPPER;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.homio.addon.tuya.internal.cloud.dto.DeviceSchema;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Wrapper for the information of a single datapoint
 */
@Getter
public class SchemaDp {

    private static final Map<String, String> REMOTE_LOCAL_TYPE_MAP = Map.of( //
        "Boolean", "bool", //
        "Enum", "enum", //
        "Integer", "value", //
        "Json", "string");

    public int dp;
    private @NotNull String type = "";
    private @NotNull String code = "";

    private @Setter @Nullable Integer dp2;
    private @Setter @Nullable Boolean writable;
    private @Setter @Nullable Boolean readable;
    private @NotNull ObjectNode meta = OBJECT_MAPPER.createObjectNode();

    @JsonIgnore
    private List<String> range;
    @JsonIgnore
    private Float min;
    @JsonIgnore
    private Float max;

    @SneakyThrows
    public void mergeMeta(String updateValue) {
        if (!meta.isEmpty()) {
            meta = OBJECT_MAPPER.readerForUpdating(meta).readValue(new StringReader(updateValue));
        }
    }

    @JsonIgnore
    public String getUnit() {
        return meta.path("unit").asText();
    }

    @JsonIgnore
    public float getMin() {
        if (min == null) {
            min = (float) meta.path("min").asDouble(0);
        }
        return min;
    }

    @JsonIgnore
    public float getMax() {
        if (max == null) {
            max = (float) meta.path("max").asDouble(0);
        }
        return max;
    }

    @JsonIgnore
    public List<String> getRange() {
        if (range == null) {
            range = new ArrayList<>();
            if (meta.has("range")) {
                for (JsonNode jsonNode : meta.get("range")) {
                    range.add(jsonNode.asText());
                }
            }
        }
        return range;
    }

    @SneakyThrows
    public static SchemaDp parse(DeviceSchema.Description description) {
        SchemaDp schemaDp = new SchemaDp();
        schemaDp.code = description.code.replace("_v2", "");
        schemaDp.dp = description.dp_id;
        schemaDp.type = REMOTE_LOCAL_TYPE_MAP.getOrDefault(description.type, "raw"); // fallback to raw
        schemaDp.meta = OBJECT_MAPPER.readValue(description.values, ObjectNode.class);
        return schemaDp;
    }
}
