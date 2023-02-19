package org.touchhome.app.model.entity.widget.impl.display;

import javax.persistence.Entity;
import org.touchhome.app.model.entity.widget.WidgetBaseEntity;
import org.touchhome.app.model.entity.widget.attributes.HasStyle;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldCodeEditor;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;

@Entity
public class WidgetTextEntity extends WidgetBaseEntity<WidgetTextEntity> implements
    HasStyle {

    public static final String PREFIX = "wgttht_";

    @Override
    public String getImage() {
        return "fas fa-file-text";
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    @Override
    public String getDefaultName() {
        return null;
    }

    @UIField(order = 1)
    @UIFieldGroup(value = "Text", order = 10, borderColor = "#4AB64D")
    @UIFieldCodeEditor(editorTypeRef = "contentType")
    public String getValue() {
        return getJsonData("text", "");
    }

    public void setValue(String value) {
        setJsonData("text", value);
    }

    @UIField(order = 3)
    @UIFieldGroup("Text")
    public ContentType getContentType() {
        return getJsonDataEnum("ct", ContentType.Text);
    }

    public void setContentType(ContentType value) {
        setJsonDataEnum("ct", value);
    }

    private enum ContentType {
        HTML, Text, Markdown
    }
}
