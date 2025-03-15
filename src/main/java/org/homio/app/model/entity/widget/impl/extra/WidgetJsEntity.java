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
import org.homio.app.model.entity.widget.WidgetEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Entity
@Getter
@Setter
@Accessors(chain = true)
public class WidgetJsEntity extends WidgetEntity<WidgetJsEntity> implements HasJsonData {

  @Transient
  private String javaScriptResponse;
  @Transient
  private String javaScriptErrorResponse;

  @Override
  protected @NotNull String getWidgetPrefix() {
    return "js";
  }

  @UIField(order = 13)
  @UIFieldCodeEditor(editorType = MonacoLanguage.JavaScript)
  public String getJavaScript() {
    return getJsonData("js");
  }

  public WidgetJsEntity setJavaScript(String value) {
    setJsonData("js", value);
    return this;
  }

  @UIField(order = 12)
  @UIFieldCodeEditor(editorType = MonacoLanguage.Json, autoFormat = true)
  public String getJavaScriptParameters() {
    return getJsonData("jsp", "{}");
  }

  public WidgetJsEntity setJavaScriptParameters(String value) {
    setJsonData("jsp", value);
    return this;
  }

  public Boolean getJavaScriptParametersReadOnly() {
    return getJsonData("jspro", Boolean.FALSE);
  }

  public WidgetJsEntity setJavaScriptParametersReadOnly(Boolean value) {
    setJsonData("jspro", value);
    return this;
  }

  public @NotNull String getImage() {
    return "fab fa-js-square";
  }
   /* @Override

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    @Override
    public String getDefaultName() {
        return null;
    }

    @Override
    public void beforePersist() {
        super.beforePersist();
        setJavaScriptParameters("{\"text\":\"Hello world!\"}");
        setJavaScript("function run() {\n\treturn params.get('text');\n}");
    }*/

  @Override
  public @Nullable String getDefaultName() {
    return "JS";
  }

}
