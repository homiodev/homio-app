package org.homio.addon.tuya.internal.util;

import com.google.gson.Gson;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.homio.addon.tuya.internal.cloud.dto.DeviceSchema;
import org.jetbrains.annotations.Nullable;

/**
 * The {@link SchemaDp} is a wrapper for the information of a single datapoint
 */
public class SchemaDp {
    private static final Map<String, String> REMOTE_LOCAL_TYPE_MAP = Map.of( //
            "Boolean", "bool", //
            "Enum", "enum", //
            "Integer", "value", //
            "Json", "string");

    public int id = 0;
    public String code = "";
    public String type = "";
    public double min;
    public double max;
    public @Nullable List<String> range;

    public static SchemaDp fromRemoteSchema(Gson gson, DeviceSchema.Description function) {
        SchemaDp schemaDp = new SchemaDp();
        schemaDp.code = function.code.replace("_v2", "");
        schemaDp.id = function.dp_id;
        schemaDp.type = REMOTE_LOCAL_TYPE_MAP.getOrDefault(function.type, "raw"); // fallback to raw

        if ("enum".equalsIgnoreCase(schemaDp.type) && function.values.contains("range")) {
            schemaDp.range = Objects.requireNonNull(
                    gson.fromJson(function.values.replaceAll("\\\\", ""), DeviceSchema.EnumRange.class)).range;
        } else if ("value".equalsIgnoreCase(schemaDp.type) && function.values.contains("min")
                && function.values.contains("max")) {
            DeviceSchema.NumericRange numericRange = Objects.requireNonNull(
                    gson.fromJson(function.values.replaceAll("\\\\", ""), DeviceSchema.NumericRange.class));
            schemaDp.min = numericRange.min;
            schemaDp.max = numericRange.max;
        }

        return schemaDp;
    }
}
