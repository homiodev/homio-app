package org.touchhome.app.workspace.block.core;

import lombok.Getter;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.workspace.BroadcastLock;
import org.touchhome.bundle.api.workspace.WorkspaceBlock;
import org.touchhome.bundle.api.workspace.scratch.Scratch3ExtensionBlocks;

@Getter
@Component
public class Scratch3EventsBlocks extends Scratch3ExtensionBlocks {

    public Scratch3EventsBlocks(EntityContext entityContext) {
        super("event", entityContext);

        blockHat("got_broadcast", this::receiveEventHandler);
        blockCommand("broadcast", this::broadcastEventHandler);
    }

    private void broadcastEventHandler(WorkspaceBlock workspaceBlock) {
        fireBroadcastEvent(workspaceBlock.getInputString("BROADCAST_INPUT"));
    }

    @SneakyThrows
    private void receiveEventHandler(WorkspaceBlock workspaceBlock) {
        workspaceBlock.handleNext(
                next -> {
                    String broadcastRefEntityID = workspaceBlock.getFieldId("BROADCAST_OPTION");
                    BroadcastLock lock =
                            workspaceBlock
                                    .getBroadcastLockManager()
                                    .getOrCreateLock(workspaceBlock, broadcastRefEntityID);

                    workspaceBlock.subscribeToLock(lock, next::handle);
                });
    }

    public void fireBroadcastEvent(String broadcastRefEntityID) {
        entityContext.var().set(broadcastRefEntityID, "event");
    }
}
