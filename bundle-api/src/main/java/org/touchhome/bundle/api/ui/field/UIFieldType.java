package org.touchhome.bundle.api.ui.field;

public enum UIFieldType {
    Slider,
    Selection,
    Float,
    Duration,
    StaticDate,
    Image,
    PlainList, // TODO: refactor
    String,
    Boolean,
    Integer, // for integer we may set metadata as min, max
    Color,
    Json,

    // special type (default for detect field type by java type)
    AutoDetect
}
