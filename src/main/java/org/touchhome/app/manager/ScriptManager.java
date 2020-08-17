package org.touchhome.app.manager;

import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.touchhome.app.extloader.BundleClassLoaderHolder;
import org.touchhome.app.model.CompileScriptContext;
import org.touchhome.app.model.entity.ScriptEntity;
import org.touchhome.app.thread.js.AbstractJSBackgroundProcessService;
import org.touchhome.app.utils.JavaScriptBinder;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.manager.LoggerManager;
import org.touchhome.bundle.api.thread.BackgroundProcessStatus;
import org.touchhome.bundle.api.util.TouchHomeUtils;

import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import java.io.PrintStream;
import java.io.StringReader;
import java.util.concurrent.*;

@Log4j2
@Component
@RequiredArgsConstructor
public class ScriptManager {
    // TODO: deprecated replace this
    public static final String REPEAT_EVERY = "REPEAT_EVERY";

    private final NashornScriptEngineFactory nashornScriptEngineFactory = new NashornScriptEngineFactory();
    private final LoggerManager loggerManager;
    private final BackgroundProcessManager backgroundProcessManager;
    private final EntityContext entityContext;
    private final BundleClassLoaderHolder bundleClassLoaderHolder;

    private ExecutorService singleCallExecutorService = Executors.newSingleThreadExecutor();

    @Value("${minScriptThreadSleep:100}")
    private int minScriptThreadSleep;

    @Value("${maxJavaScriptOnceCallBeforeInterrupt:60}")
    private Integer maxJavaScriptOnceCallBeforeInterruptInSec;

    @Value("${maxJavaScriptCompileBeforeInterruptInSec:5}")
    private Integer maxJavaScriptCompileBeforeInterruptInSec;

    public void postConstruct() {
        for (ScriptEntity scriptEntity : entityContext.findAll(ScriptEntity.class)) {
            try {
                backgroundProcessManager.fireIfNeedRestart(scriptEntity.createBackgroundProcessService(entityContext));
            } catch (Exception ex) {
                scriptEntity.setScriptStatus(BackgroundProcessStatus.FAILED);
                scriptEntity.setError(TouchHomeUtils.getErrorMessage(ex));
                entityContext.save(scriptEntity);
                log.error("Error while start script after crash: " + scriptEntity.getEntityID(), ex);
            }
        }
    }

    @ApiOperation("Execute java script")
    public Object executeJavaScriptOnce(
            @ApiParam(name = "scriptEntity") ScriptEntity scriptEntity,
            @ApiParam(name = "jsonParameters") String jsonParameters,
            @ApiParam(name = "logPrintStream") PrintStream logPrintStream,
            @ApiParam(name = "forceBackground") boolean forceBackground) throws Exception {
        return startThread(scriptEntity, jsonParameters, false, logPrintStream, forceBackground);
    }

    public BackgroundProcessStatus stopThread(ScriptEntity scriptEntity) {
        stopThreadInternal(scriptEntity);
        return BackgroundProcessStatus.STOP;
    }

    private void stopThreadInternal(ScriptEntity scriptEntity) {
        String backgroundProcessServiceID = scriptEntity.getBackgroundProcessServiceID();

        log.info("Stop script: " + scriptEntity.getEntityID());
        if (!BackgroundProcessStatus.RUNNING.equals(scriptEntity.getBackgroundProcessStatus())) {
            log.warn("Trying stop script: " + scriptEntity.getEntityID() + " with wrong status: " + scriptEntity.getBackgroundProcessStatus().name());
            if (backgroundProcessManager.isRunning(backgroundProcessServiceID)) {
                log.warn("Trying stop script: " + scriptEntity.getEntityID() + " with wrong status: " + scriptEntity.getBackgroundProcessStatus().name() + ", but it exists in pull of running processes!");
            }
        } else {
            if (!backgroundProcessManager.isRunning(backgroundProcessServiceID)) {
                log.warn("Trying stop script: " + scriptEntity.getEntityID() + " with status: " + scriptEntity.getBackgroundProcessStatus().name() + ", but it NOT exists in pull of running processes!");
            }
        }
        backgroundProcessManager.cancelTask(backgroundProcessServiceID, BackgroundProcessStatus.STOP, null);
    }

    public void invokeAfterFunction(CompileScriptContext compiled, Object parameters) {
        invokeFunction(compiled, "after", parameters, true);
    }

    public void invokeBeforeFunction(CompileScriptContext compiled, Object parameters) {
        invokeFunction(compiled, "before", parameters, true);
    }

    public Object invokeFunction(CompileScriptContext context, String methodName, Object parameters, boolean required) {
        if (context.getEngine().get(methodName) != null) {
            try {
                return ((Invocable) context.getEngine()).invokeFunction(methodName, parameters);
            } catch (Exception ex) {
                if (required) {
                    Logger threadLog = (Logger) context.getEngine().get("log");
                    if (threadLog != null) {
                        threadLog.error("Error invokeMethod " + methodName, ex);
                    }
                    log.warn("Error while call script method: " + methodName, ex);
                }
            }
        }
        return null;
    }

    /**
     * @param forceBackground - if force - execute javascript in background without check if process has period or not
     */
    public Object startThread(ScriptEntity scriptEntity, String json, boolean allowRepeat, PrintStream logPrintStream, boolean forceBackground) throws Exception {
        AbstractJSBackgroundProcessService abstractJSBackgroundProcessService = scriptEntity.createBackgroundProcessService(entityContext);
        abstractJSBackgroundProcessService.setPrintStream(logPrintStream);
        long period = abstractJSBackgroundProcessService.getPeriod();

        if (forceBackground) {
            scriptEntity.setScriptStatus(BackgroundProcessStatus.RUNNING);
            scriptEntity.setJavaScriptParameters(json);
            entityContext.save(scriptEntity);
        } else if (period != 0 && allowRepeat) {
            if (backgroundProcessManager.isRunning(abstractJSBackgroundProcessService)) {
                throw new RuntimeException("Script already in progress. Stop script to restart");
            }
            if (period < minScriptThreadSleep) {
                throw new RuntimeException("Script has bad 'REPEAT_EVERY' value. Must be >= " + minScriptThreadSleep);
            }
            scriptEntity.setScriptStatus(BackgroundProcessStatus.RUNNING);
            scriptEntity.setJavaScriptParameters(json);
            entityContext.save(scriptEntity);
        } else {
            return callJavaScriptOnce(abstractJSBackgroundProcessService);
        }
        return BackgroundProcessStatus.RUNNING;
    }

    public CompileScriptContext createCompiledScript(ScriptEntity scriptEntity, PrintStream logPrintStream, JSONObject params) {
        ScriptEngine engine = nashornScriptEngineFactory.getScriptEngine(new String[]{"--global-per-engine"}, bundleClassLoaderHolder);
        if (logPrintStream != null) {
            engine.put(JavaScriptBinder.log.name(), loggerManager.getLogger(logPrintStream));
        }
        engine.put(JavaScriptBinder.entityContext.name(), entityContext);
        engine.put(JavaScriptBinder.script.name(), scriptEntity);
        engine.put(JavaScriptBinder.params.name(), params == null ? new JSONObject(scriptEntity.getJavaScriptParameters()) : params);
        CompiledScript compiled;
        String formattedJavaScript;
        try {
            formattedJavaScript = scriptEntity.getFormattedJavaScript(entityContext, (Compilable) engine);

            compiled = ((Compilable) engine).compile(new StringReader(formattedJavaScript));
            Future<Object> future = singleCallExecutorService.submit((Callable<Object>) compiled::eval);
            try {
                future.get(maxJavaScriptCompileBeforeInterruptInSec, TimeUnit.SECONDS);
            } catch (TimeoutException ex) {
                future.cancel(true);
                singleCallExecutorService.shutdownNow();
                singleCallExecutorService = Executors.newSingleThreadExecutor();
                throw new ExecutionException("Script evaluation stuck. Got TimeoutException: " + TouchHomeUtils.getErrorMessage(ex), ex);
            }
        } catch (Exception ex) {
            log.error("Can not compile script: " + scriptEntity.getEntityID());
            throw new RuntimeException(ex);
        }
        return new CompileScriptContext(compiled, formattedJavaScript);
    }

    /**
     * Run java script once and interrupt it if too long works
     */
    public Object callJavaScriptOnce(AbstractJSBackgroundProcessService abstractJSBackgroundProcessService) throws InterruptedException, ExecutionException {
        Future<Object> future = singleCallExecutorService.submit(() -> {
            abstractJSBackgroundProcessService.beforeStart();
            Object retValue = abstractJSBackgroundProcessService.run();
            abstractJSBackgroundProcessService.afterStop();
            return retValue;
        });
        try {
            return future.get(maxJavaScriptOnceCallBeforeInterruptInSec, TimeUnit.SECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            singleCallExecutorService.shutdownNow();
            singleCallExecutorService = Executors.newSingleThreadExecutor();
            throw new ExecutionException("Script stuck. Got TimeoutException: " + TouchHomeUtils.getErrorMessage(ex), ex);
        }
    }
}
