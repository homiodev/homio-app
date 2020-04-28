package org.touchhome.app.thread.js;

import org.apache.logging.log4j.Logger;
import org.touchhome.app.manager.scripting.ScriptManager;
import org.touchhome.app.model.entity.ScriptEntity;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.manager.LoggerManager;
import org.touchhome.bundle.api.thread.BackgroundProcessService;
import org.touchhome.bundle.api.thread.BackgroundProcessStatus;
import org.touchhome.bundle.api.util.ApplicationContextHolder;

import javax.script.CompiledScript;

import static org.touchhome.bundle.api.thread.BackgroundProcessStatus.*;

public abstract class AbstractJSBackgroundProcessService<ReturnType> extends BackgroundProcessService<ReturnType> {

    protected final ScriptEntity scriptEntity;
    protected final EntityContext entityContext;
    private final LoggerManager loggerManager;
    private final ScriptManager scriptManager;

    private CompiledScript compiled;

    public AbstractJSBackgroundProcessService(ScriptEntity scriptEntity) {
        super(scriptEntity.getBackgroundProcessServiceID());

        this.scriptEntity = scriptEntity;
        this.entityContext = ApplicationContextHolder.getBean(EntityContext.class);
        this.loggerManager = ApplicationContextHolder.getBean(LoggerManager.class);
        this.scriptManager = ApplicationContextHolder.getBean(ScriptManager.class);
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

    protected CompiledScript getCompiledScript() {
        if (compiled == null) {
            compiled = scriptManager.createCompiledScript(scriptEntity, printStream);
        }
        return compiled;
    }
}
