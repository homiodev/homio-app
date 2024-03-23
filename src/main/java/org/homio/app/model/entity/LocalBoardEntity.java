package org.homio.app.model.entity;

import static org.homio.api.util.Constants.PRIMARY_DEVICE;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.homio.api.Context;
import org.homio.api.entity.storage.BaseFileSystemEntity;
import org.homio.api.entity.types.MicroControllerBaseEntity;
import org.homio.api.fs.TreeConfiguration;
import org.homio.api.fs.archive.ArchiveUtil;
import org.homio.api.fs.archive.ArchiveUtil.ArchiveFormat;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.Icon;
import org.homio.api.model.Status;
import org.homio.api.service.EntityService;
import org.homio.api.ui.UISidebarChildren;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldIgnore;
import org.homio.api.ui.field.UIFieldSlider;
import org.homio.api.ui.field.action.UIContextMenuUploadAction;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.util.CommonUtils;
import org.homio.app.service.device.LocalBoardService;
import org.homio.app.service.device.LocalBoardUsbListener.UsbDeviceInfo;
import org.homio.app.service.device.LocalFileSystemProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.springframework.web.multipart.MultipartFile;

@Entity
@Log4j2
@UISidebarChildren(icon = "", color = "", allowCreateItem = false)
public class LocalBoardEntity extends MicroControllerBaseEntity
    implements EntityService<LocalBoardService>,
    BaseFileSystemEntity<LocalFileSystemProvider> {

    @Override
    public String getDefaultName() {
        return "Local device";
    }

    @UIField(order = 200)
    public @NotNull String getFileSystemRoot() {
        return getJsonDataRequire("fs_root", CommonUtils.getRootPath().toString());
    }

    @UIField(order = 250)
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
    public @NotNull LocalFileSystemProvider buildFileSystem(@NotNull Context context, int alias) {
        return new LocalFileSystemProvider(this, alias);
    }

    @Override
    public long getConnectionHashCode() {
        return 0;
    }

    @Override
    public boolean isShowHiddenFiles() {
        return true;
    }

    @SneakyThrows
    @UIContextMenuUploadAction(value = "UPLOAD_FONT", icon = "fas fa-font", supportedFormats = {".ttf"})
    public ActionResponseModel uploadFont(JSONObject params) {
        MultipartFile[] files = (MultipartFile[]) params.get("files");
        for (MultipartFile file : files) {
            String filename = file.getOriginalFilename();
            if (filename != null && filename.endsWith(".ttf")) {
                Path fonts = CommonUtils.createDirectoriesIfNotExists(CommonUtils.getConfigPath().resolve("fonts"));
                file.transferTo(fonts.resolve(filename));
            }
        }
        context().ui().dialog().reloadWindow("Apply new fonts", 5);
        return ActionResponseModel.success();
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
    public void assembleActions(UIInputBuilder uiInputBuilder) {

    }

    @Override
    protected @NotNull String getDevicePrefix() {
        return "board";
    }

    public void setCpuFetchInterval(@Min(0) @Max(60) int value) {
        setJsonData("cpu_interval", value);
    }

    public static void ensureDeviceExists(Context context) {
        LocalBoardEntity entity = context.db().getEntity(LocalBoardEntity.class, PRIMARY_DEVICE);
        if (entity == null) {
            log.info("Save default compute board device");
            entity = new LocalBoardEntity();
            entity.setEntityID(PRIMARY_DEVICE);
            context.db().save(entity);
        }
    }

    @Override
    public @NotNull Set<String> getSupportArchiveFormats() {
        return Stream.of(ArchiveUtil.ArchiveFormat.values()).map(ArchiveFormat::getName).collect(Collectors.toSet());
    }

    @Override
    public @NotNull List<TreeConfiguration> buildFileSystemConfiguration(@NotNull Context context) {
        List<TreeConfiguration> configurations = BaseFileSystemEntity.super.buildFileSystemConfiguration(context);
        for (UsbDeviceInfo usbDevice : getService().getUsbDevices()) {
            String label = StringUtils.defaultString(usbDevice.getLabel(), usbDevice.getMount());
            configurations.add(new TreeConfiguration(this, label, usbDevice.alias, new Icon(usbDevice.getIcon(), usbDevice.getColor())));
        }
        return configurations;
    }
}
