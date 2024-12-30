package org.homio.app.model.entity.widget.attributes;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.homio.api.entity.HasJsonData;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldStringTemplate;

import java.util.function.Consumer;

public interface HasValueTemplate extends HasJsonData {

    @UIField(order = 200)
    @UIFieldGroup(value = "VALUE", order = 10)
    @UIFieldStringTemplate
    default UIFieldStringTemplate.StringTemplate getValueTemplate() {
        return getJsonData("vt", UIFieldStringTemplate.StringTemplate.class, true);
    }

    default void setValueTemplate(UIFieldStringTemplate.StringTemplate value) {
        setJsonDataObject("vt", value);
    }

    default void applyValueTemplate(Consumer<UIFieldStringTemplate.StringTemplate> handler) {
        UIFieldStringTemplate.StringTemplate valueTemplate = getValueTemplate();
        handler.accept(valueTemplate);
        setValueTemplate(valueTemplate);
    }
}
