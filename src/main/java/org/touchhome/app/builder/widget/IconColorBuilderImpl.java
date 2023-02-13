package org.touchhome.app.builder.widget;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.touchhome.bundle.api.EntityContextWidget.IconColorBuilder;
import org.touchhome.bundle.api.ui.UI;

@RequiredArgsConstructor
public class IconColorBuilderImpl implements IconColorBuilder {

    private final JSONObject node = new JSONObject();
    private final String color;

    public String build() {
        if (!node.has("threshold")) {
            return color;
        }
        node.put("entity", StringUtils.isEmpty(color) ? UI.Color.random() : color);
        return node.toString();
    }

    @Override
    public IconColorBuilder setThreshold(String color, Object value, ValueCompare op) {
        JSONArray thresholds = node.optJSONArray("threshold");
        if (thresholds == null) {
            thresholds = new JSONArray();
            node.put("threshold", thresholds);
        }
        thresholds.put(new JSONObject()
            .put("value", value)
            .put("op", "")
            .put("entity", color));
        return this;
    }
}
