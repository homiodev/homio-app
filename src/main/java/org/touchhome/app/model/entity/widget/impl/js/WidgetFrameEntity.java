package org.touchhome.app.model.entity.widget.impl.js;

import com.fasterxml.jackson.annotation.JsonIgnore;
import javax.persistence.Entity;
import javax.persistence.Transient;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;
import org.touchhome.app.model.entity.widget.WidgetBaseEntity;
import org.touchhome.bundle.api.entity.HasJsonData;
import org.touchhome.bundle.api.ui.field.MonacoLanguage;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldCodeEditor;
import org.touchhome.bundle.api.ui.field.UIFieldIgnore;

@Entity
@Getter
@Setter
@Accessors(chain = true)
public class WidgetFrameEntity extends WidgetBaseEntity<WidgetFrameEntity> implements HasJsonData {

    public static final String PREFIX = "wgtfrm_";

    @Transient private String javaScriptResponse;

    @Transient private String javaScriptErrorResponse;

    @UIField(order = 12)
    @UIFieldCodeEditor(editorType = MonacoLanguage.HTML)
    public String getHtml() {
        return getJsonData("html");
    }

    public void setHtml(String value) {
        setJsonData("html", value);
    }

    @UIField(order = 13)
    @UIFieldCodeEditor(editorType = MonacoLanguage.JavaScript)
    public String getJavaScript() {
        return getJsonData("js");
    }

    public void setJavaScript(String value) {
        setJsonData("js", value);
    }

    @UIField(order = 14)
    @UIFieldCodeEditor(editorType = MonacoLanguage.CSS)
    public String getCss() {
        return getJsonData("css");
    }

    public void setCss(String value) {
        setJsonData("css", value);
    }

    @Override
    public String getImage() {
        return "fab fa-html5";
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    @Override
    public String getDefaultName() {
        return "Frame";
    }

    @JsonIgnore
    public String getFrame() {
        StringBuilder html = new StringBuilder(getHtml());
        if (html.indexOf("</head>") > 0) {
            if (StringUtils.isNotEmpty(getJavaScript())) {
                html.insert(html.indexOf("</head>"), "<script>\n\t(function(){\n" + getJavaScript() + "\n})();\n</script>");
            }
            if (StringUtils.isNotEmpty(getCss())) {
                html.insert(html.indexOf("</head>"), "<style type=\"text/css\">" + getCss() + "</style>");
            }
        }
        return html.toString();
    }

    @Override
    @UIFieldIgnore
    @JsonIgnore
    public String getBackground() {
        return super.getBackground();
    }

    @Override
    @UIFieldIgnore
    @JsonIgnore
    public boolean isAdjustFontSize() {
        return super.isAdjustFontSize();
    }

    @Override
    protected void beforePersist() {
        super.beforePersist();
        setHtml("<html>\n"
            + "  <head></head>\n"
            + "  <body>\n"
            + "     <div style=\"display:flex;align-items:center;margin: 0 auto;\">\n\tHTML template\n</div>\n"
            + "  </body>\n"
            + "</html>");
        setCss(":root\n{\n\tcolor: #999;\n}\n\nbody\n{\n\tpadding:0;\n\tmargin:0;\n\tdisplay:flex;\n}");
    }
}
