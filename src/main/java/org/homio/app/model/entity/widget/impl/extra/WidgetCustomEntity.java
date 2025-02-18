package org.homio.app.model.entity.widget.impl.extra;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.homio.api.entity.BaseEntity;
import org.homio.api.entity.HasJsonData;
import org.homio.api.ui.field.MonacoLanguage;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldCodeEditor;
import org.homio.api.ui.field.UIFieldIgnore;
import org.homio.api.ui.field.action.HasDynamicContextMenuActions;
import org.homio.api.ui.field.action.HasDynamicUIFields;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.ui.field.selection.UIFieldEntityByClassSelection;
import org.homio.api.widget.HasCustomWidget;
import org.homio.app.model.entity.widget.UIFieldOptionFontSize;
import org.homio.app.model.entity.widget.WidgetEntity;
import org.homio.app.rest.UIFieldBuilderImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Entity
@Getter
@Setter
@Accessors(chain = true)
public class WidgetCustomEntity extends WidgetEntity<WidgetCustomEntity> implements
  HasDynamicUIFields,
  HasDynamicContextMenuActions,
  HasJsonData {

  @JsonAnyGetter
  public Map<String, Object> getDynamicUiProperties() {
    Map<String, Object> dynamicProperties = new HashMap<>();
    UIFieldBuilderImpl builder = new UIFieldBuilderImpl();
    assembleUIFields(builder);
    builder.getFields().forEach((field, fieldBuilder) ->
      dynamicProperties.put(field, fieldBuilder.getValue().getValue()));
    return dynamicProperties;
  }

  @Override
  protected @NotNull String getWidgetPrefix() {
    return "custom";
  }

  @UIField(order = 1)
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

  public @NotNull String getImage() {
    return "fas fa-panorama";
  }

  @Override
  public @Nullable String getDefaultName() {
    return "Custom";
  }

  @UIField(order = 50, required = true)
  @UIFieldEntityByClassSelection(HasCustomWidget.class)
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
      if (entity instanceof HasCustomWidget configurableEntity) {
        configurableEntity.assembleUIFields(uiFieldBuilder, this);
      }
    }
  }

  @Override
  public void assembleActions(UIInputBuilder uiInputBuilder) {
    String parameterEntity = getParameterEntity();
    if (!parameterEntity.isEmpty()) {
      BaseEntity entity = context().db().get(parameterEntity);
      if (entity instanceof HasCustomWidget configurableEntity) {
        configurableEntity.assembleActions(uiInputBuilder);
      }
    }
  }

  public String getCss() {
    return getJsonData("css");
  }

  public void setCss(String value) {
    setJsonData("css", value);
  }

  @JsonIgnore
  @UIFieldIgnore
  public List<String> getStyle() {
    return super.getStyle();
  }
}
