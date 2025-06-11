package org.homio.app.model.entity;

import com.pivovarit.function.ThrowingFunction;
import jakarta.persistence.Entity;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.homio.api.Context;
import org.homio.api.entity.device.DeviceBaseEntity;
import org.homio.api.entity.storage.BaseFileSystemEntity;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.service.EntityService;
import org.homio.api.ui.field.*;
import org.homio.api.ui.field.action.UIContextMenuAction;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.ui.field.condition.UIFieldShowOnCondition;
import org.homio.api.ui.route.UIRouteStorage;
import org.homio.api.util.SecureString;
import org.homio.app.model.entity.fsnProvider.FtpSocketClient;
import org.homio.app.model.entity.fsnProvider.SmbSocketClient;
import org.homio.app.model.entity.fsnProvider.WebdavSocketClient;
import org.homio.app.service.device.NetworkDriveFileSystem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.Proxy.Type;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@SuppressWarnings({"JpaAttributeTypeInspection", "JpaAttributeMemberSignatureInspection", "unused", "DataFlowIssue"})
@Entity
@UIRouteStorage(icon = "fas fa-network-wired", color = "#1AA36A")
public class NetworkDriveEntity extends DeviceBaseEntity implements BaseFileSystemEntity<NetworkDriveFileSystem>,
        EntityService<NetworkDriveEntity.NetworkDriveService> {

    public static final String PREFIX = "nd";

    public @NotNull String getFileSystemRoot() {
        return getJsonData("fs_root", "/");
    }

    @UIField(order = 1, required = true, inlineEditWhenEmpty = true, copyButton = true)
    @UIFieldGroup(value = "CONNECT", order = 10, borderColor = "#2782B0")
    public String getUrl() {
        return getJsonData("url");
    }

    public NetworkDriveEntity setUrl(String value) {
        setJsonData("url", value);
        return this;
    }

    @UIField(order = 2)
    @UIFieldPort(min = 0)
    @UIFieldGroup("CONNECT")
    public int getPort() {
        return getJsonData("port", 0);
    }

    public void setPort(int value) {
        setJsonData("port", value);
    }

    @UIField(order = 3)
    @UIFieldSlider(min = 0, max = 60)
    @UIFieldGroup("CONNECT")
    public int getControlKeepAliveTimeout() {
        return getJsonData("kat", 0);
    }

    public void setControlKeepAliveTimeout(int value) {
        setJsonData("kat", value);
    }

    @UIField(order = 4)
    @UIFieldSlider(min = 5, max = 60)
    @UIFieldGroup("CONNECT")
    public int getConnectTimeout() {
        return getJsonData("ct", 60);
    }

    public void setConnectTimeout(int value) {
        setJsonData("ct", value);
    }

    @UIField(order = 5)
    @UIFieldGroup("CONNECT")
    @UIFieldNoReadDefaultValue
    public NetworkDriveType getNetworkDriveType() {
        return getJsonDataEnum("type", NetworkDriveType.Auto);
    }

    public void setNetworkDriveType(NetworkDriveType value) {
        setJsonDataEnum("type", value);
    }

    @UIField(order = 6)
    @UIFieldGroup("CONNECT")
    @UIFieldShowOnCondition("return context.get('networkDriveType') == 'Samba'")
    public String getDomain() {
        return getJsonData("domain", "WORKSPACE");
    }

    public void setDomain(String value) {
        setJsonData("domain", value);
    }

    @UIField(order = 7)
    @UIFieldGroup("CONNECT")
    @UIFieldShowOnCondition("return context.get('networkDriveType') == 'Samba'")
    public String getShareName() {
        return getJsonData("share");
    }

    public void setShareName(String value) {
        setJsonData("share", value);
    }

    @UIField(order = 1)
    @UIFieldGroup(value = "AUTH", order = 10, borderColor = "#9C1A9C")
    public String getUser() {
        return getJsonData("user");
    }

    public NetworkDriveEntity setUser(String value) {
        setJsonData("user", value);
        return this;
    }

    @UIField(order = 2)
    @UIFieldGroup("AUTH")
    public SecureString getPassword() {
        return getJsonSecure("pwd");
    }

    public NetworkDriveEntity setPassword(String value) {
        setJsonDataSecure("pwd", value);
        return this;
    }

    @UIField(order = 1, hideInView = true)
    @UIFieldGroup(value = "PROXY", order = 20, borderColor = "#8C324C")
    public Type getProxyType() {
        return getJsonDataEnum("pt", Type.DIRECT);
    }

    public void setProxyType(Type value) {
        setJsonDataEnum("pt", value);
    }

    @UIField(order = 2, hideInView = true)
    @UIFieldGroup("PROXY")
    public String getProxyHost() {
        return getJsonData("ph");
    }

    public void setProxyHost(String value) {
        setJsonData("ph", value);
    }

    @UIField(order = 3, hideInView = true)
    @UIFieldGroup("PROXY")
    public int getProxyPort() {
        return getJsonData("pp", 0);
    }

    public void setProxyPort(int value) {
        setJsonData("pp", value);
    }

    @Override
    public @NotNull String getFileSystemAlias() {
        return "ND";
    }

    @Override
    public boolean isShowInFileManager() {
        return true;
    }

    @Override
    public boolean requireConfigure() {
        return StringUtils.isEmpty(getUrl());
    }

    @Override
    public @NotNull NetworkDriveFileSystem buildFileSystem(@NotNull Context context, int alias) {
        return new NetworkDriveFileSystem(this, context);
    }

    @Override
    public long getConnectionHashCode() {
        return Objects.hash(getUrl(), getUser(), getPassword());
    }

    @Override
    public boolean isShowHiddenFiles() {
        return true;
    }

    @Override
    public String getDefaultName() {
        if (StringUtils.isNotEmpty(getUrl())) {
            String name = getUrl();
            if (name.startsWith("http://")) {
                name = name.substring(7);
            } else if (name.startsWith("https://")) {
                name = name.substring(8);
            }
            return name;
        }
        return "";
    }

    @UIContextMenuAction(value = "TEST_CONNECTION", icon = "fas fa-ethernet")
    public ActionResponseModel testConnection() {
        try (var client = createNetworkClient().connect(false)) {
            return ActionResponseModel.showSuccess("Success connect to " + getUrl());
        } catch (Exception e) {
            return ActionResponseModel.showError(e);
        }
    }

    public <T> T execute(ThrowingFunction<NetworkClient, T, Exception> handler, boolean localPassive) throws Exception {
        try (var client = createNetworkClient().connect(localPassive)) {
            return handler.apply(client);
        }
    }

    @Override
    public @Nullable Set<String> getConfigurationErrors() {
        if (getUrl().isEmpty()) {
            return Set.of("W.ERROR.NO_URL");
        }
        return null;
    }

    @Override
    public @Nullable String getImageIdentifierImpl() {
        if (getJsonData().has("img")) {
            return getJsonData("img");
        }
        var networkDriveType = discoverNetworkDriveType();
        if (networkDriveType.equals(NetworkDriveType.Auto)) {
            return "NetworkDriveEntity.png";
        }
        return networkDriveType + "Entity.png";
    }

    private NetworkDriveType discoverNetworkDriveType() {
        if (NetworkDriveType.Auto.equals(getNetworkDriveType())) {
            if (getUrl().startsWith("ftp.")) {
                return NetworkDriveType.Ftp;
            }
            if (getUrl().contains("webdav")) {
                return NetworkDriveType.Webdav;
            }
        }
        return getNetworkDriveType();
    }

    @Override
    public long getEntityServiceHashCode() {
        return getConnectionHashCode();
    }

    @Override
    public @Nullable NetworkDriveEntity.NetworkDriveService createService(@NotNull Context context) {
        return new NetworkDriveService(context, this);
    }

    @Override
    protected @NotNull String getDevicePrefix() {
        return PREFIX;
    }

    @SneakyThrows
    @NotNull
    private NetworkClient createNetworkClient() {
        return getNetworkDriveType().createNetworkClient.apply(this);
    }

    @Override
    public void assembleActions(UIInputBuilder uiInputBuilder) {

    }

    @RequiredArgsConstructor
    public enum NetworkDriveType {
        Auto(arg -> {
            throw new IllegalArgumentException("Unable to detect protocol for url: " + arg.getUrl());
        }),
        Ftp(FtpSocketClient::new),
        Webdav(WebdavSocketClient::new),
        Samba(SmbSocketClient::new);

        private final ThrowingFunction<NetworkDriveEntity, NetworkClient, Exception> createNetworkClient;
    }

    public interface NetworkFile {

        String getName();

        default boolean isDirectory() {
            return true;
        }

        default Long getSize() {
            return null;
        }

        default Long getModified() {
            return null;
        }
    }

    public interface NetworkClient extends Closeable {

        NetworkClient connect(boolean localPassive);

        InputStream getInputStream(@NotNull String id) throws IOException;

        void mkdir(@NotNull String id) throws IOException;

        boolean put(@NotNull InputStream inputStream, @NotNull String id) throws IOException;

        boolean rename(final String from, final String to) throws IOException;

        NetworkFile getFile(@NotNull String id) throws IOException;

        List<? extends NetworkFile> getAllFiles(@NotNull String parentId) throws IOException;

        default boolean deleteDir(@NotNull String id) throws IOException {
            return deleteFile(id);
        }

        boolean deleteFile(@NotNull String id) throws IOException;
    }

    public static class NetworkDriveService extends ServiceInstance<NetworkDriveEntity> {

        public NetworkDriveService(Context context, NetworkDriveEntity entity) {
            super(context, entity, true, "NetworkDrive");
        }

        @Override
        protected void initialize() {
            testServiceWithSetStatus();
        }

        @Override
        public void testService() {
            try (var client = entity.createNetworkClient()) {
                client.connect(false);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void destroy(boolean forRestart, Exception ex) {

        }
    }
}
