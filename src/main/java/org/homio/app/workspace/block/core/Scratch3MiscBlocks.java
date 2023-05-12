package org.homio.app.workspace.block.core;

import static org.homio.bundle.api.util.CommonUtils.OBJECT_MAPPER;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.homio.app.manager.ScriptService;
import org.homio.app.model.entity.ScriptEntity;
import org.homio.app.workspace.WorkspaceBlockImpl;
import org.homio.bundle.api.EntityContext;
import org.homio.bundle.api.state.RawType;
import org.homio.bundle.api.state.State;
import org.homio.bundle.api.state.StringType;
import org.homio.bundle.api.workspace.WorkspaceBlock;
import org.homio.bundle.api.workspace.scratch.Scratch3ExtensionBlocks;
import org.springframework.stereotype.Component;

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
        String scriptEntityId =
                workspaceBlock.getInputWorkspaceBlock("SCRIPT").getField("SCRIPT_REF");
        ScriptEntity scriptEntity = entityContext.getEntity(scriptEntityId);
        if (scriptEntity == null) {
            entityContext.ui().sendErrorMessage("W.ERROR.SCRIPT_NOT_FOUND", scriptEntityId);
        } else {
            State lastValue = ((WorkspaceBlockImpl) workspaceBlock).getLastValue();
            Object result = scriptService.executeJavaScriptOnce(
                scriptEntity, null, false, lastValue);
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
            rawType =
                    new RawType(workspaceBlock.getInputStringRequiredWithContext(VALUE).getBytes());
        }
        ObjectNode node =
                OBJECT_MAPPER
                        .createObjectNode()
                        .put("block", workspaceBlock.getId())
                        .put("value", rawType.stringValue())
                        .put("mimeType", rawType.getMimeType())
                        .put("name", rawType.getName());
        entityContext.ui().sendNotification("-workspace-block-update", node);
    }
}
