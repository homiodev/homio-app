package org.homio.app.model.entity.widget.impl.extra;

import jakarta.persistence.Entity;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.homio.api.entity.HasJsonData;
import org.homio.api.ui.field.MonacoLanguage;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldCodeEditor;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.app.model.entity.widget.UIFieldOptionFontSize;
import org.homio.app.model.entity.widget.WidgetEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

@Entity
@Getter
@Setter
@Accessors(chain = true)
public class WidgetCustomEntity extends WidgetEntity<WidgetCustomEntity> implements HasJsonData {

    @Override
    protected @NotNull String getWidgetPrefix() {
        return "custom";
    }

    @UIField(order = 1)
    @UIFieldGroup(order = 3, value = "NAME")
    @UIFieldOptionFontSize
    public String getName() {
        return super.getName();
    }

    @UIField(order = 13)
    @UIFieldCodeEditor(editorType = MonacoLanguage.JavaScript)
    public String getCode() {
        return getJsonData("code");
    }

    public WidgetCustomEntity setCode(String value) {
        setJsonData("code", value);
        return this;
    }

    @UIField(order = 12)
    @UIFieldCodeEditor(editorType = MonacoLanguage.Json, autoFormat = true)
    public String getParameters() {
        return getJsonData("params", "{}");
    }

    public void setParameters(String value) {
        setJsonData("params", value);
    }

    public @NotNull String getImage() {
        return "fab fa-panorama";
    }

    @Override
    public @Nullable String getDefaultName() {
        return "Custom";
    }

    @Override
    protected void assembleMissingMandatoryFields(@NotNull Set<String> fields) {

    }
}
