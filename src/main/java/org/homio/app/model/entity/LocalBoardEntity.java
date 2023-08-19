package org.homio.app.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import lombok.extern.log4j.Log4j2;
import org.homio.api.EntityContext;
import org.homio.api.entity.storage.BaseFileSystemEntity;
import org.homio.api.entity.types.MicroControllerBaseEntity;
import org.homio.api.fs.archive.ArchiveUtil;
import org.homio.api.fs.archive.ArchiveUtil.ArchiveFormat;
import org.homio.api.model.Icon;
import org.homio.api.model.Status;
import org.homio.api.ui.UISidebarChildren;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldIgnore;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.util.CommonUtils;
import org.homio.app.service.LocalFileSystemProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.homio.api.util.Constants.PRIMARY_DEVICE;

@Entity
@Log4j2
@UISidebarChildren(icon = "", color = "", allowCreateItem = false)
public class LocalBoardEntity extends MicroControllerBaseEntity
        implements BaseFileSystemEntity<LocalBoardEntity, LocalFileSystemProvider> {

    @Override
    public String getDefaultName() {
        return "Local device";
    }

    @UIField(order = 200)
    public @NotNull String getFileSystemRoot() {
        return getJsonDataRequire("fs_root", CommonUtils.getRootPath().toString());
    }

    public void setFileSystemRoot(String value) {
        setJsonData("fs_root", value);
    }

    @Override
    public @NotNull String getFileSystemAlias() {
        return "PRIMARY";
    }

    @Override
    public boolean isShowInFileManager() {
        return true;
    }

    @Override
    public @NotNull Icon getFileSystemIcon() {
        return new Icon("fas fa-computer", "#23819E");
    }

    @Override
    public boolean requireConfigure() {
        return false;
    }

    @Override
    public @NotNull LocalFileSystemProvider buildFileSystem(@NotNull EntityContext entityContext) {
        return new LocalFileSystemProvider(this);
    }

    @Override
    public long getConnectionHashCode() {
        return 0;
    }

    @Override
    public boolean isShowHiddenFiles() {
        return true;
    }

    @Override
    public void assembleActions(UIInputBuilder uiInputBuilder) {

    }

    @Override
    @JsonIgnore
    @UIFieldIgnore
    public @NotNull Status getStatus() {
        return Status.OFFLINE;
    }

    @Override
    public boolean isDisableDelete() {
        return true;
    }

    @Override
    protected @NotNull String getDevicePrefix() {
        return "board";
    }

    @Override
    public boolean isDisableEdit() {
        return true;
    }

    public static void ensureDeviceExists(EntityContext entityContext) {
        if (entityContext.getEntity(LocalBoardEntity.class, PRIMARY_DEVICE) == null) {
            log.info("Save default compute board device");
            LocalBoardEntity entity = new LocalBoardEntity();
            entity.setEntityID(PRIMARY_DEVICE);
            entityContext.save(entity);
        }
    }

    @Override
    public @NotNull Set<String> getSupportArchiveFormats() {
        return Stream.of(ArchiveUtil.ArchiveFormat.values()).map(ArchiveFormat::getName).collect(Collectors.toSet());
    }
}
