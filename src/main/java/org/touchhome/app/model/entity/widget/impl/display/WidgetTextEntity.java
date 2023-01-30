package org.touchhome.app.model.entity.widget.impl.display;

import java.util.List;
import javax.persistence.Entity;
import org.touchhome.app.model.entity.widget.WidgetBaseEntity;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldCodeEditor;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;
import org.touchhome.bundle.api.ui.field.UIFieldType;

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
    @UIFieldGroup(value = "Text", order = 10, borderColor = "#4AB64D")
    @UIFieldCodeEditor(editorType = UIFieldCodeEditor.CodeEditorType.text)
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

    @UIField(order = 4, type = UIFieldType.Chips)
    @UIFieldGroup("Text")
    public List<String> getStyle() {
        return getJsonDataList("st");
    }

    public void setStyle(String value) {
        setJsonData("st", value);
    }

    private enum ContentType {
        Text, Html, Markdown, AutoDetect
    }
}
