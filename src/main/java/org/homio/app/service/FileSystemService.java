package org.homio.app.service;

import static org.homio.api.ui.field.selection.UIFieldTreeNodeSelection.LOCAL_FS;
import static org.homio.api.util.Constants.PRIMARY_DEVICE;

import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.homio.api.EntityContext;
import org.homio.api.entity.storage.BaseFileSystemEntity;
import org.homio.api.fs.FileSystemProvider;
import org.homio.app.manager.common.EntityContextImpl;
import org.homio.app.model.entity.LocalBoardEntity;
import org.homio.app.setting.console.ConsoleFMClearCacheButtonSetting;
import org.homio.app.spring.ContextCreated;
import org.homio.app.spring.ContextRefreshed;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;

@Getter
@Service
@RequiredArgsConstructor
public class FileSystemService implements ContextCreated, ContextRefreshed {

    private final EntityContextImpl entityContext;
    private List<BaseFileSystemEntity> fileSystems;
    private LocalFileSystemProvider localFileSystem;

    @Override
    public void onContextCreated(EntityContextImpl entityContext) {
        this.entityContext.event().addEntityRemovedListener(BaseFileSystemEntity.class, "fs-remove",
            e -> findAllFileSystems(this.entityContext));
        this.entityContext.event().addEntityCreateListener(BaseFileSystemEntity.class, "fs-create",
            e -> findAllFileSystems(this.entityContext));
        entityContext.setting().listenValue(ConsoleFMClearCacheButtonSetting.class, "fs-cache",
            jsonObject -> {
                for (BaseFileSystemEntity<?, ?> fileSystem : fileSystems) {
                    fileSystem.getFileSystem(entityContext).clearCache();
                }
            });

        LocalBoardEntity LocalBoardEntity = this.entityContext.getEntityRequire(LocalBoardEntity.class, PRIMARY_DEVICE);
        localFileSystem = LocalBoardEntity.getFileSystem(this.entityContext);
    }

    @Override
    public void onContextRefresh(EntityContext entityContext) {
        findAllFileSystems(this.entityContext);
    }

    public FileSystemProvider getFileSystem(String fs) {
        return getFileSystemEntity(fs).getFileSystem(entityContext);
    }

    public BaseFileSystemEntity<?, ?> getFileSystemEntity(@Nullable String fs) {
        if (StringUtils.isEmpty(fs) || fs.equals(LOCAL_FS) || fs.equals("dvc_board_primary")) {
            fs = localFileSystem.getEntity().getEntityID();
        }
        for (BaseFileSystemEntity<?, ?> fileSystem : fileSystems) {
            if (fileSystem.getEntityID().equals(fs) || fileSystem.getFileSystemAlias().equals(fs)) {
                return fileSystem;
            }
        }
        throw new RuntimeException("Unable to find file system with id: " + fs);
    }

    private void findAllFileSystems(EntityContextImpl entityContext) {
        fileSystems = entityContext.getEntityServices(BaseFileSystemEntity.class);
    }
}
