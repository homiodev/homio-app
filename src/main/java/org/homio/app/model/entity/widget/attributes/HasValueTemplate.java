package org.homio.app.model.entity.widget.attributes;

import org.homio.api.entity.HasJsonData;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldStringTemplate;

public interface HasValueTemplate extends HasJsonData {

    @UIField(order = 200)
    @UIFieldGroup(value = "VALUE", order = 10)
    @UIFieldStringTemplate
    default UIFieldStringTemplate.StringTemplate getValueTemplate() {
        return getJsonData("vt", UIFieldStringTemplate.StringTemplate.class);
    }

    default void setValueTemplate(UIFieldStringTemplate.StringTemplate value) {
        setJsonDataObject("vt", value);
    }
}
