package org.homio.app.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Transient;
import java.io.StringReader;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.Invocable;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;
import org.homio.api.EntityContext;
import org.homio.api.converter.JSONConverter;
import org.homio.api.entity.BaseEntity;
import org.homio.api.entity.HasJsonData;
import org.homio.api.model.JSON;
import org.homio.api.model.Status;
import org.homio.api.ui.UISidebarMenu;
import org.homio.api.ui.field.MonacoLanguage;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldCodeEditor;
import org.homio.api.util.SpringUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.springframework.core.env.Environment;

@Entity
@UISidebarMenu(icon = "fab fa-js-square", order = 1, bg = "#9e7d18", allowCreateNewItems = true,
               overridePath = "scripts")
@Accessors(chain = true)
public class ScriptEntity extends BaseEntity<ScriptEntity> implements HasJsonData {

    public static final String PREFIX = "script_";

    @Getter
    @Setter
    @Column(length = 10_000)
    @Convert(converter = JSONConverter.class)
    @NotNull
    private JSON jsonData = new JSON();

    @Getter
    @Setter
    @Column(nullable = false)
    private Status status = Status.UNKNOWN;

    @Getter
    @Setter
    @UIField(order = 13)
    @Column(length = 65_535)
    @UIFieldCodeEditor(editorType = MonacoLanguage.Json, autoFormat = true)
    private String javaScriptParameters = "{}";

    @Getter
    @Setter
    @UIField(order = 16, inlineEdit = true)
    private boolean autoStart = false;

    @Column(length = 1_000)
    @Getter @Setter private String error;

    @Getter
    @Setter
    @UIField(order = 30)
    @Column(length = 65_535)
    @UIFieldCodeEditor(editorType = MonacoLanguage.JavaScript, autoFormat = true)
    private String javaScript = "function before() { };\nfunction run() { };\nfunction after() { };";

    @Transient @JsonIgnore private long formattedJavaScriptHash;

    @Getter
    @Setter
    @UIField(order = 40)
    private int repeatInterval = 0;

    @Transient @JsonIgnore private String formattedJavaScript;

    public static Set<String> getFunctionsWithPrefix(String javaScript, String prefix) {
        Set<String> functions = new HashSet<>();
        int i = javaScript.indexOf("function " + prefix);
        while (i >= 0) {
            int endIndex = i + 1;
            int countOfBrackets = 0;
            while (javaScript.length() > endIndex) {
                char at = javaScript.charAt(endIndex);

                if (at == '}' && countOfBrackets == 1) {
                    endIndex++;
                    break;
                }

                if (at == '{') {
                    countOfBrackets++;
                } else if (at == '}') {
                    countOfBrackets--;
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
            String envFormattedJavaScript = SpringUtils.replaceEnvValues(javaScript,
                (key, defValue, prefix) -> {
                    if (params.has(key)) {
                        return params.get(key).toString();
                    }
                    return env.getProperty(key, defValue);
                });
            this.formattedJavaScript = detectReplaceableValues(params, engine, envFormattedJavaScript);
        }
        return this.formattedJavaScript;
    }

    @Override
    public @NotNull String getEntityPrefix() {
        return PREFIX;
    }

    @Override
    public String getDefaultName() {
        return "Script";
    }

    @Override
    protected int getChildEntityHashCode() {
        return 0;
    }

    @SneakyThrows
    private String detectReplaceableValues(
        JSONObject params, Compilable engine, String formattedJavaScript) {
        List<String> patternValues = SpringUtils.getPatternValues(SpringUtils.HASH_PATTERN, formattedJavaScript);
        if (!patternValues.isEmpty()) {
            StringBuilder sb = new StringBuilder(formattedJavaScript);
            for (String patternValue : patternValues) {
                String fnName = "rpl_" + Math.abs(patternValue.hashCode());
                sb.append("\nfunction ").append(fnName).append("() {")
                  .append(patternValue.contains("return ") ? patternValue : "return " + patternValue)
                  .append(" }");
            }

            // fire rpl functions
            String jsWithRplFunctions = sb.toString();
            CompiledScript compileScript = engine.compile(new StringReader(jsWithRplFunctions));
            compileScript.eval();
            return SpringUtils.replaceHashValues(jsWithRplFunctions,
                (s, s2, prefix) -> {
                    Object ret = ((Invocable) compileScript.getEngine()).invokeFunction("rpl_" + Math.abs(s.hashCode()), params);
                    return ret == null ? "" : ret.toString();
                });
        }
        return formattedJavaScript;
    }
}
