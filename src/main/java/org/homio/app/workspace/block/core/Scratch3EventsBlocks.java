package org.homio.app.workspace.block.core;

import lombok.Getter;
import lombok.SneakyThrows;
import org.homio.api.Context;
import org.homio.api.workspace.Lock;
import org.homio.api.workspace.WorkspaceBlock;
import org.homio.api.workspace.scratch.Scratch3ExtensionBlocks;
import org.springframework.stereotype.Component;

@Getter
@Component
public class Scratch3EventsBlocks extends Scratch3ExtensionBlocks {

    public Scratch3EventsBlocks(Context context) {
        super("event", context);

        blockHat("gotbroadcast", this::receiveEventHandler);
        blockCommand("broadcast", this::broadcastEventHandler);
    }

    public void fireBroadcastEvent(String broadcastRefEntityID) {
        context.var().set(broadcastRefEntityID, "event");
    }

    private void broadcastEventHandler(WorkspaceBlock workspaceBlock) {
        fireBroadcastEvent(workspaceBlock.getInputString("BROADCAST_INPUT"));
    }

    @SneakyThrows
    private void receiveEventHandler(WorkspaceBlock workspaceBlock) {
        workspaceBlock.handleNext(
                next -> {
                    String broadcastRefEntityID = workspaceBlock.getFieldId("BROADCAST_OPTION");
                    Lock lock =
                            workspaceBlock
                                    .getLockManager()
                                    .getLock(workspaceBlock, broadcastRefEntityID);

                    workspaceBlock.subscribeToLock(lock, next::handle);
                });
    }
}
