package org.touchhome.app.workspace.block.core;

import lombok.Getter;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import org.touchhome.app.workspace.BroadcastLockManagerImpl;
import org.touchhome.app.workspace.WorkspaceBlockImpl;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.entity.workspace.WorkspaceJsonVariableEntity;
import org.touchhome.bundle.api.entity.workspace.WorkspaceStandaloneVariableEntity;
import org.touchhome.bundle.api.entity.workspace.backup.WorkspaceBackupEntity;
import org.touchhome.bundle.api.entity.workspace.backup.WorkspaceBackupValueCrudEntity;
import org.touchhome.bundle.api.entity.workspace.bool.WorkspaceBooleanEntity;
import org.touchhome.bundle.api.entity.workspace.var.WorkspaceVariableBackupValueCrudEntity;
import org.touchhome.bundle.api.entity.workspace.var.WorkspaceVariableEntity;
import org.touchhome.bundle.api.repository.WorkspaceBackupRepository;
import org.touchhome.bundle.api.state.DecimalType;
import org.touchhome.bundle.api.state.OnOffType;
import org.touchhome.bundle.api.util.TouchHomeUtils;
import org.touchhome.bundle.api.workspace.BroadcastLock;
import org.touchhome.bundle.api.workspace.WorkspaceBlock;
import org.touchhome.bundle.api.workspace.scratch.BlockType;
import org.touchhome.bundle.api.workspace.scratch.Scratch3Block;
import org.touchhome.bundle.api.workspace.scratch.Scratch3ExtensionBlocks;
import org.touchhome.common.util.CommonUtils;

import java.util.Date;
import java.util.function.Function;

@Getter
@Component
public class Scratch3DataBlocks extends Scratch3ExtensionBlocks {

    // backup
    private final Scratch3Block lastBackupBlock;
    private final Scratch3Block backupBlock;

    // boolean
    private final Scratch3Block isBoolVariableTrueBlock;
    private final Scratch3Block setBooleanBlock;
    private final Scratch3Block inverseBooleanBlock;
    private final Scratch3Block booleanEventChangesBlock;
    private final Scratch3Block booleanLink;

    // variable group
    private final Scratch3Block setGroupVariableBlock;
    private final Scratch3Block changeGroupVariableBlock;
    private final Scratch3Block groupVariableBlock;
    private final Scratch3Block setAndBackupGroupVariableBlock;
    private final Scratch3Block variableGroupLink;

    // single variable
    private final Scratch3Block setVariableToBlock;
    private final Scratch3Block changeVariableByBlock;
    private final Scratch3Block onChangeVariableBlock;
    private final Scratch3Block onChangeVariableToBlock;

    // json
    private final Scratch3Block setJsonVariableToBlock;
    private final Scratch3Block getJsonVariableToBlock;

    private final WorkspaceBackupRepository workspaceBackupRepository;
    private final BroadcastLockManagerImpl broadcastLockManager;

    private final Scratch3Block getPrevVariableBlock;

    public Scratch3DataBlocks(EntityContext entityContext, WorkspaceBackupRepository workspaceBackupRepository, BroadcastLockManagerImpl broadcastLockManager) {
        super("data", entityContext);
        this.workspaceBackupRepository = workspaceBackupRepository;
        this.broadcastLockManager = broadcastLockManager;

        // Blocks
        this.getPrevVariableBlock = Scratch3Block.ofReporter("prev_variable", this::getPreviousValue);

        this.isBoolVariableTrueBlock = Scratch3Block.ofReporter("bool_variables", this::isBoolVariableTrueReporter);
        this.setBooleanBlock = Scratch3Block.ofHandler("set_boolean", BlockType.command, this::setBooleanHandler);
        this.inverseBooleanBlock = Scratch3Block.ofHandler("inverse_boolean", BlockType.command, this::inverseBooleanHandler);
        this.booleanEventChangesBlock = Scratch3Block.ofHandler("boolean_event_changes", BlockType.hat, this::onBooleanChangeHat);
        this.booleanLink = Scratch3Block.ofHandler("boolean_link", BlockType.hat, this::booleanLinkHatEvent);

        this.setVariableToBlock = Scratch3Block.ofHandler("setvariableto", BlockType.command, this::setVariableToHandler);
        this.changeVariableByBlock = Scratch3Block.ofHandler("changevariableby", BlockType.command, this::changeVariableByHandler);

        this.onChangeVariableBlock = Scratch3Block.ofHandler("onchangevariable", BlockType.hat, this::onChangeVariableHat);
        this.onChangeVariableToBlock = Scratch3Block.ofHandler("onchangevariableto", BlockType.hat, this::onChangeVariableHatTo);

        this.setJsonVariableToBlock = Scratch3Block.ofHandler("set_json", BlockType.command, this::setJsonVariableToHandler);
        this.getJsonVariableToBlock = Scratch3Block.ofReporter("get_json", this::getJsonVariableToReporter);

        this.lastBackupBlock = Scratch3Block.ofReporter("last_backup_value", this::lastBackupValueReporter);
        this.backupBlock = Scratch3Block.ofHandler("addtobackup", BlockType.command, this::backupHandler);

        this.groupVariableBlock = Scratch3Block.ofReporter("group_variable", this::groupVariableReporter);
        this.setGroupVariableBlock = Scratch3Block.ofHandler("set_group_variable", BlockType.command, this::setGroupVariableHandler);
        this.changeGroupVariableBlock = Scratch3Block.ofHandler("change_group_variable", BlockType.command, this::changeGroupVariableHandler);
        this.setAndBackupGroupVariableBlock = Scratch3Block.ofHandler("set_group_variable_and_backup", BlockType.command, this::setAndBackupGroupVariableHandler);
        this.variableGroupLink = Scratch3Block.ofHandler("group_variable_link", BlockType.hat, this::onVariableGroupLinkHat);
    }

    private void onChangeVariableHatTo(WorkspaceBlock workspaceBlock) {
        workspaceBlock.handleNext(next -> {
            String varRefId = workspaceBlock.getFieldId("VARIABLE");
            String expectedValue = workspaceBlock.getFieldId("VALUE");
            BroadcastLock lock = broadcastLockManager.getOrCreateLock(workspaceBlock, WorkspaceStandaloneVariableEntity.PREFIX + varRefId);
            workspaceBlock.subscribeToLock(lock, new Function<Object, Boolean>() {
                @Override
                public Boolean apply(Object o) {
                    return String.valueOf(o).equals(expectedValue);
                }
            }, next::handle);
        });
    }

    private void onChangeVariableHat(WorkspaceBlock workspaceBlock) {
        workspaceBlock.handleNext(next -> {
            String varRefId = workspaceBlock.getFieldId("VARIABLE");
            BroadcastLock lock = broadcastLockManager.getOrCreateLock(workspaceBlock, WorkspaceStandaloneVariableEntity.PREFIX + varRefId);
            if (workspaceBlock.getFieldBoolean("DP")) {
                workspaceBlock.subscribeToLock(lock, next::handle);
            } else {
                workspaceBlock.subscribeToLock(lock, o -> !((DecimalType) o).equalToOldValue(), next::handle);
            }
        });
    }

    private Object getPreviousValue(WorkspaceBlock workspaceBlock) {
        WorkspaceBlockImpl workspaceBlockImpl = (WorkspaceBlockImpl) workspaceBlock;
        return ((WorkspaceBlockImpl) workspaceBlockImpl.getParent()).getLastValue();
    }

    private JSONObject getJsonVariableToReporter(WorkspaceBlock workspaceBlock) {
        WorkspaceJsonVariableEntity entity = entityContext.getEntity(WorkspaceJsonVariableEntity.PREFIX
                + workspaceBlock.getFieldId("json_variables"));
        String query = workspaceBlock.getInputString("ITEM");
        return Scratch3MutatorBlocks.reduceJSON(entity.getValue().toString(), query);
    }

    private void onVariableGroupLinkHat(WorkspaceBlock workspaceBlock) {
        try {
            WorkspaceBlock source = getWorkspaceBlockToLink(workspaceBlock);
            ((WorkspaceBlockImpl) source).linkVariable(workspaceBlock.getFieldId("group_variables_group"));
        } catch (Exception ex) {
            workspaceBlock.logError("Unable to link variable to wb: <{}>", workspaceBlock.getOpcode() +
                    CommonUtils.getErrorMessage(ex));
        }
    }

    private void booleanLinkHatEvent(WorkspaceBlock workspaceBlock) {
        try {
            WorkspaceBlock source = getWorkspaceBlockToLink(workspaceBlock);
            ((WorkspaceBlockImpl) source).linkBoolean(workspaceBlock.getFieldId("bool_variables_group"));
        } catch (Exception ex) {
            workspaceBlock.logError("Unable to link bool variable to wb: <{}>", workspaceBlock.getOpcode() +
                    CommonUtils.getErrorMessage(ex));
        }
    }

    private WorkspaceBlock getWorkspaceBlockToLink(WorkspaceBlock workspaceBlock) {
        WorkspaceBlock source = workspaceBlock.getInputWorkspaceBlock("SOURCE");
        if (source == null) {
            throw new IllegalArgumentException("Unable to find source block to link");
        }
        return source;
    }

    private void onBooleanChangeHat(WorkspaceBlock workspaceBlock) {
        workspaceBlock.handleNext(next -> {
            String varRefId = workspaceBlock.getFieldId("bool_variables_group");
            BroadcastLock lock = broadcastLockManager.getOrCreateLock(workspaceBlock, WorkspaceBooleanEntity.PREFIX + varRefId);

            if (workspaceBlock.getFieldBoolean("DP")) {
                workspaceBlock.subscribeToLock(lock, next::handle);
            } else {
                workspaceBlock.subscribeToLock(lock, o -> !((OnOffType) o).equalToOldValue(), next::handle);
            }
        });
    }

    private void inverseBooleanHandler(WorkspaceBlock workspaceBlock) {
        String boolVariableId = workspaceBlock.getFieldId("bool_variables_group");
        WorkspaceBooleanEntity entity = entityContext.getEntity(WorkspaceBooleanEntity.PREFIX + boolVariableId);
        entityContext.save(entity.inverseValue());
    }

    private void setAndBackupGroupVariableHandler(WorkspaceBlock workspaceBlock) {
        WorkspaceVariableEntity entity = this.setGroupVariableHandler(workspaceBlock);
        if (entity != null) {
            // backup
            entityContext.save(new WorkspaceVariableBackupValueCrudEntity()
                    .setCreationTime(new Date())
                    .setWorkspaceVariableEntity(entity)
                    .setValue(entity.getValue()));
            // TODO: backup value
        }
    }

    private Float groupVariableReporter(WorkspaceBlock workspaceBlock) {
        String groupVariablesItem = workspaceBlock.getFieldId("group_variables_group");
        WorkspaceVariableEntity groupVariableItemEntity =
                entityContext.getEntity(WorkspaceVariableEntity.PREFIX + groupVariablesItem);

        return groupVariableItemEntity.getValue();
    }

    private void changeGroupVariableHandler(WorkspaceBlock workspaceBlock) {
        String groupVariablesItem = workspaceBlock.getFieldId("group_variables_group");
        Float value = workspaceBlock.getInputFloat("ITEM");

        if (value != null) {
            WorkspaceVariableEntity entity = entityContext.getEntity(WorkspaceVariableEntity.PREFIX + groupVariablesItem);
            entityContext.save(entity.setValue(entity.getValue() + value));
        }
    }

    private WorkspaceVariableEntity setGroupVariableHandler(WorkspaceBlock workspaceBlock) {
        String groupVariablesItem = workspaceBlock.getFieldId("group_variables_group");
        Float value = workspaceBlock.getInputFloat("ITEM");
        if (value != null) {
            WorkspaceVariableEntity entity = entityContext.getEntity(WorkspaceVariableEntity.PREFIX + groupVariablesItem);
            entityContext.save(entity.setValue(value));
            return entity;
        }
        return null;
    }

    private void setBooleanHandler(WorkspaceBlock workspaceBlock) {
        String boolVariableId = workspaceBlock.getFieldId("bool_variables_group");
        Boolean value = workspaceBlock.getFieldBoolean("status");
        WorkspaceBooleanEntity entity = entityContext.getEntity(WorkspaceBooleanEntity.PREFIX + boolVariableId);
        entityContext.save(entity.setValue(value));
    }

    private Boolean isBoolVariableTrueReporter(WorkspaceBlock workspaceBlock) {
        String boolVariableId = workspaceBlock.getFieldId("bool_variables_group");
        WorkspaceBooleanEntity entity = entityContext.getEntity(WorkspaceBooleanEntity.PREFIX + boolVariableId);
        return entity.getValue();
    }

    private Float lastBackupValueReporter(WorkspaceBlock workspaceBlock) {
        String backupId = workspaceBlock.getFieldId("backup_list_group");
        WorkspaceBackupEntity entity = entityContext.getEntity(WorkspaceBackupEntity.PREFIX + backupId);
        return workspaceBackupRepository.getBackupLastValue(entity);
    }

    private void backupHandler(WorkspaceBlock workspaceBlock) {
        Float value = workspaceBlock.getInputFloat("ITEM");
        if (value != null) {
            String backupId = workspaceBlock.getFieldId("backup_list_group");

            WorkspaceBackupEntity entity = entityContext.getEntity(WorkspaceBackupEntity.PREFIX + backupId);
            entityContext.createDelayed(new WorkspaceBackupValueCrudEntity()
                    .setCreationTime(new Date())
                    .setWorkspaceBackupEntity(entity)
                    .setValue(value));
        }
    }

    private void changeVariableByHandler(WorkspaceBlock workspaceBlock) {
        String variableId = workspaceBlock.getFieldId("VARIABLE");
        Float changeValue = workspaceBlock.getInputFloat("ITEM");

        WorkspaceStandaloneVariableEntity workspaceStandaloneVariableEntity = entityContext.getEntity(WorkspaceStandaloneVariableEntity.PREFIX + variableId);

        if (workspaceStandaloneVariableEntity != null) {
            float value = workspaceStandaloneVariableEntity.getValue();
            entityContext.save(workspaceStandaloneVariableEntity.setValue(value + changeValue));
        }
    }

    private void setVariableToHandler(WorkspaceBlock workspaceBlock) {
        String variableId = workspaceBlock.getFieldId("VARIABLE");
        Float value = workspaceBlock.getInputFloat("ITEM");

        WorkspaceStandaloneVariableEntity workspaceStandaloneVariableEntity = entityContext.getEntity(WorkspaceStandaloneVariableEntity.PREFIX + variableId);
        if (workspaceStandaloneVariableEntity != null) {
            entityContext.save(workspaceStandaloneVariableEntity.setValue(value));
        }
    }

    private void setJsonVariableToHandler(WorkspaceBlock workspaceBlock) {
        String variableId = workspaceBlock.getFieldId("json_variables");
        JSONObject value = workspaceBlock.getInputJSON("ITEM");
        WorkspaceJsonVariableEntity workspaceJsonVariableEntity = entityContext.getEntity(WorkspaceJsonVariableEntity.PREFIX + variableId);
        if (workspaceJsonVariableEntity != null) {
            entityContext.save(workspaceJsonVariableEntity.setValue(value));
        }
    }
}
