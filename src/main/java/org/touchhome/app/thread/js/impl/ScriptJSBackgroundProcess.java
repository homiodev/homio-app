package org.touchhome.app.thread.js.impl;

import jdk.nashorn.api.scripting.ScriptObjectMirror;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.touchhome.app.manager.ScriptManager;
import org.touchhome.app.model.CompileScriptContext;
import org.touchhome.app.model.entity.ScriptEntity;
import org.touchhome.app.thread.js.AbstractJSBackgroundProcessService;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.thread.BackgroundProcessStatus;
import org.touchhome.bundle.api.util.TouchHomeUtils;

import javax.script.Invocable;
import javax.script.ScriptException;
import java.util.Set;

import static org.touchhome.app.manager.ScriptManager.REPEAT_EVERY;

/**
 * Run script once until it finished or exception or stop requested
 */
public class ScriptJSBackgroundProcess extends AbstractJSBackgroundProcessService<Object> {

    private final ScriptManager scriptManager;

    public ScriptJSBackgroundProcess(ScriptEntity scriptEntity, EntityContext entityContext) {
        super(scriptEntity, entityContext);
        this.scriptManager = entityContext.getBean(ScriptManager.class);
    }

    public static String runJavaScript(CompileScriptContext compileScriptContext, JSONObject params) throws ScriptException, NoSuchMethodException {
        StringBuilder script = new StringBuilder();

        Set<String> functions = ScriptEntity.getFunctionsWithPrefix(compileScriptContext.getFormattedJavaScript(), "js_");
        if (!functions.isEmpty()) {
            script.append("JS_BLOCK(");
            for (String function : functions) {
                script.append(function);
            }
            script.append(")JS_BLOCK");
        }

        appendFunc(script, "readyOnClient", "READY_BLOCK", compileScriptContext.getFormattedJavaScript());

        Object value = ((Invocable) compileScriptContext.getCompiledScript().getEngine()).invokeFunction("run", params);
        if (value instanceof ScriptObjectMirror) {
            ScriptObjectMirror obj = (ScriptObjectMirror) value;
            JSONObject jsonObject = new JSONObject();
            for (String key : obj.keySet()) {
                jsonObject.put(key, obj.get(key));
            }
            script.append(jsonObject);
        } else {
            script.append(value);
        }

        return script.toString();
    }

    private static void appendFunc(StringBuilder script, String funcName, String separator, String javaScript) {
        String result = ScriptEntity.getFunctionWithName(javaScript, funcName);
        if (result != null) {
            script.append(separator).append("(").append(result).append(")").append(separator);
        }
    }

    @Override
    public Object runInternal() {
        try {
            return runJavaScript(getCompiledScript(), params);
        } catch (Exception ex) {
            String msg = TouchHomeUtils.getErrorMessage(ex);
            setStatus(BackgroundProcessStatus.FAILED, msg);
            logError("Error while call script with id: <{}>. Msg: <{}>", scriptEntity.getEntityID(), msg);
            throw new RuntimeException(ex);
        }
    }

    @Override
    public boolean onException(Exception ex) {
        logError("Error while trying call action in thread for item: <{}>", scriptEntity.getTitle());
        return false;
    }

    @Override
    public long getPeriod() {
        if (getCompiledScript().getEngine().get(REPEAT_EVERY) != null) {
            return (Integer) getCompiledScript().getEngine().get(REPEAT_EVERY);
        }
        return 0;
    }

    @Override
    public boolean canWork() {
        return true;
    }

    @Override
    protected boolean isAutoStart() {
        return scriptEntity.isAutoStart();
    }

    @Override
    public Logger createLogger() {
        return ((Logger) getCompiledScript().getEngine().get("log"));
    }

    @Override
    public void beforeStart() {
        scriptManager.invokeBeforeFunction(getCompiledScript(), params);
    }

    @Override
    public void afterStop() {
        scriptManager.invokeAfterFunction(getCompiledScript(), params);
    }
}
