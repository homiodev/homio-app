package org.homio.app.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.*;
import lombok.extern.log4j.Log4j2;
import nl.martijndwars.webpush.Utils;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.homio.api.Context;
import org.homio.api.entity.CreateSingleEntity;
import org.homio.api.entity.device.DeviceBaseEntity;
import org.homio.api.entity.storage.BaseFileSystemEntity;
import org.homio.api.fs.TreeConfiguration;
import org.homio.api.fs.archive.ArchiveUtil;
import org.homio.api.fs.archive.ArchiveUtil.ArchiveFormat;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.Icon;
import org.homio.api.model.Status;
import org.homio.api.service.EntityService;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldIgnore;
import org.homio.api.ui.field.UIFieldSlider;
import org.homio.api.ui.field.UIFieldType;
import org.homio.api.ui.field.action.UIContextMenuUploadAction;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.ui.route.UIRouteMicroController;
import org.homio.api.util.CommonUtils;
import org.homio.app.service.device.LocalBoardService;
import org.homio.app.service.device.LocalBoardUsbListener.DiskInfo;
import org.homio.app.service.device.LocalFileSystemProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.homio.api.util.Constants.PRIMARY_DEVICE;

@Entity
@Log4j2
@CreateSingleEntity
@UIRouteMicroController(icon = "fas fa-computer", color = "#9C3866", allowCreateItem = false)
public class LocalBoardEntity extends DeviceBaseEntity
        implements EntityService<LocalBoardService>,
        BaseFileSystemEntity<LocalFileSystemProvider> {

    public static LocalBoardEntity getEntity(Context context) {
        return context.db().get(LocalBoardEntity.class, PRIMARY_DEVICE);
    }

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

    @UIField(order = 250)
    @UIFieldSlider(min = 10, max = 300, header = "sec")
    public int getHardwareFetchInterval() {
        return getJsonData("cpu_interval", 10);
    }

    public void setHardwareFetchInterval(@Min(0) @Max(60) int value) {
        setJsonData("cpu_interval", value);
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
    public long getEntityServiceHashCode() {
        return getJsonDataHashCode("cpu_interval", "fs_root");
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
    public @NotNull Set<String> getSupportArchiveFormats() {
        return Stream.of(ArchiveUtil.ArchiveFormat.values()).map(ArchiveFormat::getName).collect(Collectors.toSet());
    }

    @Override
    public @NotNull List<TreeConfiguration> buildFileSystemConfiguration() {
        List<TreeConfiguration> configurations = BaseFileSystemEntity.super.buildFileSystemConfiguration();
        for (DiskInfo usb : getService().getUsbDevices()) {
            if (usb.getMount().isEmpty()) {
                continue;
            }
            String label = Objects.toString(usb.getModel(), usb.getMount());
            TreeConfiguration configuration = new TreeConfiguration(this, label, usb.alias, new Icon(usb.getIcon(), usb.getColor()));
            try {
                FileStore fileStore = Files.getFileStore(Path.of(usb.getMount()));
                long totalSpace = fileStore.getTotalSpace();
                long freeSpace = fileStore.getUsableSpace();
                configuration.setSize(freeSpace, totalSpace);
            } catch (IOException ignore) {
            }
            configurations.add(configuration);
        }
        return configurations;
    }

    @Override
    public Alias getAlias(int alias) {
        DiskInfo info = getService().getUsbDevice(alias);
        if (info != null) {
            return new Alias(info.getMount(), info.getAlias(), info.getMount(),
                    new Icon(info.getIcon(), info.getColor()));
        }
        return BaseFileSystemEntity.super.getAlias(alias);
    }

    @UIField(order = 900, type = UIFieldType.HTML, hideInEdit = true, hideOnEmpty = true)
    public String getMounts() {
        return optService().map(service -> {
            StringBuilder html = new StringBuilder();
            for (DiskInfo usbDevice : service.getUsbDevices()) {
                html.append("<div><i class=\"fa-fw %s\" style=\"color: %s\"></i>%s - %s</div>".
                        formatted(usbDevice.getIcon(), usbDevice.getColor(), usbDevice.getModel(),
                                StringUtils.defaultIfEmpty(usbDevice.getMount(), "No mounted")));
            }
            return html.toString();
        }).orElse(null);
    }

    @Override
    public void assembleActions(UIInputBuilder uiInputBuilder) {
        optService().ifPresent(service -> service.getUsbDevices().stream()
                .filter(d -> d.getMount().isEmpty()).forEach(diskInfo -> {
                    uiInputBuilder.addSelectableButton("mount_" + diskInfo.getUuid(),
                            new Icon("fab fa-usb", "#215A6A"), (context, params) -> {
                                service.getUsbListener().handleUsbDeviceAdded(diskInfo);
                                return null;
                            });
                }));
    }

    @Override
    public void beforePersist() {
        try {
            Security.addProvider(new BouncyCastleProvider());
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC", "BC");
            keyGen.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair keyPair = keyGen.generateKeyPair();

            String publicKey = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    Utils.encode((ECPublicKey) keyPair.getPublic())
            );

            String privateKey = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    Utils.encode((ECPrivateKey) keyPair.getPrivate())
            );

            setJsonData("vapid_pub", publicKey);
            setJsonData("vapid_private", privateKey);
        } catch (Exception ex) {
            log.error("Failed to generate VAPID key", ex);
        }
    }

    @JsonIgnore
    public String getVapidPublicKey() {
        return "BEk9iXWlOuZPpclq2aZGYtMmhcXeRjeA0He4bMIJXNVOniIQqCtWKLMWip1coTggt1UDuW0L5pS6byCHnlNqtP0";
        // return  getJsonData("vapid_pub");
    }

    @JsonIgnore
    public String getVapidPrivateKey() {
        return "39QynO3lQN6lzKt5-J2hdD8bZVPChaHCLWx-5cgX3hI";
        // return getJsonData("vapid_private");
    }

    @JsonIgnore
    public PushSubscription getVapidSubscription() {
        return getJsonData("vapid_sub", PushSubscription.class, false);
    }

    public void setVapidSubscription(PushSubscription subscription) {
        setJsonDataObject("vapid_sub", subscription);
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PushSubscription {
        private String endpoint;
        private Keys keys;

        @Getter
        @Setter
        public static class Keys {
            private String p256dh;
            private String auth;
        }
    }
}