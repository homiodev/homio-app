package org.homio.app.model.entity.widget.impl.simple;

import jakarta.persistence.Entity;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldCodeEditor;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.app.model.entity.widget.WidgetEntity;
import org.homio.app.model.entity.widget.WidgetGroup;
import org.homio.app.model.entity.widget.attributes.HasAlign;
import org.homio.app.model.entity.widget.attributes.HasIcon;
import org.homio.app.model.entity.widget.attributes.HasMargin;
import org.jetbrains.annotations.NotNull;

@Entity
public class WidgetSimpleTextEntity extends WidgetEntity<WidgetSimpleTextEntity>
        implements
        HasAlign,
        HasIcon,
        HasMargin {

    @Override
    public WidgetGroup getGroup() {
        return WidgetGroup.Simple;
    }

    @Override
    public @NotNull String getImage() {
        return "fas fa-file-text";
    }

    @Override
    protected @NotNull String getWidgetPrefix() {
        return "sim-txt";
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

    public enum ContentType {
        HTML, Text, Markdown
    }
}
