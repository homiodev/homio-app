package org.homio.app.model.entity.widget.impl.js;

import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.homio.api.entity.HasJsonData;
import org.homio.api.model.JSON;
import org.homio.api.ui.field.MonacoLanguage;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldCodeEditor;
import org.jetbrains.annotations.NotNull;

// @Entity
@Getter
@Setter
@Accessors(chain = true)
public class WidgetJavaJsEntity /*TODO: fix:   extends WidgetBaseEntity<WidgetJsEntity>*/ implements HasJsonData {

    public static final String PREFIX = "widget_js_";

    @Transient private String javaScriptResponse;

    @Transient private String javaScriptErrorResponse;

    @UIField(order = 13)
    @UIFieldCodeEditor(editorType = MonacoLanguage.JavaScript)
    public String getJavaScript() {
        return getJsonData("js");
    }

    public WidgetJavaJsEntity setJavaScript(String value) {
        setJsonData("js", value);
        return this;
    }

    @UIField(order = 12)
    @UIFieldCodeEditor(editorType = MonacoLanguage.Json, autoFormat = true)
    public String getJavaScriptParameters() {
        return getJsonData("jsp", "{}");
    }

    public WidgetJavaJsEntity setJavaScriptParameters(String value) {
        setJsonData("jsp", value);
        return this;
    }

    public Boolean getJavaScriptParametersReadOnly() {
        return getJsonData("jspro", Boolean.FALSE);
    }

    public WidgetJavaJsEntity setJavaScriptParametersReadOnly(Boolean value) {
        setJsonData("jspro", value);
        return this;
    }

   /* @Override
    public String getImage() {
        return "fab fa-js-square";
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    @Override
    public String getDefaultName() {
        return null;
    }

    @Override
    protected void beforePersist() {
        super.beforePersist();
        setJavaScriptParameters("{\"text\":\"Hello world!\"}");
        setJavaScript("function run() {\n\treturn params.get('text');\n}");
    }*/

    @Override
    public @NotNull JSON getJsonData() {
        return null;
    }
}
