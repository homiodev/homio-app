package org.touchhome.app.model.entity.widget.impl.js;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.touchhome.bundle.api.entity.widget.WidgetBaseEntity;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldCodeEditor;

import javax.persistence.Entity;
import javax.persistence.Transient;

@Entity
@Getter
@Setter
@Accessors(chain = true)
public class WidgetJsEntity extends WidgetBaseEntity<WidgetJsEntity> {

    public static final String PREFIX = "wgtjs_";

    @Transient
    private String javaScriptResponse;

    @Transient
    private String javaScriptErrorResponse;

    @UIField(order = 13)
    @UIFieldCodeEditor(editorType = UIFieldCodeEditor.CodeEditorType.javascript)
    public String getJavaScript() {
        return getJsonData("js");
    }

    public WidgetJsEntity setJavaScript(String value) {
        setJsonData("js", value);
        return this;
    }

    @UIField(order = 12)
    @UIFieldCodeEditor(editorType = UIFieldCodeEditor.CodeEditorType.json, autoFormat = true)
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

    @Override
    public String getImage() {
        return "fab fa-js-square";
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    @Override
    protected void beforePersist() {
        super.beforePersist();
        setJavaScriptParameters("{\"text\":\"Hello world!\"}");
        setJavaScript("function run() {\n\treturn params.get('text');\n}");
    }
}
