package org.homio.app.manager;

import static org.homio.api.util.CommonUtils.OBJECT_MAPPER;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import java.io.PrintStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.homio.api.EntityContext;
import org.homio.api.EntityContextBGP;
import org.homio.api.EntityContextBGP.ScheduleBuilder;
import org.homio.api.exception.ServerException;
import org.homio.api.model.Status;
import org.homio.api.state.State;
import org.homio.api.state.StringType;
import org.homio.api.util.CommonUtils;
import org.homio.app.config.AppProperties;
import org.homio.app.manager.common.EntityContextImpl;
import org.homio.app.model.CompileScriptContext;
import org.homio.app.model.entity.ScriptEntity;
import org.homio.app.spring.ContextCreated;
import org.homio.app.utils.JavaScriptBinder;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class ScriptService implements ContextCreated {

    static {
        System.setProperty("polyglot.js.nashorn-compat", "true");
        System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
    }

    private final LoggerService loggerService;
    private final EntityContext entityContext;
    private final AppProperties properties;

    private ExecutorService createCompiledScriptSingleCallExecutorService = Executors.newSingleThreadExecutor();

    private static void appendFunc(List<Object> script, String funcName, String separator, String javaScript) {
        String result = ScriptEntity.getFunctionWithName(javaScript, funcName);
        if (result != null) {
            script.add(separator + "(" + result + ")" + separator);
        }
    }

    @Override
    public void onContextCreated(EntityContextImpl entityContext) throws Exception {
        for (ScriptEntity scriptEntity : this.entityContext.findAll(ScriptEntity.class)) {
            if (scriptEntity.isAutoStart()) {
                this.entityContext.bgp().builder(scriptEntity.getEntityID())
                                  .onError(ex ->
                                      this.entityContext.updateDelayed(scriptEntity, s ->
                                          s.setStatus(Status.ERROR).setError(CommonUtils.getErrorMessage(ex))))
                                  .execute(() -> {
                                      CompileScriptContext compiledScriptContext = createCompiledScript(scriptEntity, null, null);
                                      runJavaScript(compiledScriptContext);
                                  });
            }
        }
    }

    @ApiOperation("Execute java script")
    public @NotNull State executeJavaScriptOnce(
        @ApiParam(name = "scriptEntity") ScriptEntity scriptEntity,
        @ApiParam(name = "logPrintStream") PrintStream logPrintStream,
        @ApiParam(name = "forceBackground") boolean forceBackground,
        @ApiParam(name = "context") State context)
        throws Exception {
        return startThread(scriptEntity, false, logPrintStream, forceBackground, context);
    }

    public void stopThread(ScriptEntity scriptEntity) {
        this.entityContext.bgp().cancelThread(scriptEntity.getEntityID());
    }

    /**
     * @param forceBackground - if force - execute javascript in background without check if process has period or not
     * @param context
     */
    public @NotNull State startThread(ScriptEntity scriptEntity, boolean allowRepeat,
        PrintStream logPrintStream, boolean forceBackground, State context) throws Exception {
        scriptEntity.setStatus(Status.RUNNING);
        if (forceBackground) {
            entityContext.save(scriptEntity);
        } else if (scriptEntity.getRepeatInterval() != 0 && allowRepeat) {
            if (entityContext.bgp().isThreadExists(scriptEntity.getEntityID(), true)) {
                throw new ServerException("Script already in progress. Stop script to restart");
            }
            if (scriptEntity.getRepeatInterval() < properties.getMinScriptThreadSleep().toMillis()) {
                throw new ServerException("Script has bad 'REPEAT_EVERY' value. Must be >= " + properties.getMinScriptThreadSleep());
            }
            entityContext.save(scriptEntity);
        } else {
            CompileScriptContext compiledScriptContext = createCompiledScript(scriptEntity, logPrintStream, context);
            return callJavaScriptOnce(scriptEntity, compiledScriptContext);
        }
        return new StringType("");
    }

    public @NotNull State runJavaScript(CompileScriptContext compileScriptContext)
        throws ScriptException, NoSuchMethodException {
        List<Object> script = new ArrayList<>();
        // TODO: UI scripts should be separated type
        // appendFunc(script, "readyOnClient", "READY_BLOCK", compileScriptContext.getFormattedJavaScript());

        Object value = ((Invocable) compileScriptContext.getCompiledScript().getEngine())
            .invokeFunction("run", compileScriptContext.getJsonParams());
        /* TODO: if (value instanceof ScriptObjectMirror) {
            ScriptObjectMirror obj = (ScriptObjectMirror) value;
            Map<String, Object> map = new HashMap<>();
            for (String key : obj.keySet()) {
                map.put(key, obj.get(key));
            }
            script.add(map);
        } else {*/
        script.add(value);
        // }
        if (script.size() == 1) {
            return State.of(script.get(0) == null ? "" : script.get(0));
        }
        return new StringType(String.join("", script.stream().map(Object::toString).collect(Collectors.toList())));
    }

    public CompileScriptContext createCompiledScript(ScriptEntity scriptEntity, PrintStream logPrintStream, State context) {
        ScriptEngine engine = new ScriptEngineManager().getEngineByName("graal.js");
        if (logPrintStream != null) {
            engine.put(JavaScriptBinder.log.name(), loggerService.getLogger(logPrintStream));
        }
        engine.put(JavaScriptBinder.entityContext.name(), entityContext);
        engine.put(JavaScriptBinder.script.name(), scriptEntity);
        JsonNode jsonParams;
        try {
            jsonParams = OBJECT_MAPPER.readValue(scriptEntity.getJavaScriptParameters(), JsonNode.class);
        } catch (Exception ignore) {
            jsonParams = OBJECT_MAPPER.createObjectNode();
        }
        engine.put(JavaScriptBinder.params.name(), jsonParams);
        if (context != null) {
            engine.put(JavaScriptBinder.context.name(), context.rawValue());
        }

        CompiledScript compiled;
        String formattedJavaScript;
        try {
            formattedJavaScript = scriptEntity.getFormattedJavaScript(entityContext, (Compilable) engine);

            compiled = ((Compilable) engine).compile(new StringReader(formattedJavaScript));
            Future<Object> future = createCompiledScriptSingleCallExecutorService.submit((Callable<Object>) compiled::eval);
            try {
                future.get(properties.getMaxJavaScriptCompileBeforeInterrupt().toSeconds(), TimeUnit.SECONDS);
            } catch (TimeoutException ex) {
                future.cancel(true);
                createCompiledScriptSingleCallExecutorService.shutdownNow();
                createCompiledScriptSingleCallExecutorService = Executors.newSingleThreadExecutor();
                throw new ExecutionException("Script evaluation stuck. Got TimeoutException: " + CommonUtils.getErrorMessage(ex), ex);
            }
        } catch (Exception ex) {
            log.error("Can not compile script: <{}>. Msg: <{}>", scriptEntity.getEntityID(), ex.getMessage());
            throw new ServerException(ex);
        }
        return new CompileScriptContext(compiled, formattedJavaScript, jsonParams);
    }

    /**
     * Run java script once and interrupt it if too long works
     */
    public @NotNull State callJavaScriptOnce(ScriptEntity scriptEntity, CompileScriptContext compiledScriptContext) throws ExecutionException {
        ScheduleBuilder<State> builder = this.entityContext.bgp().builder(scriptEntity.getEntityID());
        EntityContextBGP.ThreadContext<State> threadContext =
            builder
                .throwOnError(true)
                .execute(arg -> runJavaScript(compiledScriptContext));
        try {
            State value = threadContext.await(properties.getMaxJavaScriptOnceCallBeforeInterrupt());
            return value == null ? State.of("") : value;
        } catch (Exception ex) {
            threadContext.cancel();
            throw new ExecutionException("Exception: " + CommonUtils.getErrorMessage(ex), ex);
        }
    }
}
