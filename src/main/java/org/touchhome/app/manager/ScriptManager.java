package org.touchhome.app.manager;

import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.touchhome.app.extloader.BundleClassLoaderHolder;
import org.touchhome.app.model.CompileScriptContext;
import org.touchhome.app.model.entity.ScriptEntity;
import org.touchhome.app.utils.JavaScriptBinder;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.EntityContextBGP;
import org.touchhome.bundle.api.model.Status;
import org.touchhome.bundle.api.util.TouchHomeUtils;

import javax.script.*;
import java.io.PrintStream;
import java.io.StringReader;
import java.util.concurrent.*;

@Log4j2
@Component
@RequiredArgsConstructor
public class ScriptManager {

    private final NashornScriptEngineFactory nashornScriptEngineFactory = new NashornScriptEngineFactory();
    private final LoggerManager loggerManager;
    private final EntityContext entityContext;
    private final BundleClassLoaderHolder bundleClassLoaderHolder;

    private ExecutorService createCompiledScriptSingleCallExecutorService = Executors.newSingleThreadExecutor();

    @Value("${minScriptThreadSleep:100}")
    private int minScriptThreadSleep;

    @Value("${maxJavaScriptOnceCallBeforeInterrupt:60}")
    private Integer maxJavaScriptOnceCallBeforeInterruptInSec;

    @Value("${maxJavaScriptCompileBeforeInterruptInSec:5}")
    private Integer maxJavaScriptCompileBeforeInterruptInSec;

    private static void appendFunc(StringBuilder script, String funcName, String separator, String javaScript) {
        String result = ScriptEntity.getFunctionWithName(javaScript, funcName);
        if (result != null) {
            script.append(separator).append("(").append(result).append(")").append(separator);
        }
    }

    public void postConstruct() {
        for (ScriptEntity scriptEntity : entityContext.findAll(ScriptEntity.class)) {
            if (scriptEntity.isAutoStart()) {
                EntityContextBGP.ThreadContext<Void> threadContext = this.entityContext.bgp().run(scriptEntity.getEntityID(), () -> {
                    CompileScriptContext compiledScriptContext = createCompiledScript(scriptEntity, null);
                    runJavaScript(compiledScriptContext);
                }, true);
                threadContext.onError(ex ->
                        entityContext.updateDelayed(scriptEntity, s -> s.setStatus(Status.ERROR).setError(TouchHomeUtils.getErrorMessage(ex))));
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

    public void stopThread(ScriptEntity scriptEntity) {
        this.entityContext.bgp().cancelThread(scriptEntity.getEntityID());
    }

    /**
     * @param forceBackground - if force - execute javascript in background without check if process has period or not
     */
    public String startThread(ScriptEntity scriptEntity, String json, boolean allowRepeat, PrintStream logPrintStream, boolean forceBackground) throws Exception {
        scriptEntity.setStatus(Status.RUNNING);
        if (forceBackground) {
            scriptEntity.setJavaScriptParameters(json);
            entityContext.save(scriptEntity);
        } else if (scriptEntity.getRepeatInterval() != 0 && allowRepeat) {
            if (entityContext.bgp().isThreadExists(scriptEntity.getEntityID(), true)) {
                throw new RuntimeException("Script already in progress. Stop script to restart");
            }
            if (scriptEntity.getRepeatInterval() < minScriptThreadSleep) {
                throw new RuntimeException("Script has bad 'REPEAT_EVERY' value. Must be >= " + minScriptThreadSleep);
            }
            scriptEntity.setJavaScriptParameters(json);
            entityContext.save(scriptEntity);
        } else {
            CompileScriptContext compiledScriptContext = createCompiledScript(scriptEntity, logPrintStream);
            return callJavaScriptOnce(scriptEntity, compiledScriptContext);
        }
        return null;
    }

    public String runJavaScript(CompileScriptContext compileScriptContext) throws ScriptException, NoSuchMethodException {
        StringBuilder script = new StringBuilder();
        appendFunc(script, "readyOnClient", "READY_BLOCK", compileScriptContext.getFormattedJavaScript());

        Object value = ((Invocable) compileScriptContext.getCompiledScript().getEngine()).invokeFunction("run", compileScriptContext.getJsonParams());
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

    public CompileScriptContext createCompiledScript(ScriptEntity scriptEntity, PrintStream logPrintStream) {
        ScriptEngine engine = nashornScriptEngineFactory.getScriptEngine(new String[]{"--global-per-engine"}, bundleClassLoaderHolder);
        if (logPrintStream != null) {
            engine.put(JavaScriptBinder.log.name(), loggerManager.getLogger(logPrintStream));
        }
        engine.put(JavaScriptBinder.entityContext.name(), entityContext);
        engine.put(JavaScriptBinder.script.name(), scriptEntity);

        JSONObject jsonParams = new JSONObject(StringUtils.defaultIfEmpty(scriptEntity.getJavaScriptParameters(), "{}"));
        engine.put(JavaScriptBinder.params.name(), jsonParams);

        CompiledScript compiled;
        String formattedJavaScript;
        try {
            formattedJavaScript = scriptEntity.getFormattedJavaScript(entityContext, (Compilable) engine);

            compiled = ((Compilable) engine).compile(new StringReader(formattedJavaScript));
            Future<Object> future = createCompiledScriptSingleCallExecutorService.submit((Callable<Object>) compiled::eval);
            try {
                future.get(maxJavaScriptCompileBeforeInterruptInSec, TimeUnit.SECONDS);
            } catch (TimeoutException ex) {
                future.cancel(true);
                createCompiledScriptSingleCallExecutorService.shutdownNow();
                createCompiledScriptSingleCallExecutorService = Executors.newSingleThreadExecutor();
                throw new ExecutionException("Script evaluation stuck. Got TimeoutException: " + TouchHomeUtils.getErrorMessage(ex), ex);
            }
        } catch (Exception ex) {
            log.error("Can not compile script: <{}>. Msg: <{}>", scriptEntity.getEntityID(), ex.getMessage());
            throw new RuntimeException(ex);
        }
        return new CompileScriptContext(compiled, formattedJavaScript, jsonParams);
    }

    /**
     * Run java script once and interrupt it if too long works
     */
    public String callJavaScriptOnce(ScriptEntity scriptEntity, CompileScriptContext compiledScriptContext) throws InterruptedException, ExecutionException {
        EntityContextBGP.ThreadContext<String> threadContext = this.entityContext.bgp().run(scriptEntity.getEntityID(), () -> runJavaScript(compiledScriptContext), true);
        try {
            return threadContext.await(maxJavaScriptOnceCallBeforeInterruptInSec, TimeUnit.SECONDS);
        } catch (TimeoutException ex) {
            threadContext.cancel();
            throw new ExecutionException("Script stuck. Got TimeoutException: " + TouchHomeUtils.getErrorMessage(ex), ex);
        }
    }
}
