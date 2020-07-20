package org.touchhome.app.thread.js.impl;

import jdk.nashorn.api.scripting.ScriptObjectMirror;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.touchhome.app.manager.scripting.ScriptManager;
import org.touchhome.app.model.entity.ScriptEntity;
import org.touchhome.app.thread.js.AbstractJSBackgroundProcessService;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.thread.BackgroundProcessStatus;
import org.touchhome.bundle.api.util.TouchHomeUtils;

import javax.script.Invocable;
import java.util.Iterator;
import java.util.Set;

import static org.touchhome.app.manager.scripting.ScriptManager.REPEAT_EVERY;

/**
 * Run script once until it finished or exception or stop requested
 */
public class ScriptJSBackgroundProcess extends AbstractJSBackgroundProcessService<Object> {

    private final ScriptManager scriptManager;
    private JSONObject jsonObj;

    public ScriptJSBackgroundProcess(ScriptEntity scriptEntity, EntityContext entityContext) {
        super(scriptEntity, entityContext);
        this.scriptManager = entityContext.getBean(ScriptManager.class);
    }

    private JSONObject getJsonObj() {
        if (jsonObj == null) {
            jsonObj = scriptManager.assertJsonParams(scriptEntity, scriptEntity.getJavaScriptParameters());
        }
        return jsonObj;
    }

    @Override
    public Object runInternal() {
        try {
            StringBuilder script = new StringBuilder();

            Object cssBlock = scriptManager.invokeFunction(getCompiledScript(), "css_block", null, false);
            if (cssBlock != null) {
                script.append("CSS_BLOCK(").append(cssBlock).append(")CSS_BLOCK");
            }

            Object scopeBlock = scriptManager.invokeFunction(getCompiledScript(), "scope_block", null, false);
            if (scopeBlock != null) {
                script.append("SCOPE_BLOCK(").append(scopeBlock).append(")SCOPE_BLOCK");
            }

            Set<String> functions = scriptEntity.getFunctionsWithPrefix("js_");
            if (!functions.isEmpty()) {
                script.append("JS_BLOCK(");
                for (String function : functions) {
                    script.append(function);
                }
                script.append(")JS_BLOCK");
            }
            String onReadyOnClient = scriptEntity.getFunctionWithName("onReadyOnClient");
            if (onReadyOnClient != null) {
                script.append("READY_BLOCK(").append(onReadyOnClient).append(")READY_BLOCK");
            }

            String readVariablesOnServerValues = scriptEntity.getFunctionWithName("readVariablesOnServerValues");
            if (readVariablesOnServerValues != null) {
                ScriptObjectMirror serverVariables = (ScriptObjectMirror) ((Invocable) getCompiledScript().getEngine()).invokeFunction("readVariablesOnServerValues", getJsonObj());
                ScriptObjectMirror serverKeys = (ScriptObjectMirror) ((Invocable) getCompiledScript().getEngine()).invokeFunction("readVariablesOnServerKeys", getJsonObj());
                script.append("VARIABLE_BLOCK(");
                Iterator<Object> serverVariablesIterator = serverVariables.values().iterator();
                for (Object serviceKey : serverKeys.values()) {
                    script.append(serviceKey).append("=").append(serverVariablesIterator.next()).append(";");
                }
                script.append(")VARIABLE_BLOCK");
            }

            Object value = ((Invocable) getCompiledScript().getEngine()).invokeFunction("run", getJsonObj());
            script.append(value);

            return script.toString();
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
        scriptManager.invokeBeforeFunction(getCompiledScript(), getJsonObj());
    }

    @Override
    public void afterStop() {
        scriptManager.invokeAfterFunction(getCompiledScript(), getJsonObj());
    }
}
