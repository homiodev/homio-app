package org.touchhome.app.workspace.block.core;

import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import org.touchhome.app.manager.ScriptService;
import org.touchhome.app.model.entity.ScriptEntity;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.workspace.WorkspaceBlock;
import org.touchhome.bundle.api.workspace.scratch.BlockType;
import org.touchhome.bundle.api.workspace.scratch.Scratch3Block;
import org.touchhome.bundle.api.workspace.scratch.Scratch3ExtensionBlocks;

@Log4j2
@Getter
@Component
public class Scratch3MiscBlocks extends Scratch3ExtensionBlocks {

    private final ScriptService scriptService;

    private final Scratch3Block logToConsoleBlock;
    private final Scratch3Block printBlock;
    private final Scratch3Block runScriptValueBlock;
    private final Scratch3Block runScriptBlock;

    public Scratch3MiscBlocks(EntityContext entityContext, ScriptService scriptService) {
        super("misc", entityContext);
        this.scriptService = scriptService;

        // Blocks
        this.printBlock = Scratch3Block.ofHandler("print", BlockType.command, this::printHandler);
        this.logToConsoleBlock = Scratch3Block.ofHandler("log_to_console", BlockType.command, this::logHandler);
        this.runScriptBlock = Scratch3Block.ofHandler("run_code", BlockType.command, this::runCodeHandler);
        this.runScriptValueBlock = Scratch3Block.ofEvaluate("run_code_value", BlockType.reporter, this::runCodeValueEvaluate);
    }

    private void runCodeHandler(WorkspaceBlock workspaceBlock) {
        runCodeValueEvaluate(workspaceBlock);
    }

    @SneakyThrows
    private String runCodeValueEvaluate(WorkspaceBlock workspaceBlock) {
        String scriptEntityId = workspaceBlock.getInputWorkspaceBlock("SCRIPT").getField("SCRIPT_REF");
        ScriptEntity scriptEntity = entityContext.getEntity(scriptEntityId);
        if (scriptEntity == null) {
            entityContext.ui().sendErrorMessage("WORKSPACE.SCRIPT_NOT_FOUND", scriptEntityId);
        } else {
            Object result = scriptService.executeJavaScriptOnce(scriptEntity, scriptEntity.getJavaScriptParameters(), null, false);
            return result == null ? null : result.toString();
        }
        return "";
    }

    private void logHandler(WorkspaceBlock workspaceBlock) {
        log.info(workspaceBlock.getInputString("VALUE"));
    }

    @SneakyThrows
    private void printHandler(WorkspaceBlock workspaceBlock) {
        String str = workspaceBlock.getInputString("VALUE", "NULL");
        JSONObject node = new JSONObject().put("block", workspaceBlock.getId()).put("value", str);
        entityContext.ui().sendNotification("-workspace-block-update", node);
    }
}
