package org.homio.app.manager.common.impl;

import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.log4j.Log4j2;
import org.homio.api.ContextWorkspace;
import org.homio.api.workspace.scratch.Scratch3ExtensionBlocks;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.workspace.WorkspaceService;
import org.jetbrains.annotations.NotNull;

@Log4j2
public class ContextWorkspaceImpl implements ContextWorkspace {

    private final @Getter
    @Accessors(fluent = true) ContextImpl context;
    private WorkspaceService workspaceService;

    public ContextWorkspaceImpl(ContextImpl context) {
        this.context = context;
    }

    public void onContextCreated(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @Override
    public void registerScratch3Extension(@NotNull Scratch3ExtensionBlocks scratch3ExtensionBlocks) {
        workspaceService.registerScratch3Extension(scratch3ExtensionBlocks);
    }
}
