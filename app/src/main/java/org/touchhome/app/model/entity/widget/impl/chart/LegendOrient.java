package org.touchhome.app.model.entity.widget.impl.chart;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.stream.Stream;

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum LegendOrient {
    vertical_left("vertical", "left"), vertical_right("vertical", "right"), horizontal("horizontal", null);

    private final String orient;
    private final String x;

    LegendOrient(String orient, String x) {
        this.orient = orient;
        this.x = x;
    }

    @JsonCreator
    public static LegendOrient fromValue(String value) {
        return Stream.of(LegendOrient.values()).filter(e -> e.name().equals(value)).findFirst().orElse(null);
    }

    public String getOrient() {
        return orient;
    }

    public String getX() {
        return x;
    }

    @JsonValue
    public String toValue() {
        return name();
    }
}
