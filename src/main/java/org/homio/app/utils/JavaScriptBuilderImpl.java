package org.homio.app.utils;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import org.homio.api.EntityContext;
import org.homio.api.widget.JavaScriptBuilder;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class JavaScriptBuilderImpl implements JavaScriptBuilder {

    private String rawContent;

    @Getter
    private final JSONObject jsonParams = new JSONObject();
    private final Set<String> css = new HashSet<>();
    private final Map<String, JsMethodImpl> jsMethods = new LinkedHashMap<>();
    private final Class<?> aClass;
    private JSContentImpl jsContentImpl;
    private JsMethodImpl beforeFunc;
    private final JsServerVariables jsServerVariables = new JsServerVariables();
    @Getter
    private boolean jsonReadOnly;

    public JavaScriptBuilderImpl(Class<?> aClass) {
        this.aClass = aClass;
    }

    public String build() {
        StringBuilder builder = new StringBuilder();

        if (!css.isEmpty()) {
            ((JsMethodImpl) this.readyOnClient())
                    .addChild(() -> "this.addGlobalStyle('" + String.join(" ", css) + "')", 1);
        }

        for (JsMethodImpl jsMethodImpl : jsMethods.values()) {
            builder.append(jsMethodImpl.build());
        }

        if (!jsServerVariables.variables.isEmpty()) {
            JsMethodImpl readVariablesOnServerValues =
                    new JsMethodImpl("readVariablesOnServerValues", new String[0], false, 0);
            readVariablesOnServerValues.raw(
                    () ->
                            "return ["
                                    + String.join(" \", \"", jsServerVariables.variables.values())
                                    + "];");
            builder.append(readVariablesOnServerValues.build());

            JsMethodImpl readVariablesOnServerKeys =
                    new JsMethodImpl("readVariablesOnServerKeys", new String[0], false, 0);
            readVariablesOnServerKeys.raw(
                    () ->
                            "return ["
                                    + String.join(" \", \"", jsServerVariables.variables.keySet())
                                    + "];");
            builder.append(readVariablesOnServerKeys.build());
        }

        if (this.beforeFunc != null) {
            builder.append("\n\n").append(beforeFunc.build());
        }

        builder.append("\n\nfunction run() {\n");
        /*for (FetchEntity fetchEntity : fetchEntities) {
            builder.append("var ").append(fetchEntity.name).append(" = manager.getEntity('").append(fetchEntity.entityID)
            .append("');\n");
            if (fetchEntity.errorOnNotFound) {
                builder.append("if(").append(fetchEntity.name).append("==null)return 'Error: Entity with entityID <").append
                (fetchEntity.entityID).append("> not found';\n");
            }
        }*/

        if (rawContent != null) {
            builder.append(rawContent);
        } else if (jsContentImpl != null) {
            builder.append(jsContentImpl.build());
        }

        return builder.append("\n}").toString();
    }

    @Override
    public void rawContent(String content) {
        this.rawContent = content;
    }

    @Override
    public JavaScriptBuilderImpl jsonParam(String key, Object value) {
        // this.jsServerVariables.variables.put(key, "'" + value.toString() + "'");
        // this.globalParams.add(key + " = params.get(\"" + key + "\");\n");
        this.jsonParams.put(key, value);
        return this;
    }

    @Override
    public JavaScriptBuilderImpl css(String className, String... values) {
        this.css.add("." + className + " { " + String.join(";", values) + "}");
        return this;
    }

    /* @Override
    public FetchEntity fetchEntity(String name, String entityID) {
        FetchEntity fetchEntity = new FetchEntity(name, entityID);
        fetchEntities.add(fetchEntity);
        return fetchEntity;
    }*/

    /* @Override
    public JavaScriptBuilderImpl array(String name, String... values) {
        StringJoiner joiner = new StringJoiner(",");
        for (String value : values) {
            joiner.add("'" + value + "'");
        }
        this.globalParams.add("var " + name + "=[" + joiner.toString() + "];\n");
        return this;
    }*/

    @Override
    public void setJsonReadOnly() {
        this.jsonReadOnly = true;
    }

    @Override
    public JsMethodImpl js(String methodName, String... params) {
        if (!methodName.startsWith("js_")) {
            throw new IllegalArgumentException("MethodName has to have 'js_' prefix");
        }
        this.jsMethods.computeIfAbsent(
                methodName, s -> new JsMethodImpl(methodName, params, false, 0));
        return this.jsMethods.get(methodName);
    }

    @Override
    public JSContent jsContent() {
        this.jsContentImpl = new JSContentImpl();
        return jsContentImpl;
    }

    @Override
    public void wsHandler(String[] params, Consumer<JsMethod> jsMethodConsumer) {
        // scope().func("wsHandler", params, jsMethodConsumer);
    }

    @Override
    public JsMethod beforeFunc() {
        this.beforeFunc = new JsMethodImpl("before", new String[0], false, 0);
        return beforeFunc;
    }

    @Override
    public JsMethod readyOnClient() {
        this.jsMethods.computeIfAbsent(
                "readyOnClient", s -> new JsMethodImpl("readyOnClient", new String[0], false, 0));
        return this.jsMethods.get("readyOnClient");
    }

    private static String appendPOST(
            String request, String params, boolean singleLine, String tabs) {
        return ("$.ajax({type: \"POST\"," + "url: window.location.origin + \"/rest/widget/")
                + request
                + "\","
                + (singleLine ? "\\\n" : "\n")
                + tabs
                + "\tdataType: \"json\",async: false,contentType: \"application/json; charset=utf-8\","
                + (singleLine ? "\\\n" : "\n")
                + tabs
                + "\tdata: JSON.stringify("
                + params
                + "),"
                + (singleLine ? "\\\n" : "\n")
                + tabs
                + "\tsuccess: function () {},"
                + (singleLine ? "\\\n" : "\n")
                + tabs
                + "\terror: showException"
                + (singleLine ? "\\\n" : "\n")
                + tabs
                + "});";
    }

    private void addTag(
            List<JSInput> jsInputs,
            String tag,
            int level,
            JSInputImpl parent,
            Consumer<JSStyle> jsStyleContext,
            Consumer<JSInput> jsInputContext) {
        JSInputImpl jsInputImpl = new JSInputImpl(tag, null, level, parent, jsStyleContext);
        jsInputs.add(jsInputImpl);
        jsInputContext.accept(jsInputImpl);
    }

    public static class JsServerVariables {

        private final Map<String, String> variables = new LinkedHashMap<>();
    }

    public static class JSONParameterContextImpl implements JSONParameterContext {

        final Map<String, JSONParameter> parameters = new HashMap<>();
        final Map<String, JSONParameter> arrays = new HashMap<>();

        @Override
        public JSONParameter obj(String name) {
            return parameters.computeIfAbsent(name, key -> new JSONParameterImpl());
        }

        @Override
        public JSONParameter array(String name) {
            return arrays.computeIfAbsent(name, key -> new JSONParameterImpl());
        }
    }

    public static class JSONParameterImpl implements JSONParameter {

        JSONObject object = new JSONObject();
        private Object current = object;

        @Override
        public String toString(int indent) {
            return object.toString(indent);
        }

        @Override
        public JSONParameter obj(String key) {
            JSONObject jsObject = new JSONObject();
            if (current instanceof JSONObject) {
                ((JSONObject) current).put(key, jsObject);
            } else {
                ((JSONArray) current).put(jsObject);
            }
            current = jsObject;
            return this;
        }

        @Override
        public JSONParameter array(String key) {
            JSONArray jsonArray = new JSONArray();
            if (current instanceof JSONObject) {
                ((JSONObject) current).put(key, jsonArray);
            } else {
                ((JSONArray) current).put(jsonArray);
            }
            current = jsonArray;
            return this;
        }

        @Override
        public JSONParameterImpl value(String key, String value) {
            object.put(key, value);
            return this;
        }

        @Override
        public JSONParameterImpl value(String key, Consumer<JSONObject> consumer) {
            consumer.accept(object);
            return this;
        }

        @Override
        public JSONParameter value(String key, EvaluableValue evaluableValue) {
            object.put(key, "#{" + evaluableValue.get() + "}");
            return this;
        }

        @Override
        public JSONParameter value(String key, ProxyEntityContextValue proxyEntityContextValue) {
            StringBuilder builder = new StringBuilder("return entityContext");
            proxyEntityContextValue.apply(createProxyInstance(EntityContext.class, builder));
            object.put(key, "#{" + builder.append(";") + "}");
            return this;
        }

        private <T> T createProxyInstance(Class<T> clazz, StringBuilder builder) {
            return (T) Enhancer.create(clazz, createProxyHandler(builder));
        }

        private MethodInterceptor createProxyHandler(StringBuilder builder) {
            return (obj, method, args, proxy) -> {
                builder.append(".")
                        .append(method.getName())
                        .append("(")
                        .append(evalProxyArguments(args))
                        .append(")");
                if (method.getReturnType().getSimpleName().equals(Object.class.getSimpleName())) {
                    return null;
                }
                return createProxyInstance(method.getReturnType(), builder);
            };
        }

        private String evalProxyArguments(Object[] args) {
            StringBuilder argBuilder = new StringBuilder();
            for (Object arg : args) {
                if (arg instanceof Class) {
                    argBuilder
                            .append("Java.type('")
                            .append(((Class) arg).getName())
                            .append("').class");
                } else {
                    argBuilder.append(arg);
                }
            }
            return argBuilder.toString();
        }
    }

    public static class JSWindowImpl extends JSONParameterContextImpl implements JSWindow {

        private String build(String tabs) {
            StringBuilder builder = new StringBuilder();
            for (Map.Entry<String, JSONParameter> entry : arrays.entrySet()) {
                builder.append("window.")
                        .append(entry.getKey())
                        .append(" = (window.")
                        .append(entry.getKey())
                        .append(" || []).concat(")
                        .append("\n")
                        .append(tabs)
                        .append(entry.getValue().toString(tabs.length() * 4 + 4))
                        .append(tabs)
                        .append(");");
            }
            for (Map.Entry<String, JSONParameter> entry : parameters.entrySet()) {
                builder.append("window.")
                        .append(entry.getKey())
                        .append(" = ")
                        .append(entry.getValue().toString());
            }
            return builder.toString();
        }
    }

    /*public class JSObject implements Builder {
        JSONObject object = new JSONObject();

        public JSList array(String name) {
            JSList jsList = new JSList();
            object.put(name, jsList.list);
            return jsList;
        }

        public JSObject obj(String name) {
            JSObject jsObject = new JSObject();
            object.put(name, jsObject);
            return jsObject;
        }

        public JSObject add(String key, Object value) {
            object.put(key, value);
            return this;
        }

        @Override
        public String build() {
            for (String key : object.keySet()) {
                if (object.get(key) instanceof JSObject) {
                    object.put(key, ((JSObject) object.get(key)).object);
                }
            }
            return object.toString();
        }
    }*/

    /* public class JSList {
        JSONArray list = new JSONArray();

        public JSObject obj() {
            JSObject jsObject = new JSObject();
            list.put(jsObject.object);
            return jsObject;
        }

        public void consumer(Consumer<JSList> jsListConsumer) {
            jsListConsumer.accept(this);
        }
    }*/

    abstract static class CommonBuilder implements Builder {

        final int level;
        final String tabs;
        final String tabsUp;
        final String tabsDown;
        @Getter
        private final List<Builder> children = new ArrayList<>();

        CommonBuilder(int level) {
            this.level = level;
            char[] chars = new char[level];
            Arrays.fill(chars, '\t');
            tabs = new String(chars);

            chars = new char[level + 1];
            Arrays.fill(chars, '\t');
            tabsUp = new String(chars);

            if (level > 0) {
                chars = new char[level - 1];
                Arrays.fill(chars, '\t');
                tabsDown = new String(chars);
            } else {
                tabsDown = tabs;
            }
        }

        public void addChild(Builder builder) {
            children.add(() -> tabs + builder.build());
        }

        public void addChild(Builder builder, int index) {
            children.add(index, () -> tabs + builder.build());
        }

        public void addChildNoTab(Builder builder) {
            children.add(builder);
        }

        @Override
        public String build() {
            StringBuilder builder = new StringBuilder();
            for (Builder child : this.children) {
                builder.append(child.build()).append("\n");
            }
            builder.append(onEnd());
            return builder.toString();
        }

        public String onEnd() {
            return "";
        }

        public <T extends Builder> void child(Consumer<T> consumer, T block) {
            this.children.add(block);
            consumer.accept(block);
        }
    }

    public static class JsCondImpl extends CommonBuilder implements JsCond {

        private JsCondImpl(int level) {
            super(level);
            this.addChild(() -> "if(");
        }

        @Override
        public String onEnd() {
            return ")";
        }

        @Override
        public JsCond eq(String cond1, String cond2, boolean escapeSecondCondition) {
            this.addChild(() -> "if(");
            this.addChild(
                    new JsBlockImpl(
                            cond1
                                    + " === "
                                    + (escapeSecondCondition ? "\\'" + cond2 + "\\'" : cond2)));
            return this;
        }

        @Override
        public JsCond bool(String cond) {
            this.addChildNoTab(new JsBlockImpl(cond));
            return this;
        }

        @Override
        public JsCond and() {
            this.addChildNoTab(new JsBlockImpl(" && "));
            return this;
        }
    }

    public static class SBuilder implements Builder {

        private final String value;

        private SBuilder(String value) {
            this.value = value;
        }

        @Override
        public String build() {
            return value;
        }
    }

    public static class JsBlockImpl extends CommonBuilder {

        private JsBlockImpl(String value) {
            super(0);
            this.addChildNoTab(() -> value);
        }
    }

    @AllArgsConstructor
    public class JSStyleImpl implements JSStyle {

        private final StringBuilder builder = new StringBuilder();

        @Override
        public JSStyle clazz(String clazz) {
            builder.append(" class=\"").append(clazz).append("\"");
            return this;
        }

        @Override
        public JSStyle style(String style) {
            builder.append(" style=\"").append(style).append("\"");
            return this;
        }

        @Override
        public JSStyle ngIf(String condition) {
            return attr("*ngIf", condition);
        }

        @Override
        public JSStyle id(String id) {
            return attr("id", id);
        }

        @Override
        public JSStyle ngStyleIf(String condition, String style, String value, String otherValue) {
            builder.append(" [style]=\"")
                    .append("{")
                    .append("\\'")
                    .append(style)
                    .append("\\'")
                    .append(":")
                    .append(condition)
                    .append(" ? \\'")
                    .append(value)
                    .append("\\' : \\'")
                    .append(otherValue)
                    .append("\\'}\"");
            return this;
        }

        @Override
        public JSStyle ngRepeat(String key, String value) {
            builder.append(" *ngFor=let \"").append(key).append(" of ").append(value).append("\"");
            return this;
        }

        @Override
        public JSStyle onClick(String content) {
            builder.append(" (click)=\"");
            builder.append(content).append("\"");
            return this;
        }

        @Override
        public JSStyleImpl onClick(JsMethod jsMethod, String... params) {
            JsMethodImpl jsMethodImpl = (JsMethodImpl) jsMethod;
            builder.append(" (click)=\"").append(jsMethodImpl.name).append("(");
            if (params.length != jsMethodImpl.params.length) {
                throw new IllegalArgumentException(
                        "JsMethod: "
                                + jsMethod
                                + "has different parameter count that onClick handling");
            }
            for (int i = 0; i < params.length; i++) {
                String param = params[i];
                builder.append("\\'' + ").append(param).append(" + '\\'");
                if (i < params.length - 1) {
                    builder.append(", ");
                }
            }
            builder.append(")\"");
            return this;
        }

        @Override
        public JSStyleImpl attr(String attr, String value) {
            builder.append(" ").append(attr).append("=\"").append(value).append("\"");
            return this;
        }
    }

    public class JSInputImpl implements JSInput {

        private final StringBuilder builder = new StringBuilder();
        private final String inputType;
        private String inputContent;
        private final int level;
        private final List<JSInput> jsInputs = new ArrayList<>();
        private final JSInputImpl parent;
        private final String tabs;

        private JSInputImpl(
                String input,
                String inputContent,
                int level,
                JSInput parent,
                Consumer<JSStyle> jsStyleContext) {
            this.inputType = input;
            this.inputContent = inputContent;
            this.level = level;
            this.parent = (JSInputImpl) parent;
            char[] chars = new char[level];
            Arrays.fill(chars, '\t');
            tabs = new String(chars);
            builder.append("<").append(input);
            JSStyleImpl jsStyleImpl = new JSStyleImpl();
            if (jsStyleContext != null) {
                jsStyleContext.accept(jsStyleImpl);
            }
            builder.append(jsStyleImpl.builder);
            builder.append(">\\\n").append(tabs);
        }

        public String build() {
            for (JSInput jsInput : jsInputs) {
                builder.append(jsInput.build());
            }
            if (inputContent != null) {
                builder.append(inputContent);
            }
            builder.append("\\\n")
                    .append(parent == null ? "" : parent.tabs)
                    .append("</")
                    .append(inputType)
                    .append(">\\\n")
                    .append(parent == null ? "" : parent.tabs);

            return builder.toString();
        }

        @Override
        public void div(Consumer<JSInput> jsInput) {
            div(null, jsInput);
        }

        @Override
        public void div(Consumer<JSStyle> jsStyle, Consumer<JSInput> jsInput) {
            addTag(jsInputs, "div", level + 1, this, jsStyle, jsInput);
        }

        @Override
        public void span(Consumer<JSInput> jsInput) {
            span(null, jsInput);
        }

        @Override
        public void span(Consumer<JSStyle> jsStyle, Consumer<JSInput> jsInput) {
            addTag(jsInputs, "span", level + 1, this, jsStyle, jsInput);
        }

        @Override
        public void ngLabel(Consumer<JSStyle> jsStyle, Consumer<JSInput> jsInput) {
            addTag(jsInputs, "label", level + 1, this, jsStyle, jsInput);
        }

        @Override
        public JSInputImpl innerHtml(String innerHtml) {
            this.inputContent = innerHtml;
            return this;
        }

        @Override
        public JSInputImpl bind(String bindKey) {
            String code = JavaScriptBuilder.buildBind(bindKey);
            if (this.inputContent != null) {
                this.inputContent += code;
            } else {
                this.inputContent = code;
            }
            return this;
        }
    }

    public class JSAjaxPostImpl extends CommonBuilder implements JSAjaxPost {

        private final String[] params;
        private final boolean ngFunc;
        private final Map<String, String> additionalParams = new HashMap<>();

        JSAjaxPostImpl(JsMethodImpl jsMethodImpl, int level) {
            super(level);
            this.params = jsMethodImpl.params;
            this.ngFunc = jsMethodImpl.ngFunc;
        }

        @Override
        public void param(String paramKey, String paramValue) {
            additionalParams.put(paramKey, paramValue);
        }

        @Override
        public String build() {
            List<String> parameters = new ArrayList<>();
            for (Map.Entry<String, String> item : additionalParams.entrySet()) {
                parameters.add(item.getKey() + ":\"" + item.getValue() + "\"");
            }
            for (String param : params) {
                if (!param.equals("$event")) {
                    parameters.add(param + ":" + param);
                }
            }
            String paramJson = parameters.stream().collect(Collectors.joining(",", "{", "}"));
            return appendPOST("request/handle/" + aClass.getSimpleName(), paramJson, ngFunc, tabs);
        }
    }

    public class JSContentImpl implements JSContent {

        private final StringBuilder builder = new StringBuilder();
        private final List<JSInput> jsInputs = new ArrayList<>();

        @Override
        public JSContent add(JSInput jsInput) {
            builder.append(jsInput.build());
            return this;
        }

        @Override
        public void div(Consumer<JSInput> div) {
            div(null, div);
        }

        @Override
        public void div(Consumer<JSStyle> jsStyle, Consumer<JSInput> div) {
            addTag(jsInputs, "div", 1, null, jsStyle, div);
        }

        public String build() {
            for (JSInput jsInput : jsInputs) {
                builder.append(jsInput.build());
            }
            rawContent = "return '" + builder.toString() + "';";
            return rawContent;
        }

        @Override
        public void button(Consumer<JSStyle> jsStyle, Consumer<JSInput> button) {
            addTag(jsInputs, "button", 1, null, jsStyle, button);
        }
    }

    public class JsCondBodyImpl extends CommonBuilder implements JsCondBody {

        private final boolean isTextContext;

        JsCondBodyImpl(int level, boolean isTextContext) {
            super(level);
            this.isTextContext = isTextContext;
        }

        /*public void then(String thenValue) {
            this.children.add(new JsBlock(" { \\\n" + tabsUp + thenValue + " \\\n" + tabs + "}\\\n"));
        }*/

        @Override
        public void then(Consumer<JSCodeContext<JsCondBody>> jsCode) {
            this.addChildNoTab(() -> " {");
            child(jsCode, new JSCodeContextImpl<>(level, isTextContext));
            this.addChild(() -> "}");
        }
    }

    public class JsMethodImpl extends JSCodeContextImpl<JsMethod> implements JsMethod {

        private final String[] params;
        public String name;
        private final boolean ngFunc;

        JsMethodImpl(String methodName, String[] params, boolean ngFunc, int level) {
            super(level, ngFunc);
            this.name = methodName;
            this.params = params;
            this.ngFunc = ngFunc;
            if (ngFunc) {
                this.addChildNoTab(
                        new SBuilder(
                                "this."
                                        + methodName
                                        + " = function("
                                        + String.join(",", params)
                                        + ") {"));
            } else {
                this.addChildNoTab(
                        () -> "function " + methodName + "(" + String.join(",", params) + ") {");
            }
        }

        @Override
        public void post(Consumer<JSAjaxPost> jsAjaxPostConsumer) {
            child(jsAjaxPostConsumer, new JSAjaxPostImpl(this, level));
        }

        @SneakyThrows
        @Override
        public void post(String request, String params) {
            this.addChild(() -> appendPOST(request, params, false, tabs));
        }

        @Override
        public JsMethod clientJs(String clientCode) {
            this.addChild(() -> clientCode);
            return this;
        }

        @Override
        public String onEnd() {
            return "}\n\n";
        }
    }

    public class JSCodeContextImpl<T> extends CommonBuilder implements JSCodeContext<T> {

        private final boolean isTextContext;

        public JSCodeContextImpl(int level, boolean isTextContext) {
            super(level + 1);
            this.isTextContext = isTextContext;
        }

        @Override
        public JSCodeContext<T> raw(Supplier<String> jsStringBlock) {
            this.addChild(() -> jsStringBlock.get() + (isTextContext ? "\\" : ""));
            return this;
        }

        @Override
        public void cond(
                Consumer<JsCond> jsCondConsumerContext, Consumer<JsCondBody> methodContext) {
            child(jsCondConsumerContext, new JsCondImpl(level));
            child(methodContext, new JsCondBodyImpl(level, isTextContext));
        }

        @Override
        public void iter(String key, String array, Consumer<IterContext> iterContext) {
            child(iterContext, new IterContextImpl(key, array, isTextContext, level));
        }

        @Override
        public JSCodeContext<T> window(Consumer<JSWindow> jsWindowConsumer) {
            JSWindowImpl jsWindowImpl = new JSWindowImpl();
            jsWindowConsumer.accept(jsWindowImpl);
            this.addChild(() -> jsWindowImpl.build(tabs));
            return this;
        }

        @Override
        public JSCodeContext<T> addGlobalScript(String path) {
            this.addChild(() -> "this.addGlobalScript('" + path + "')");
            return this;
        }

        @Override
        public JSCodeContext<T> addGlobalLink(String path) {
            this.addChild(() -> "this.addGlobalLink('" + path + "')");
            return this;
        }
    }

    public class IterContextImpl extends JsCondBodyImpl implements IterContext {

        IterContextImpl(String key, String array, boolean isTextContext, int level) {
            super(level, isTextContext);
            this.addChild(() -> tabs + "for(let " + key + " of " + array + ")");
        }
    }
}
