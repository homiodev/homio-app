package org.homio.app.builder.widget;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.homio.bundle.api.EntityContextWidget.AnimateBuilder;
import org.homio.bundle.api.EntityContextWidget.AnimateColor;
import org.homio.bundle.api.EntityContextWidget.ThresholdBuilder;
import org.homio.bundle.api.EntityContextWidget.ValueCompare;
import org.homio.bundle.api.ui.UI;
import org.json.JSONArray;
import org.json.JSONObject;

@RequiredArgsConstructor
public class ThresholdBuilderImpl implements ThresholdBuilder, AnimateBuilder {

    private final JSONObject node = new JSONObject();
    private final String color;

    public String build() {
        if (!node.has("threshold") && !node.has("animation")) {
            return color;
        }
        node.put("entity", StringUtils.isEmpty(color) ? UI.Color.random() : color);
        return node.toString();
    }

    @Override
    public ThresholdBuilder setThreshold(String color, Object value, ValueCompare op) {
        JSONArray thresholds = node.optJSONArray("threshold");
        if (thresholds == null) {
            thresholds = new JSONArray();
            node.put("threshold", thresholds);
        }
        thresholds.put(new JSONObject()
            .put("value", value)
            .put("op", op.getOp())
            .put("entity", color));
        return this;
    }

    @Override
    public AnimateBuilder setAnimate(AnimateColor animateColor, Object value, ValueCompare op) {
        node.put("animation",
            new JSONObject()
                .put("value", value)
                .put("op", op.getOp())
                .put("entity", animateColor.name()));

        return this;
    }

    @Getter
    @AllArgsConstructor
    private static class AnimationPOJO {

        private final Object value;
        private final String op;
        private final String entity;
    }
}
