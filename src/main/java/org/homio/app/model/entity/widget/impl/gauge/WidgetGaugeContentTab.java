package org.homio.app.model.entity.widget.impl.gauge;

import org.homio.api.entity.HasJsonData;
import org.homio.api.ui.field.*;

public interface WidgetGaugeContentTab extends HasJsonData {

    @UIField(order = 1, label = "gauge_content")
    @UIFieldTab("CONTENT")
    @UIFieldColorPicker
    @UIFieldReadDefaultValue
    default ContentType getContent() {
        return getJsonDataEnum("contType", ContentType.value);
    }

    @UIField(order = 200)
    @UIFieldTab("CONTENT")
    @UIFieldGroup(value = "VALUE", order = 1)
    @UIFieldStringTemplate(allowPrefix = false)
    default UIFieldStringTemplate.StringTemplate getValueTemplate() {
        return getJsonData("vt", UIFieldStringTemplate.StringTemplate.class);
    }

    default void setValueTemplate(UIFieldStringTemplate.StringTemplate value) {
        setJsonDataObject("vt", value);
    }

    default void setInnerContent(ContentType value) {
        setJsonDataEnum("contType", value);
    }

    enum ContentType {
        value,
        valueValue,
        valueValueValue
    }
}
