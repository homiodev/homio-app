package org.homio.app.model.entity;

import static org.homio.api.util.Constants.PRIMARY_DEVICE;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.log4j.Log4j2;
import org.homio.api.Context;
import org.homio.api.entity.storage.BaseFileSystemEntity;
import org.homio.api.entity.types.MicroControllerBaseEntity;
import org.homio.api.fs.archive.ArchiveUtil;
import org.homio.api.fs.archive.ArchiveUtil.ArchiveFormat;
import org.homio.api.model.Icon;
import org.homio.api.model.Status;
import org.homio.api.service.EntityService;
import org.homio.api.ui.UISidebarChildren;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldIgnore;
import org.homio.api.ui.field.UIFieldSlider;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.util.CommonUtils;
import org.homio.app.service.LocalBoardService;
import org.homio.app.service.LocalFileSystemProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Entity
@Log4j2
@UISidebarChildren(icon = "", color = "", allowCreateItem = false)
public class LocalBoardEntity extends MicroControllerBaseEntity
        implements EntityService<LocalBoardService, LocalBoardEntity>,
    BaseFileSystemEntity<LocalBoardEntity, LocalFileSystemProvider> {

    @Override
    public String getDefaultName() {
        return "Local device";
    }

    @UIField(order = 200)
    public @NotNull String getFileSystemRoot() {
        return getJsonDataRequire("fs_root", CommonUtils.getRootPath().toString());
    }

    @UIField(order = 250, inlineEdit = true)
    @UIFieldSlider(min = 1, max = 60)
    public int getCpuFetchInterval() {
        return getJsonData("cpu_interval", 10);
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
    public @NotNull LocalFileSystemProvider buildFileSystem(@NotNull Context context) {
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
    public @Nullable Set<String> getConfigurationErrors() {
        return null;
    }

    @Override
    public long getEntityServiceHashCode() {
        return getJsonDataHashCode("cpu_interval", "fs_root");
    }

    @Override
    public @NotNull Class<LocalBoardService> getEntityServiceItemClass() {
        return LocalBoardService.class;
    }

    @Override
    public @Nullable LocalBoardService createService(@NotNull Context context) {
        return new LocalBoardService(context, this);
    }

    @Override
    protected @NotNull String getDevicePrefix() {
        return "board";
    }

    @Override
    public boolean isDisableEdit() {
        return true;
    }

    public void setCpuFetchInterval(@Min(0) @Max(60) int value) {
        setJsonData("cpu_interval", value);
    }

    public static LocalBoardEntity ensureDeviceExists(Context context) {
        LocalBoardEntity entity = context.db().getEntity(LocalBoardEntity.class, PRIMARY_DEVICE);
        if (entity == null) {
            log.info("Save default compute board device");
            entity = new LocalBoardEntity();
            entity.setEntityID(PRIMARY_DEVICE);
            entity = context.db().save(entity);
        }
        return entity;
    }

    @Override
    public @NotNull Set<String> getSupportArchiveFormats() {
        return Stream.of(ArchiveUtil.ArchiveFormat.values()).map(ArchiveFormat::getName).collect(Collectors.toSet());
    }
}
