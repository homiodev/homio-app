package org.homio.app.model.entity.widget.impl.display;

import jakarta.persistence.Entity;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldCodeEditor;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.app.model.entity.widget.WidgetBaseEntity;

@Entity
public class WidgetTextEntity extends WidgetBaseEntity<WidgetTextEntity> {

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
    @UIFieldGroup(value = "TEXT", order = 10, borderColor = "#4AB64D")
    @UIFieldCodeEditor(editorTypeRef = "contentType")
    public String getValue() {
        return getJsonData("text", "");
    }

    public void setValue(String value) {
        setJsonData("text", value);
    }

    @UIField(order = 3)
    @UIFieldGroup("TEXT")
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
