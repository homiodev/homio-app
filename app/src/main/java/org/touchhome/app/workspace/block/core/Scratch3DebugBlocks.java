package org.touchhome.app.workspace.block.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.touchhome.app.manager.scripting.ScriptManager;
import org.touchhome.app.model.entity.ScriptEntity;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.notification.NotificationType;
import org.touchhome.bundle.api.scratch.BlockType;
import org.touchhome.bundle.api.scratch.Scratch3Block;
import org.touchhome.bundle.api.scratch.Scratch3ExtensionBlocks;
import org.touchhome.bundle.api.scratch.WorkspaceBlock;

@Log4j2
@Getter
@Component
public class Scratch3DebugBlocks extends Scratch3ExtensionBlocks {

    private final ObjectMapper objectMapper;
    private final ScriptManager scriptManager;

    private final Scratch3Block logToConsoleBlock;
    private final Scratch3Block printBlock;
    private final Scratch3Block runScriptValueBlock;
    private final Scratch3Block runScriptBlock;

    public Scratch3DebugBlocks(ObjectMapper objectMapper, EntityContext entityContext, ScriptManager scriptManager) {
        super("debug", null, entityContext);

        this.objectMapper = objectMapper;
        this.scriptManager = scriptManager;

        // Blocks
        this.printBlock = Scratch3Block.ofHandler("print", BlockType.command, this::printHandler);
        this.logToConsoleBlock = Scratch3Block.ofHandler("log_to_console", BlockType.command, this::logHandler);
        this.runScriptBlock = Scratch3Block.ofHandler("run_code", BlockType.command, this::runCodeHandler);
        this.runScriptValueBlock = Scratch3Block.ofEvaluate("run_code_value", BlockType.reporter, this::runCodeValueEvaluate);

        this.postConstruct();
    }

    private void runCodeHandler(WorkspaceBlock workspaceBlock) {
        runCodeValueEvaluate(workspaceBlock);
    }

    @SneakyThrows
    private String runCodeValueEvaluate(WorkspaceBlock workspaceBlock) {
        String scriptEntityId = workspaceBlock.getInputWorkspaceBlock("SCRIPT").getField("SCRIPT_REF");
        ScriptEntity scriptEntity = entityContext.getEntity(scriptEntityId);
        if (scriptEntity == null) {
            entityContext.sendNotification("WORKSPACE.SCRIPT_NOT_FOUND", scriptEntityId, NotificationType.danger);
        } else {
            Object result = scriptManager.executeJavaScriptOnce(scriptEntity, scriptEntity.getJavaScriptParameters(), null, false);
            return result == null ? null : result.toString();
        }
        return "";
    }

    private void logHandler(WorkspaceBlock workspaceBlock) {
        log.info(workspaceBlock.getInputString("VALUE"));
    }

    @SneakyThrows
    private void printHandler(WorkspaceBlock workspaceBlock) {
        String value = workspaceBlock.getInputString("VALUE");
        sendWorkspaceBlockChangeValue(workspaceBlock, value);
    }

    private void sendWorkspaceBlockChangeValue(WorkspaceBlock workspaceBlock, String value) {
        ObjectNode node = objectMapper.createObjectNode().put("block", workspaceBlock.getId()).put("value", value);
        entityContext.sendNotification("-workspace-block-update", node);
    }
}
