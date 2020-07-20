package org.touchhome.app.workspace;

import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.scratch.WorkspaceBlock;
import org.touchhome.bundle.api.thread.BackgroundProcessService;
import org.touchhome.bundle.api.workspace.WorkspaceEntity;

public class WorkspaceBlockProcessService extends BackgroundProcessService<Void> {

    private final WorkspaceBlock workspaceBlock;
    private final WorkspaceEntity workspaceEntity;

    WorkspaceBlockProcessService(WorkspaceBlock workspaceBlock, WorkspaceEntity workspaceEntity, EntityContext entityContext) {
        super("workspace_block_" + workspaceBlock.getId(), entityContext);
        this.workspaceBlock = workspaceBlock;
        this.workspaceBlock.setStateHandler(this::setState);
        this.workspaceEntity = workspaceEntity;
    }

    @Override
    protected Void runInternal() {
        String oldName = Thread.currentThread().getName();
        try {
            Thread.currentThread().setName(this.workspaceEntity.getEntityID());
            workspaceBlock.handle();
            logInfo("Workspace thread finished");
            return null;
        } finally {
            Thread.currentThread().setName(oldName);
        }
    }

    @Override
    public boolean onException(Exception ex) {
        return false;
    }

    @Override
    public long getPeriod() {
        return 0;
    }

    @Override
    public boolean shouldStartNow() {
        return true;
    }

    @Override
    public boolean canWork() {
        return true;
    }

    @Override
    protected boolean isAutoStart() {
        return true;
    }

    @Override
    public String getDescription() {
        return "Tab[" + this.workspaceEntity.getName() + "]. " + this.workspaceBlock.getDescription();
    }
}
