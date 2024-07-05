package org.homio.app.manager;

import static org.homio.api.util.JsonUtils.OBJECT_MAPPER;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.PrintStream;
import java.io.StringReader;
import java.time.Duration;
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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.homio.api.Context;
import org.homio.api.ContextBGP;
import org.homio.api.ContextBGP.ScheduleBuilder;
import org.homio.api.exception.ServerException;
import org.homio.api.model.Status;
import org.homio.api.state.State;
import org.homio.api.state.StringType;
import org.homio.api.util.CommonUtils;
import org.homio.app.manager.common.ContextImpl;
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
    private final Context context;

    private ExecutorService createCompiledScriptSingleCallExecutorService = Executors.newSingleThreadExecutor();

    @Override
    public void onContextCreated(ContextImpl context) throws Exception {
        for (ScriptEntity scriptEntity : this.context.db().findAll(ScriptEntity.class)) {
            if (scriptEntity.isAutoStart()) {
                this.context.bgp().builder(scriptEntity.getEntityID())
                        .onError(ex ->
                            this.context.db().updateDelayed(scriptEntity, s ->
                                        s.setStatus(Status.ERROR).setError(CommonUtils.getErrorMessage(ex))))
                        .execute(() -> {
                            CompileScriptContext compiledScriptContext = createCompiledScript(scriptEntity, null, null);
                            runJavaScript(compiledScriptContext);
                        });
            }
        }
    }

    @Operation(description = "Execute java script")
    public @NotNull State executeJavaScriptOnce(
            @Parameter(name = "scriptEntity") ScriptEntity scriptEntity,
            @Parameter(name = "logPrintStream") PrintStream logPrintStream,
            @Parameter(name = "forceBackground") boolean forceBackground,
            @Parameter(name = "context") State context)
            throws Exception {
        return startThread(scriptEntity, false, logPrintStream, forceBackground, context);
    }

    public void stopThread(ScriptEntity scriptEntity) {
        this.context.bgp().cancelThread(scriptEntity.getEntityID());
    }

    /**
     * @param forceBackground - if force - execute javascript in background without check if process has period or not
     * @param state
     */
    public @NotNull State startThread(ScriptEntity scriptEntity, boolean allowRepeat,
        PrintStream logPrintStream, boolean forceBackground, State state) throws Exception {
        scriptEntity.setStatus(Status.RUNNING);
        if (forceBackground) {
            context.db().save(scriptEntity);
        } else if (scriptEntity.getRepeatInterval() != 0 && allowRepeat) {
            if (context.bgp().isThreadExists(scriptEntity.getEntityID(), true)) {
                throw new ServerException("Script already in progress. Stop script to restart");
            }
            // throw if sleep less than 0.1s
            if (scriptEntity.getRepeatInterval() < 100) {
                throw new ServerException("Script has bad 'REPEAT_EVERY' value. Must be >= 100ms");
            }
            context.db().save(scriptEntity);
        } else {
            CompileScriptContext compiledScriptContext = createCompiledScript(scriptEntity, logPrintStream, state);
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

    public CompileScriptContext createCompiledScript(ScriptEntity scriptEntity, PrintStream logPrintStream, State state) {
        ScriptEngine engine = new ScriptEngineManager().getEngineByName("graal.js");
        if (logPrintStream != null) {
            engine.put(JavaScriptBinder.log.name(), loggerService.getLogger(logPrintStream));
        }
        engine.put(JavaScriptBinder.context.name(), context);
        engine.put(JavaScriptBinder.script.name(), scriptEntity);
        JsonNode jsonParams;
        try {
            jsonParams = OBJECT_MAPPER.readValue(scriptEntity.getJavaScriptParameters(), JsonNode.class);
        } catch (Exception ignore) {
            jsonParams = OBJECT_MAPPER.createObjectNode();
        }
        engine.put(JavaScriptBinder.params.name(), jsonParams);
        if (state != null) {
            engine.put(JavaScriptBinder.value.name(), state.rawValue());
        }

        CompiledScript compiled;
        String formattedJavaScript;
        try {
            formattedJavaScript = scriptEntity.getFormattedJavaScript(context, (Compilable) engine);

            compiled = ((Compilable) engine).compile(new StringReader(formattedJavaScript));
            Future<Object> future = createCompiledScriptSingleCallExecutorService.submit((Callable<Object>) compiled::eval);
            try {
                future.get(60, TimeUnit.SECONDS);
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
        ScheduleBuilder<State> builder = context.bgp().builder(scriptEntity.getEntityID());
        ContextBGP.ThreadContext<State> threadContext =
                builder
                        .throwOnError(true)
                        .execute(arg -> runJavaScript(compiledScriptContext));
        try {
            State value = threadContext.await(Duration.ofSeconds(60));
            return value == null ? State.of("") : value;
        } catch (Exception ex) {
            threadContext.cancel();
            throw new ExecutionException("Exception: " + CommonUtils.getErrorMessage(ex), ex);
        }
    }

    private static void appendFunc(List<Object> script, String funcName, String separator, String javaScript) {
        String result = ScriptEntity.getFunctionWithName(javaScript, funcName);
        if (result != null) {
            script.add(separator + "(" + result + ")" + separator);
        }
    }
}
