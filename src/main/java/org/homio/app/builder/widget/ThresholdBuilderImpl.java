package org.homio.app.builder.widget;

import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.homio.api.EntityContextWidget.PulseBuilder;
import org.homio.api.EntityContextWidget.PulseColor;
import org.homio.api.EntityContextWidget.ThresholdBuilder;
import org.homio.api.EntityContextWidget.ValueCompare;
import org.homio.api.ui.UI.Color;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;

@RequiredArgsConstructor
public class ThresholdBuilderImpl implements ThresholdBuilder, PulseBuilder {

    private static final Set<String> ANIMATIONS = Set.of("fa-beat", "fa-beat-fade", "fa-bounce", "fa-fade", "fa-flip", "fa-shake", "fa-spin",
        "fa-spin fa-spin-reverse");
    private static final Set<String> ROTATIONS = Set.of("fa-rotate-90", "fa-rotate-180", "fa-rotate-270", "fa-flip-horizontal", "fa-flip-vertical",
        "fa-flip-both");
    private static final Set<String> SPEED = Set.of("fa-speed-slow", "fa-speed-fast", "fa-speed-fastest");

    private static final String THRESHOLD_MODELS = "thresholdModel";
    private static final String MODEL = "model";
    private static final String PULSE_ANIMATION = "pulseThresholdModel";

    private final JSONObject node = new JSONObject();
    private final String entity;

    public String build() {
        if (!node.has(THRESHOLD_MODELS) && !node.has(PULSE_ANIMATION)) {
            return entity;
        }
        String finalEntity = StringUtils.defaultIfEmpty(entity, Color.random());
        node.put(MODEL, new JSONObject().put("entity", finalEntity));
        return node.toString();
    }

    @Override
    public ThresholdBuilder setThreshold(String entity, Object value, ValueCompare op) {
        return addThreshold(THRESHOLD_MODELS, entity, value, op, (items, json) -> {
            removeItem(items, ANIMATIONS, json, "spin");
            removeItem(items, ROTATIONS, json, "rotate");
            removeItem(items, SPEED, json, "spinSpeed");
        });
    }

    @Override
    public PulseBuilder setPulse(PulseColor pulseColor, Object value, ValueCompare op) {
        return addThreshold(PULSE_ANIMATION, pulseColor.name(), value, op, null);
    }

    private ThresholdBuilderImpl addThreshold(@NotNull String key, @NotNull String entity, @NotNull Object value,
        @NotNull ValueCompare op, @Nullable BiConsumer<Set<String>, JSONObject> builder) {
        JSONArray thresholds = node.optJSONArray(key);
        if (thresholds == null) {
            thresholds = new JSONArray();
            node.put(key, thresholds);
        }

        JSONObject json = new JSONObject().put("value", value).put("op", op.getOp());
        Set<String> items = Stream.of(entity.split(" ")).collect(Collectors.toSet());
        json.put("entity", String.join(" ", items));
        if (builder != null) {
            builder.accept(items, json);
        }

        thresholds.put(json);
        return this;
    }

    private void removeItem(Set<String> items, Set<String> collection, JSONObject json, String key) {
        String item = items.stream().filter(collection::contains).findAny().orElse(null);
        if (item != null) {
            items.remove(item);
            json.put(key, item);
        }
    }
}
