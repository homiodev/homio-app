package org.touchhome.app.workspace.block.core;

import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import org.touchhome.app.manager.ScriptService;
import org.touchhome.app.model.entity.ScriptEntity;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.state.RawType;
import org.touchhome.bundle.api.state.State;
import org.touchhome.bundle.api.state.StringType;
import org.touchhome.bundle.api.workspace.WorkspaceBlock;
import org.touchhome.bundle.api.workspace.scratch.Scratch3ExtensionBlocks;

@Log4j2
@Getter
@Component
public class Scratch3MiscBlocks extends Scratch3ExtensionBlocks {

  private final ScriptService scriptService;

  public Scratch3MiscBlocks(EntityContext entityContext, ScriptService scriptService) {
    super("misc", entityContext);
    this.scriptService = scriptService;

    blockCommand("print", this::printHandler);
    blockCommand("log_to_console", this::logHandler);
    blockCommand("run_code", this::runCodeHandler);
    blockReporter("run_code_value", this::runCodeValueEvaluate);
  }

  private void runCodeHandler(WorkspaceBlock workspaceBlock) {
    runCodeValueEvaluate(workspaceBlock);
  }

  @SneakyThrows
  private State runCodeValueEvaluate(WorkspaceBlock workspaceBlock) {
    String scriptEntityId = workspaceBlock.getInputWorkspaceBlock("SCRIPT").getField("SCRIPT_REF");
    ScriptEntity scriptEntity = entityContext.getEntity(scriptEntityId);
    if (scriptEntity == null) {
      entityContext.ui().sendErrorMessage("error.script_not_found", scriptEntityId);
    } else {
      Object result =
          scriptService.executeJavaScriptOnce(scriptEntity, scriptEntity.getJavaScriptParameters(), null, false);
      return State.of(result);
    }
    return StringType.EMPTY;
  }

  private void logHandler(WorkspaceBlock workspaceBlock) {
    log.info(workspaceBlock.getInputStringRequiredWithContext(VALUE));
  }

  @SneakyThrows
  private void printHandler(WorkspaceBlock workspaceBlock) {
    RawType rawType = workspaceBlock.getInputRawType(VALUE);
    if (rawType == null) {
      rawType = RawType.ofPlainText("NULL");
    } else if ("text/plain".equals(rawType.getMimeType())) {
      rawType = new RawType(workspaceBlock.getInputStringRequiredWithContext(VALUE).getBytes());
    }
    JSONObject node = new JSONObject().put("block", workspaceBlock.getId()).put("value", rawType.toFullString())
        .put("mimeType", rawType.getMimeType()).put("name", rawType.getName());
    entityContext.ui().sendNotification("-workspace-block-update", node);
  }
}
