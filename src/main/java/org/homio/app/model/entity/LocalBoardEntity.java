package org.homio.app.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.log4j.Log4j2;
import org.homio.api.EntityContext;
import org.homio.api.entity.storage.BaseFileSystemEntity;
import org.homio.api.entity.types.MicroControllerBaseEntity;
import org.homio.api.fs.archive.ArchiveUtil;
import org.homio.api.fs.archive.ArchiveUtil.ArchiveFormat;
import org.homio.api.model.Status;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldIgnore;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.util.CommonUtils;
import org.homio.app.service.LocalFileSystemProvider;
import org.jetbrains.annotations.NotNull;

@Entity
@Log4j2
public class LocalBoardEntity extends MicroControllerBaseEntity<LocalBoardEntity>
    implements BaseFileSystemEntity<LocalBoardEntity, LocalFileSystemProvider> {

    public static final String PREFIX = "cbe_";
    public static final String DEFAULT_DEVICE_ENTITY_ID = PREFIX + CommonUtils.APP_UUID;

    @Override
    public String getDefaultName() {
        return "Local device";
    }

    @UIField(order = 200)
    public String getFileSystemRoot() {
        return getJsonData("fs_root", CommonUtils.getRootPath().toString());
    }

    public void setFileSystemRoot(String value) {
        setJsonData("fs_root", value);
    }

    @Override
    public boolean isDisableEdit() {
        return getEntityID().equals(DEFAULT_DEVICE_ENTITY_ID);
    }

    @Override
    public @NotNull String getEntityPrefix() {
        return PREFIX;
    }

    @Override
    public String getFileSystemAlias() {
        return "PRIMARY";
    }

    @Override
    public boolean isShowInFileManager() {
        return true;
    }

    @Override
    public String getFileSystemIcon() {
        return "fas fa-computer";
    }

    @Override
    public String getFileSystemIconColor() {
        return "#23819E";
    }

    @Override
    public boolean requireConfigure() {
        return false;
    }

    @Override
    public LocalFileSystemProvider buildFileSystem(EntityContext entityContext) {
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
    public Status getStatus() {
        return super.getStatus();
    }

    @Override
    public boolean isDisableDelete() {
        return true;
    }

    public static void ensureDeviceExists(EntityContext entityContext) {
        if (entityContext.getEntity(DEFAULT_DEVICE_ENTITY_ID) == null) {
            log.info("Save default compute board device");
            entityContext.save(new LocalBoardEntity().setEntityID(DEFAULT_DEVICE_ENTITY_ID));
        }
    }

    @Override
    public Set<String> getSupportArchiveFormats() {
        return Stream.of(ArchiveUtil.ArchiveFormat.values()).map(ArchiveFormat::getName).collect(Collectors.toSet());
    }
}
