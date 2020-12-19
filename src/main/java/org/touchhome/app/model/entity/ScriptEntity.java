package org.touchhome.app.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.pivovarit.function.ThrowingBinaryOperator;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.springframework.core.env.Environment;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.model.Status;
import org.touchhome.bundle.api.ui.UISidebarMenu;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldCodeEditor;
import org.touchhome.bundle.api.ui.field.UIFieldType;
import org.touchhome.bundle.api.util.SpringUtils;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Lob;
import javax.persistence.Transient;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.Invocable;
import java.io.StringReader;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@UISidebarMenu(icon = "fab fa-js-square", order = 1, bg = "#9e7d18", allowCreateNewItems = true)
@Accessors(chain = true)
public class ScriptEntity extends BaseEntity<ScriptEntity> {

    @Getter
    @Setter
    @Column(nullable = false)
    private Status status = Status.UNKNOWN;

    @Getter
    @Setter
    @UIField(order = 13, type = UIFieldType.Json)
    private String javaScriptParameters = "{}";

    @Getter
    @Setter
    @UIField(order = 16, inlineEdit = true)
    private boolean autoStart = false;

    @Getter
    @Setter
    private String error;

    @Lob
    @Getter
    @Setter
    @UIField(order = 30)
    @Column(length = 1048576)
    @UIFieldCodeEditor(editorType = UIFieldCodeEditor.CodeEditorType.javascript)
    private String javaScript = "function before() { };\nfunction run() { };\nfunction after() { };";

    @Transient
    @JsonIgnore
    private long formattedJavaScriptHash;

    @Getter
    @Setter
    @UIField(order = 40)
    private int repeatInterval = 0;

    @Transient
    @JsonIgnore
    private String formattedJavaScript;

    public static Set<String> getFunctionsWithPrefix(String javaScript, String prefix) {
        Set<String> functions = new HashSet<>();
        int i = javaScript.indexOf("function " + prefix, 0);
        while (i >= 0) {
            int endIndex = i + 1;
            int countOfBrakets = 0;
            while (javaScript.length() > endIndex) {
                char at = javaScript.charAt(endIndex);

                if (at == '}' && countOfBrakets == 1) {
                    endIndex++;
                    break;
                }

                if (at == '{') {
                    countOfBrakets++;
                } else if (at == '}') {
                    countOfBrakets--;
                }
                endIndex++;
            }
            functions.add(javaScript.substring(i, endIndex));
            i = javaScript.indexOf("function " + prefix, i + 1);
        }
        return functions;
    }

    public static String getFunctionWithName(String javaScript, String name) {
        Set<String> functionsWithPrefix = getFunctionsWithPrefix(javaScript, name);
        return functionsWithPrefix.isEmpty() ? null : functionsWithPrefix.iterator().next();
    }

    public String getFormattedJavaScript(EntityContext entityContext, Compilable engine) {
        String jsonParams = StringUtils.defaultIfEmpty(javaScriptParameters, "{}");
        long hash = StringUtils.defaultIfEmpty(javaScript, "").hashCode() + jsonParams.hashCode();
        if (this.formattedJavaScriptHash != hash) {
            this.formattedJavaScriptHash = hash;

            Environment env = entityContext.getBean(Environment.class);
            JSONObject params = new JSONObject(jsonParams);
            String envFormattedJavaScript = SpringUtils.replaceEnvValues(javaScript, (key, defValue) -> {
                if (params.has(key)) {
                    return params.get(key).toString();
                }
                return env.getProperty(key, defValue);
            });
            this.formattedJavaScript = detectReplaceableValues(params, engine, envFormattedJavaScript);
        }
        return this.formattedJavaScript;
    }

    @SneakyThrows
    private String detectReplaceableValues(JSONObject params, Compilable engine, String formattedJavaScript) {
        List<String> patternValues = SpringUtils.getPatternValues(SpringUtils.HASH_PATTERN, formattedJavaScript);
        if (!patternValues.isEmpty()) {
            StringBuilder sb = new StringBuilder(formattedJavaScript);
            for (String patternValue : patternValues) {
                String fnName = "rpl_" + Math.abs(patternValue.hashCode());
                sb.append("\nfunction ").append(fnName).append("() { ").append(patternValue.contains("return ") ? patternValue : "return " + patternValue).append(" }");
            }

            // fire rpl functions
            String jsWithRplFunctions = sb.toString();
            CompiledScript cmpl = engine.compile(new StringReader(jsWithRplFunctions));
            cmpl.eval();
            return SpringUtils.replaceHashValues(jsWithRplFunctions, (ThrowingBinaryOperator<String, Exception>) (s, s2) -> {
                Object ret = ((Invocable) cmpl.getEngine()).invokeFunction("rpl_" + Math.abs(s.hashCode()), params);
                return ret == null ? "" : ret.toString();
            });
        }
        return formattedJavaScript;
    }
}
