package org.homio.app.model.entity.widget.impl.extra;

import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.homio.api.entity.BaseEntity;
import org.homio.api.entity.HasJsonData;
import org.homio.api.ui.field.MonacoLanguage;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldCodeEditor;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.action.HasDynamicContextMenuActions;
import org.homio.api.ui.field.action.HasDynamicUIFields;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.ui.field.selection.UIFieldEntityByClassSelection;
import org.homio.api.widget.CustomWidgetConfigurableEntity;
import org.homio.app.model.entity.widget.WidgetEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

@Entity
@Getter
@Setter
@Accessors(chain = true)
public class WidgetCustomEntity extends WidgetEntity<WidgetCustomEntity> implements
        HasDynamicUIFields,
        HasDynamicContextMenuActions,
        HasJsonData {

    @Override
    protected @NotNull String getWidgetPrefix() {
        return "custom";
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

    @UIField(order = 50, required = true)
    @UIFieldEntityByClassSelection(CustomWidgetConfigurableEntity.class)
    public String getParameterEntity() {
        return getJsonData("pe");
    }

    public void setParameterEntity(String value) {
        setJsonData("pe", value);
    }

    @Override
    public void assembleUIFields(@NotNull HasDynamicUIFields.UIFieldBuilder uiFieldBuilder) {
        String parameterEntity = getParameterEntity();
        if (!parameterEntity.isEmpty()) {
            BaseEntity entity = context().db().get(parameterEntity);
            if (entity instanceof CustomWidgetConfigurableEntity configurableEntity) {
                configurableEntity.assembleUIFields(uiFieldBuilder, this);
            }
        }
    }

    @Override
    public void assembleActions(UIInputBuilder uiInputBuilder) {
        String parameterEntity = getParameterEntity();
        if (!parameterEntity.isEmpty()) {
            BaseEntity entity = context().db().get(parameterEntity);
            if (entity instanceof CustomWidgetConfigurableEntity configurableEntity) {
                configurableEntity.assembleActions(uiInputBuilder);
            }
        }
    }

    @UIField(order = 450)
    @UIFieldGroup("UI")
    public String getCss() {
        return getJsonData("css");
    }

    public void setCss(String value) {
        setJsonData("css", value);
    }
}
