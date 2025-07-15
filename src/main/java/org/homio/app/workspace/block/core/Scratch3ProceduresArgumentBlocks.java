package org.homio.app.workspace.block.core;

import lombok.Getter;
import org.homio.api.Context;
import org.homio.api.workspace.scratch.Scratch3ExtensionBlocks;
import org.springframework.stereotype.Component;

@Getter
@Component
public class Scratch3ProceduresArgumentBlocks extends Scratch3ExtensionBlocks {

  public Scratch3ProceduresArgumentBlocks(Context context) {
    super("argument", context);

    blockReporter(
        "reporter_string_number",
        workspaceBlock -> {
          String valueId = workspaceBlock.getField("VALUE");
          return workspaceBlock.getTop().getValue(valueId);
        });

    blockReporter(
        "reporter_boolean",
        workspaceBlock -> {
          String valueId = workspaceBlock.getField("VALUE");
          return workspaceBlock.getTop().getValue(valueId);
        });
  }
}
