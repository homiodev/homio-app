package org.homio.app.workspace.block.core;

import com.jayway.jsonpath.JsonPath;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.homio.api.Context;
import org.homio.api.state.JsonType;
import org.homio.api.state.State;
import org.homio.api.state.StringType;
import org.homio.api.workspace.WorkspaceBlock;
import org.homio.api.workspace.WorkspaceEventListener;
import org.homio.api.workspace.scratch.Scratch3ExtensionBlocks;
import org.homio.app.manager.ScriptService;
import org.homio.app.model.CompileScriptContext;
import org.homio.app.model.entity.ScriptEntity;
import org.homio.app.workspace.WorkspaceBlockImpl;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Getter
@Component
public class Scratch3MutatorBlocks extends Scratch3ExtensionBlocks
  implements WorkspaceEventListener {

  private final Map<Integer, CompileScriptContext> compileScriptContextMap = new HashMap<>();
  private final ScriptService scriptService;

  public Scratch3MutatorBlocks(Context context, ScriptService scriptService) {
    super("mutator", context);
    this.scriptService = scriptService;

    blockReporter("join", this::joinStringEvaluate);
    blockReporter("json_reduce", this::jsonReduceEvaluate);
    blockReporter("map", this::mapEvaluate);
  }

  public static State reduceJSON(String json, String query) {
    if (StringUtils.isNotEmpty(query)) {
      Object filteredObject = JsonPath.read(json, query);
      return State.of(filteredObject);
    }
    return new JsonType(json);
  }

  @Override
  public void release(String id) {
    this.compileScriptContextMap.clear();
  }

  @SneakyThrows
  private State mapEvaluate(WorkspaceBlock workspaceBlock) {
    String source = workspaceBlock.getInputString("SOURCE");
    String map = workspaceBlock.getInputString("MAP");
    State lastValue = ((WorkspaceBlockImpl) workspaceBlock).getLastValue();

    CompileScriptContext compileScriptContext = this.compileScriptContextMap.computeIfAbsent(map.hashCode(),
      integer -> {
        String code = map;
        if (ScriptEntity.getFunctionWithName(code, "run") == null) {
          code = "function run() { " + code + " }";
        }
        ScriptEntity scriptEntity = new ScriptEntity().setJavaScript(code);
        return scriptService.createCompiledScript(scriptEntity, null, lastValue);
      });
    compileScriptContext.getEngine().put("input", source);
    return scriptService.runJavaScript(compileScriptContext);
  }

  private State jsonReduceEvaluate(WorkspaceBlock workspaceBlock) {
    String json = workspaceBlock.getInputString("JSON");
    String query = workspaceBlock.getInputString("REDUCE");
    return reduceJSON(json, query);
  }

  private State joinStringEvaluate(WorkspaceBlock workspaceBlock) {
    return new StringType(
      workspaceBlock.getInputString("STRING1")
      + workspaceBlock.getInputString("STRING2"));
  }
}
