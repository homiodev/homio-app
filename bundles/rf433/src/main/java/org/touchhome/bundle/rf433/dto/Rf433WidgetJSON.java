package org.touchhome.bundle.rf433.dto;

import java.util.ArrayList;
import java.util.List;

public class Rf433WidgetJSON {
    public Tooltip tooltip;
    public Legend legend;
    public Toolbox toolbox;
    public Title title;
    public String calculable = "!0";
    public Boolean animation;
    public List<Axis> xAxis = new ArrayList<>();
    public List<Axis> yAxis = new ArrayList<>();
    public List<Series> series = new ArrayList<>();

    public static class Series {
        public String name;
        public String type;
        public List<String> radius;
        public List<Object> data = new ArrayList<>();
    }

    public static class Axis {
        public String type;
        public List<String> data = new ArrayList<>();
        public AxisLabel axisLabel = new AxisLabel();
    }

    public static class AxisLabel {
        public String formatter;
    }

    public static class Title {
        public String text;
        public String x;
    }

    public static class Toolbox {
        public String show = "!0";
        public String trigger;
        public Feature feature = new Feature();
    }

    public static class Feature {
        public SaveAsImage saveAsImage;
    }

    public static class SaveAsImage {
        public String show = "!0";
        public String title = "Save as image";
    }

    public static class Tooltip {
        public String trigger;
    }

    public static class Legend {
        public List<String> data;
        public String orient;
        public String x;
    }

    public static class PIEData {
        public String name;
        public Object value;
        public ItemStyle itemStyle;
    }

    public static class ItemStyle {
        public ItemStyleNormal normal;
    }

    public static class ItemStyleNormal {
        public String color;
    }
}
