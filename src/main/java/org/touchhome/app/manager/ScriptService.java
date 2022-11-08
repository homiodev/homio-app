package org.touchhome.app.manager;

import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import java.io.PrintStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import javax.script.ScriptException;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import org.touchhome.app.config.TouchHomeProperties;
import org.touchhome.app.extloader.BundleClassLoaderHolder;
import org.touchhome.app.model.CompileScriptContext;
import org.touchhome.app.model.entity.ScriptEntity;
import org.touchhome.app.spring.ContextCreated;
import org.touchhome.app.utils.JavaScriptBinder;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.EntityContextBGP;
import org.touchhome.bundle.api.EntityContextBGP.ScheduleBuilder;
import org.touchhome.bundle.api.model.Status;
import org.touchhome.bundle.api.state.State;
import org.touchhome.bundle.api.state.StringType;
import org.touchhome.common.exception.ServerException;
import org.touchhome.common.util.CommonUtils;

@Log4j2
@Component
@RequiredArgsConstructor
public class ScriptService implements ContextCreated {

  private final NashornScriptEngineFactory nashornScriptEngineFactory = new NashornScriptEngineFactory();
  private final LoggerService loggerService;
  private final EntityContext entityContext;
  private final BundleClassLoaderHolder bundleClassLoaderHolder;
  private final TouchHomeProperties properties;

  private ExecutorService createCompiledScriptSingleCallExecutorService = Executors.newSingleThreadExecutor();

  private static void appendFunc(List<Object> script, String funcName, String separator, String javaScript) {
    String result = ScriptEntity.getFunctionWithName(javaScript, funcName);
    if (result != null) {
      script.add(separator + "(" + result + ")" + separator);
    }
  }

  @Override
  public void onContextCreated(EntityContext entityContext) throws Exception {
    for (ScriptEntity scriptEntity : this.entityContext.findAll(ScriptEntity.class)) {
      if (scriptEntity.isAutoStart()) {
        EntityContextBGP.ThreadContext<Void> threadContext =
            this.entityContext.bgp().builder(scriptEntity.getEntityID()).execute(() -> {
              CompileScriptContext compiledScriptContext = createCompiledScript(scriptEntity, null);
              runJavaScript(compiledScriptContext);
            });
        threadContext.onError(ex -> this.entityContext.updateDelayed(scriptEntity,
            s -> s.setStatus(Status.ERROR).setError(CommonUtils.getErrorMessage(ex))));
      }
    }
  }

  @ApiOperation("Execute java script")
  public Object executeJavaScriptOnce(@ApiParam(name = "scriptEntity") ScriptEntity scriptEntity,
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
  public String startThread(ScriptEntity scriptEntity, String json, boolean allowRepeat, PrintStream logPrintStream,
      boolean forceBackground) throws Exception {
    scriptEntity.setStatus(Status.RUNNING);
    if (forceBackground) {
      scriptEntity.setJavaScriptParameters(json);
      entityContext.save(scriptEntity);
    } else if (scriptEntity.getRepeatInterval() != 0 && allowRepeat) {
      if (entityContext.bgp().isThreadExists(scriptEntity.getEntityID(), true)) {
        throw new ServerException("Script already in progress. Stop script to restart");
      }
      if (scriptEntity.getRepeatInterval() < properties.getMinScriptThreadSleep().toMillis()) {
        throw new ServerException(
            "Script has bad 'REPEAT_EVERY' value. Must be >= " + properties.getMinScriptThreadSleep());
      }
      scriptEntity.setJavaScriptParameters(json);
      entityContext.save(scriptEntity);
    } else {
      CompileScriptContext compiledScriptContext = createCompiledScript(scriptEntity, logPrintStream);
      return callJavaScriptOnce(scriptEntity, compiledScriptContext);
    }
    return null;
  }

  public State runJavaScript(CompileScriptContext compileScriptContext) throws ScriptException, NoSuchMethodException {
    List<Object> script = new ArrayList<>();
    appendFunc(script, "readyOnClient", "READY_BLOCK", compileScriptContext.getFormattedJavaScript());

    Object value = ((Invocable) compileScriptContext.getCompiledScript().getEngine()).invokeFunction("run",
        compileScriptContext.getJsonParams());
    if (value instanceof ScriptObjectMirror) {
      ScriptObjectMirror obj = (ScriptObjectMirror) value;
      Map<String, Object> map = new HashMap<>();
      for (String key : obj.keySet()) {
        map.put(key, obj.get(key));
      }
      script.add(map);
    } else {
      script.add(value);
    }
    if (script.size() == 1) {
      return State.of(script.get(0));
    }
    return new StringType(String.join("", script.stream().map(Object::toString).collect(Collectors.toList())));
  }

  public CompileScriptContext createCompiledScript(ScriptEntity scriptEntity, PrintStream logPrintStream) {
    ScriptEngine engine =
        nashornScriptEngineFactory.getScriptEngine(new String[]{"--global-per-engine"}, bundleClassLoaderHolder);
    if (logPrintStream != null) {
      engine.put(JavaScriptBinder.log.name(), loggerService.getLogger(logPrintStream));
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
        future.get(properties.getMaxJavaScriptCompileBeforeInterrupt().toSeconds(), TimeUnit.SECONDS);
      } catch (TimeoutException ex) {
        future.cancel(true);
        createCompiledScriptSingleCallExecutorService.shutdownNow();
        createCompiledScriptSingleCallExecutorService = Executors.newSingleThreadExecutor();
        throw new ExecutionException("Script evaluation stuck. Got TimeoutException: " + CommonUtils.getErrorMessage(ex),
            ex);
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
  public String callJavaScriptOnce(ScriptEntity scriptEntity, CompileScriptContext compiledScriptContext)
      throws InterruptedException, ExecutionException {
    ScheduleBuilder<String> builder = this.entityContext.bgp().builder(scriptEntity.getEntityID());
    EntityContextBGP.ThreadContext<String> threadContext = builder.execute(arg -> runJavaScript(compiledScriptContext).toFullString());
    try {
      return threadContext.await(properties.getMaxJavaScriptOnceCallBeforeInterrupt());
    } catch (TimeoutException ex) {
      threadContext.cancel();
      throw new ExecutionException("Script stuck. Got TimeoutException: " + CommonUtils.getErrorMessage(ex), ex);
    }
  }
}
