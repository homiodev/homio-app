package org.touchhome.app.thread.js;

import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.touchhome.app.manager.ScriptManager;
import org.touchhome.app.model.CompileScriptContext;
import org.touchhome.app.model.entity.ScriptEntity;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.thread.BackgroundProcessService;
import org.touchhome.bundle.api.thread.BackgroundProcessStatus;

import static org.touchhome.bundle.api.thread.BackgroundProcessStatus.*;

public abstract class AbstractJSBackgroundProcessService<ReturnType> extends BackgroundProcessService<ReturnType> {

    protected final ScriptEntity scriptEntity;
    protected final JSONObject params;
    private final ScriptManager scriptManager;
    private CompileScriptContext compiled;

    public AbstractJSBackgroundProcessService(ScriptEntity scriptEntity, EntityContext entityContext) {
        super(scriptEntity.getBackgroundProcessServiceID(), entityContext);

        this.scriptEntity = scriptEntity;
        this.params = new JSONObject(scriptEntity.getJavaScriptParameters());
        this.scriptManager = entityContext.getBean(ScriptManager.class);
    }

    @Override
    public boolean shouldStartNow() {
        BackgroundProcessStatus status = scriptEntity.getBackgroundProcessStatus();
        return status != STOP && canWorkSafe() && (status == RUNNING || status == RESTARTING || isAutoStart());
    }

    @Override
    public String getName() {
        return getClass().getSimpleName() + "_" + scriptEntity.getTitle();
    }

    // this method not stops bgp
    public void setStatus(BackgroundProcessStatus status, String errorMessage) {
        if (scriptEntity.getId() != null && scriptEntity.setScriptStatus(status)) { // store only for scripts in db
            super.setStatus(status, errorMessage);

            scriptEntity.setError(errorMessage);
            entityContext.save(scriptEntity);
            super.setStatus(status, errorMessage);
        }
    }

    public String getErrorMessage() {
        return scriptEntity.getError();
    }

    public String whyCannotWork() {
        return null;
    }

    /**
     * Logger part
     */
    @Override
    public Logger createLogger() {
        return loggerManager.getLogger(scriptEntity.getBackgroundProcessServiceID(), scriptEntity);
    }

    protected CompileScriptContext getCompiledScript() {
        if (compiled == null) {
            compiled = scriptManager.createCompiledScript(scriptEntity, printStream, params);
        }
        return compiled;
    }
}
