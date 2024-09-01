package org.homio.app.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.homio.api.Context;
import org.homio.api.entity.storage.BaseFileSystemEntity;
import org.homio.api.fs.FileSystemProvider;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.model.entity.LocalBoardEntity;
import org.homio.app.service.device.LocalFileSystemProvider;
import org.homio.app.setting.console.ConsoleFMClearCacheButtonSetting;
import org.homio.app.spring.ContextCreated;
import org.homio.app.spring.ContextRefreshed;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;

import static org.homio.api.ui.field.selection.UIFieldTreeNodeSelection.LOCAL_FS;
import static org.homio.api.util.Constants.PRIMARY_DEVICE;

@Getter
@Service
@RequiredArgsConstructor
public class FileSystemService implements ContextCreated, ContextRefreshed {

    private final ContextImpl context;
    private List<BaseFileSystemEntity> fileSystems;
    private LocalFileSystemProvider localFileSystem;

    @Override
    public void onContextCreated(ContextImpl context) {
        this.context.event().addEntityRemovedListener(BaseFileSystemEntity.class, "fs-remove",
                e -> findAllFileSystems(this.context));
        this.context.event().addEntityCreateListener(BaseFileSystemEntity.class, "fs-create",
                e -> findAllFileSystems(this.context));
        this.context.event().addEntityUpdateListener(BaseFileSystemEntity.class, "fs-update",
                e -> findAllFileSystems(this.context));
        context.setting().listenValue(ConsoleFMClearCacheButtonSetting.class, "fs-cache",
                jsonObject -> {
                    for (BaseFileSystemEntity<?> fileSystem : fileSystems) {
                        fileSystem.getFileSystem(context, 0).clearCache();
                    }
                });

        LocalBoardEntity LocalBoardEntity = this.context.db().getRequire(LocalBoardEntity.class, PRIMARY_DEVICE);
        localFileSystem = LocalBoardEntity.getFileSystem(this.context, 0);
    }

    @Override
    public void onContextRefresh(Context context) {
        findAllFileSystems(this.context);
    }

    public FileSystemProvider getFileSystem(String fs, int alias) {
        BaseFileSystemEntity<?> entity = getFileSystemEntity(fs);
        return entity.getFileSystem(context, alias);
    }

    public BaseFileSystemEntity<?> getFileSystemEntity(@Nullable String fs) {
        if (StringUtils.isEmpty(fs) || fs.equals(LOCAL_FS) || fs.equals("dvc_board_primary")) {
            fs = localFileSystem.getEntity().getEntityID();
        }
        for (BaseFileSystemEntity<?> fileSystem : fileSystems) {
            if (fileSystem.getEntityID().equals(fs) || fileSystem.getFileSystemAlias().equals(fs)) {
                return fileSystem;
            }
        }
        throw new RuntimeException("Unable to find file system with id: " + fs);
    }

    private void findAllFileSystems(ContextImpl context) {
        fileSystems = context.getEntityServices(BaseFileSystemEntity.class);
    }
}
