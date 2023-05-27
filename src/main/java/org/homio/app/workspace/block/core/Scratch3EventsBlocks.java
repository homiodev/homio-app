package org.homio.app.workspace.block.core;

import lombok.Getter;
import lombok.SneakyThrows;
import org.homio.api.EntityContext;
import org.homio.api.workspace.BroadcastLock;
import org.homio.api.workspace.WorkspaceBlock;
import org.homio.api.workspace.scratch.Scratch3ExtensionBlocks;
import org.springframework.stereotype.Component;

@Getter
@Component
public class Scratch3EventsBlocks extends Scratch3ExtensionBlocks {

    public Scratch3EventsBlocks(EntityContext entityContext) {
        super("event", entityContext);

        blockHat("gotbroadcast", this::receiveEventHandler);
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
