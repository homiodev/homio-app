package org.homio.app.workspace.block.core;

import java.text.NumberFormat;
import java.util.function.BiFunction;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.homio.api.EntityContext;
import org.homio.api.state.State;
import org.homio.api.util.DataSourceUtil;
import org.homio.api.workspace.BroadcastLock;
import org.homio.api.workspace.WorkspaceBlock;
import org.homio.api.workspace.scratch.Scratch3ExtensionBlocks;
import org.homio.app.workspace.WorkspaceBlockImpl;
import org.springframework.stereotype.Component;

@Getter
@Component
public class Scratch3DataBlocks extends Scratch3ExtensionBlocks {

    public Scratch3DataBlocks(EntityContext entityContext) {
        super("data", entityContext);

        blockReporter("prev_variable", this::getPreviousValue);

        blockHat("onchange_variable_to", this::onChangeVariableHatTo);
        blockHat("onchange_variable", this::onChangeVariableHat);

        blockReporter("variable", this::variableReporter);
        blockCommand("setvariableto", this::setVariableHandler);
        blockCommand("changevariableby", this::changeVariableHandler);
    }

    private void onChangeVariableHat(WorkspaceBlock workspaceBlock) {
        workspaceBlock.handleNext(next -> {
            String variableId = getVariable(workspaceBlock);
            BroadcastLock lock = workspaceBlock.getBroadcastLockManager().getOrCreateLock(workspaceBlock, variableId);
            workspaceBlock.subscribeToLock(lock, next::handle);
        });
    }

    private void onChangeVariableHatTo(WorkspaceBlock workspaceBlock) {
        workspaceBlock.handleNext(next -> {
            WhenValueOperator operator = WhenValueOperator.getByOp(workspaceBlock.getField("OPERATOR"));
            String variableId = getVariable(workspaceBlock);
            BroadcastLock lock = workspaceBlock.getBroadcastLockManager().getOrCreateLock(workspaceBlock, variableId);
            workspaceBlock.subscribeToLock(lock, o -> operator.checkFn.apply(workspaceBlock, o), next::handle);
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

    private State variableReporter(WorkspaceBlock workspaceBlock) {
        String variable = getVariable(workspaceBlock);
        return variable == null ? null : State.of(entityContext.var().get(variable));
    }

    private void changeVariableHandler(WorkspaceBlock workspaceBlock) {
        String variable = getVariable(workspaceBlock);
        Float value = workspaceBlock.getInputFloat("ITEM");

        if (value != null) {
            entityContext.var().inc(variable, value);
        }
    }

    private void setVariableHandler(WorkspaceBlock workspaceBlock) {
        String variable = getVariable(workspaceBlock);
        Object value = workspaceBlock.getInput("ITEM", true);
        if (value != null) {
            entityContext.var().set(variable, value);
        }
    }

    private String getVariable(WorkspaceBlock workspaceBlock) {
        String source = workspaceBlock.getFieldId("VARIABLE");
        return DataSourceUtil.getSelection(source).getEntityValue();
    }

    @RequiredArgsConstructor
    private enum WhenValueOperator {
        More(">",
                (workspaceBlock, value) -> toNumber(value) > workspaceBlock.getInputFloat("ITEM")),
        Less("<",
                (workspaceBlock, value) -> toNumber(value) < workspaceBlock.getInputFloat("ITEM")),
        Eq("=",
                (workspaceBlock, value) -> value.toString().equals(workspaceBlock.getInputString("ITEM", ""))),
        Regexp("regex",
                (workspaceBlock, value) -> {
                    return value.toString().matches(workspaceBlock.getInputString("ITEM", ""));
                }),
        Any("any", (workspaceBlock, o) -> true),
        NotEq("!=",
                (workspaceBlock, value) -> !value.toString().equals(workspaceBlock.getInputString("ITEM", "")));

        private final String op;
        private final BiFunction<WorkspaceBlock, Object, Boolean> checkFn;

        public static WhenValueOperator getByOp(String operator) {
            for (WhenValueOperator item : WhenValueOperator.values()) {
                if (item.op.equals(operator)) {
                    return item;
                }
            }
            throw new IllegalStateException("Unable to find compare operator: " + operator);
        }

        @SneakyThrows
        private static float toNumber(Object value) {
            if (value instanceof Number) {
                return ((Number) value).floatValue();
            }
            return NumberFormat.getInstance().parse(value.toString()).floatValue();
        }
    }
}
