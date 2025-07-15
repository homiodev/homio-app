package org.homio.app.workspace.block.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import lombok.Getter;
import org.homio.api.Context;
import org.homio.api.state.State;
import org.homio.api.workspace.Lock;
import org.homio.api.workspace.WorkspaceBlock;
import org.homio.api.workspace.scratch.Scratch3Block;
import org.homio.api.workspace.scratch.Scratch3ExtensionBlocks;
import org.homio.app.workspace.WorkspaceBlockImpl;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Getter
@Component
public class Scratch3ProceduresBlocks extends Scratch3ExtensionBlocks {

  public Scratch3ProceduresBlocks(Context context) {
    super("procedures", context);

    blockCommand(
        "definition",
            workspaceBlock -> workspaceBlock.handleNext(
                    next -> {
                      var refInput = workspaceBlock.getInputWorkspaceBlock("custom_block");
                      String uuid = getProcedureUUID((WorkspaceBlockImpl) refInput);
                      Lock lock = workspaceBlock.getLockManager().createLock(workspaceBlock, uuid);
                      workspaceBlock.subscribeToLock(
                          lock,
                          () -> {
                            Map<String, State> values = (Map<String, State>) lock.getValue();
                            if (values != null) {
                              for (Map.Entry<String, State> entry : values.entrySet()) {
                                  WorkspaceBlock argBlock = refInput.getInputWorkspaceBlock(entry.getKey());
                                  String realArgName = argBlock.getField("VALUE");
                                  workspaceBlock.setValue(realArgName, entry.getValue());
                              }
                            }
                            next.handle();
                          });
                    }));
    blockCommand(
        "call",
        workspaceBlock -> {
          Map<String, State> values = new HashMap<>();
          for (String input : workspaceBlock.getInputs().keySet()) {
            var value = workspaceBlock.getInputRawType(input);
            values.put(input, value);
          }
          String uuid = getProcedureUUID((WorkspaceBlockImpl) workspaceBlock);
          Lock lock = workspaceBlock.getLockManager().getLockRequired(workspaceBlock, uuid);
          lock.signalAll(values);
        });
  }

  private static String getProcedureUUID(WorkspaceBlockImpl refInput) {
    return Objects.hash(refInput.getProcedureCode(), refInput.getProcedureArgumentIds()) + "";
  }
}
