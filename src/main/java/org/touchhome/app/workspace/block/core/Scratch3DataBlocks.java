package org.touchhome.app.workspace.block.core;

import java.text.NumberFormat;
import java.util.function.BiFunction;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;
import org.touchhome.app.workspace.WorkspaceBlockImpl;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.state.State;
import org.touchhome.bundle.api.workspace.BroadcastLock;
import org.touchhome.bundle.api.workspace.WorkspaceBlock;
import org.touchhome.bundle.api.workspace.scratch.Scratch3ExtensionBlocks;

@Getter
@Component
public class Scratch3DataBlocks extends Scratch3ExtensionBlocks {

    public Scratch3DataBlocks(EntityContext entityContext) {
        super("data", entityContext);

        blockReporter("prev_variable", this::getPreviousValue);

        blockHat("onchange_group_variable_to", this::onChangeVariableHatTo);
        blockHat("onchange_group_variable", this::onChangeVariableHat);

        blockReporter("group_variable", this::groupVariableReporter);
        blockCommand("set_group_variable", this::setGroupVariableHandler);
        blockCommand("change_group_variable", this::changeGroupVariableHandler);
    }

    private void onChangeVariableHat(WorkspaceBlock workspaceBlock) {
        workspaceBlock.handleNext(
                next -> {
                    String variableId = workspaceBlock.getFieldId("group_variables_group");
                    BroadcastLock lock =
                            workspaceBlock
                                    .getBroadcastLockManager()
                                    .getOrCreateLock(workspaceBlock, variableId);
                    workspaceBlock.subscribeToLock(lock, next::handle);
                });
    }

    private void onChangeVariableHatTo(WorkspaceBlock workspaceBlock) {
        workspaceBlock.handleNext(
                next -> {
                    WhenValueOperator operator =
                            WhenValueOperator.getByOp(workspaceBlock.getField("OPERATOR"));
                    String variableId = workspaceBlock.getFieldId("group_variables_group");
                    BroadcastLock lock =
                            workspaceBlock
                                    .getBroadcastLockManager()
                                    .getOrCreateLock(workspaceBlock, variableId);
                    workspaceBlock.subscribeToLock(
                            lock, o -> operator.checkFn.apply(workspaceBlock, o), next::handle);
                });
    }

    private State getPreviousValue(WorkspaceBlock workspaceBlock) {
        WorkspaceBlockImpl workspaceBlockImpl = (WorkspaceBlockImpl) workspaceBlock;
        return workspaceBlockImpl.getParent().getLastValue();
    }

    /*private JSONObject getJsonVariableToReporter(WorkspaceBlock workspaceBlock) {
        WorkspaceJsonVariableEntity entity = entityContext.getEntity(WorkspaceJsonVariableEntity.PREFIX
                + workspaceBlock.getFieldId("json_variables"));
        String query = workspaceBlock.getInputString("ITEM");
        return Scratch3MutatorBlocks.reduceJSON(entity.getValue().toString(), query);
    }*/

    private State groupVariableReporter(WorkspaceBlock workspaceBlock) {
        String groupVariablesItem = workspaceBlock.getFieldId("group_variables_group");
        return groupVariablesItem == null
                ? null
                : State.of(entityContext.var().get(groupVariablesItem));
    }

    private void changeGroupVariableHandler(WorkspaceBlock workspaceBlock) {
        String groupVariablesItem = workspaceBlock.getFieldId("group_variables_group");
        Float value = workspaceBlock.getInputFloat("ITEM");

        if (value != null) {
            entityContext.var().inc(groupVariablesItem, value);
        }
    }

    private void setGroupVariableHandler(WorkspaceBlock workspaceBlock) {
        String groupVariablesItem = workspaceBlock.getFieldId("group_variables_group");
        Object value = workspaceBlock.getInput("ITEM", true);
        if (value != null) {
            entityContext.var().set(groupVariablesItem, value);
        }
    }

    @RequiredArgsConstructor
    private enum WhenValueOperator {
        More(
                ">",
                (workspaceBlock, value) -> toNumber(value) > workspaceBlock.getInputFloat("ITEM")),
        Less(
                "<",
                (workspaceBlock, value) -> toNumber(value) < workspaceBlock.getInputFloat("ITEM")),
        Eq(
                "=",
                (workspaceBlock, value) ->
                        value.toString().equals(workspaceBlock.getInputString("ITEM", ""))),
        Regexp(
                "regex",
                (workspaceBlock, value) -> {
                    return value.toString().matches(workspaceBlock.getInputString("ITEM", ""));
                }),
        Any("any", (workspaceBlock, o) -> true),
        NotEq(
                "!=",
                (workspaceBlock, value) ->
                        !value.toString().equals(workspaceBlock.getInputString("ITEM", "")));

        private final String op;
        private final BiFunction<WorkspaceBlock, Object, Boolean> checkFn;

        @SneakyThrows
        private static float toNumber(Object value) {
            if (value instanceof Number) {
                return ((Number) value).floatValue();
            }
            return NumberFormat.getInstance().parse(value.toString()).floatValue();
        }

        public static WhenValueOperator getByOp(String operator) {
            for (WhenValueOperator item : WhenValueOperator.values()) {
                if (item.op.equals(operator)) {
                    return item;
                }
            }
            throw new IllegalStateException("Unable to find compare operator: " + operator);
        }
    }
}
