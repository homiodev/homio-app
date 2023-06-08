package org.homio.app.manager.common.impl;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.homio.api.EntityContextWorkspace;
import org.homio.api.workspace.scratch.Scratch3ExtensionBlocks;
import org.homio.app.manager.common.EntityContextImpl;
import org.homio.app.workspace.WorkspaceService;
import org.jetbrains.annotations.NotNull;

@Log4j2
public class EntityContextWorkspaceImpl implements EntityContextWorkspace {

    @Getter
    private final EntityContextImpl entityContext;
    private WorkspaceService workspaceService;

    public EntityContextWorkspaceImpl(EntityContextImpl entityContext) {
        this.entityContext = entityContext;
    }

    public void onContextCreated(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @Override
    public void registerScratch3Extension(@NotNull Scratch3ExtensionBlocks scratch3ExtensionBlocks) {
        workspaceService.registerScratch3Extension(scratch3ExtensionBlocks);
    }
}
