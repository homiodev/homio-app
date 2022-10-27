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
import org.touchhome.bundle.api.workspace.scratch.BlockType;
import org.touchhome.bundle.api.workspace.scratch.Scratch3Block;
import org.touchhome.bundle.api.workspace.scratch.Scratch3ExtensionBlocks;
import org.touchhome.common.util.CommonUtils;

@Getter
@Component
public class Scratch3DataBlocks extends Scratch3ExtensionBlocks {

  // variable group
  private final Scratch3Block setGroupVariableBlock;
  private final Scratch3Block changeGroupVariableBlock;
  private final Scratch3Block groupVariableBlock;
  private final Scratch3Block variableGroupLink;

  private final Scratch3Block getPrevVariableBlock;
  private final Scratch3Block onChangeVariableToBlock;
  private final Scratch3Block onChangeVariableBlock;

  public Scratch3DataBlocks(EntityContext entityContext) {
    super("data", entityContext);

    // Blocks
    this.getPrevVariableBlock = Scratch3Block.ofReporter("prev_variable", this::getPreviousValue);

    this.onChangeVariableToBlock =
        Scratch3Block.ofHandler("onchange_group_variable_to", BlockType.hat, this::onChangeVariableHatTo);
    this.onChangeVariableBlock =
        Scratch3Block.ofHandler("onchange_group_variable", BlockType.hat, this::onChangeVariableHat);

    this.groupVariableBlock = Scratch3Block.ofReporter("group_variable", this::groupVariableReporter);
    this.setGroupVariableBlock =
        Scratch3Block.ofHandler("set_group_variable", BlockType.command, this::setGroupVariableHandler);
    this.changeGroupVariableBlock =
        Scratch3Block.ofHandler("change_group_variable", BlockType.command, this::changeGroupVariableHandler);
    this.variableGroupLink = Scratch3Block.ofHandler("group_variable_link", BlockType.hat, this::onVariableGroupLinkHat);
  }

  private void onChangeVariableHat(WorkspaceBlock workspaceBlock) {
    workspaceBlock.handleNext(next -> {
      String variableId = workspaceBlock.getFieldId("group_variables_group");
      BroadcastLock lock = workspaceBlock.getBroadcastLockManager().getOrCreateLock(workspaceBlock, variableId);
      workspaceBlock.subscribeToLock(lock, next::handle);
    });
  }

  private void onChangeVariableHatTo(WorkspaceBlock workspaceBlock) {
    workspaceBlock.handleNext(next -> {
      WhenValueOperator operator = WhenValueOperator.getByOp(workspaceBlock.getField("OPERATOR"));
      String variableId = workspaceBlock.getFieldId("group_variables_group");
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

  private State groupVariableReporter(WorkspaceBlock workspaceBlock) {
    String groupVariablesItem = workspaceBlock.getFieldId("group_variables_group");
    return groupVariablesItem == null ? null : State.of(entityContext.var().get(groupVariablesItem));
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
    More(">", (workspaceBlock, value) -> toNumber(value) > workspaceBlock.getInputFloat("ITEM")),
    Less("<", (workspaceBlock, value) -> toNumber(value) < workspaceBlock.getInputFloat("ITEM")),
    Eq("=", (workspaceBlock, value) -> value.toString().equals(workspaceBlock.getInputString("ITEM", ""))),
    Regexp("regex", (workspaceBlock, value) -> {
      return value.toString().matches(workspaceBlock.getInputString("ITEM", ""));
    }),
    Any("any", (workspaceBlock, o) -> true),
    NotEq("!=", (workspaceBlock, value) -> !value.toString().equals(workspaceBlock.getInputString("ITEM", "")));

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
