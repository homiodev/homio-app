package org.homio.app.ssh;

import static com.sshtools.common.publickey.SshKeyPairGenerator.ECDSA;
import static com.sshtools.common.publickey.SshKeyPairGenerator.ED25519;
import static com.sshtools.common.publickey.SshKeyPairGenerator.SSH2_RSA;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.trimToNull;
import static org.homio.api.ui.field.action.UIActionInput.Type.select;
import static org.homio.api.ui.field.action.UIActionInput.Type.text;
import static org.homio.api.ui.field.action.UIActionInput.Type.textarea;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sshtools.client.SshClient;
import com.sshtools.common.publickey.SshKeyPairGenerator;
import com.sshtools.common.publickey.SshKeyUtils;
import com.sshtools.common.publickey.SshPrivateKeyFile;
import com.sshtools.common.publickey.SshPrivateKeyFileFactory;
import com.sshtools.common.publickey.SshPublicKeyFile;
import com.sshtools.common.publickey.SshPublicKeyFileFactory;
import com.sshtools.common.ssh.components.SshKeyPair;
import jakarta.persistence.Entity;
import java.util.Objects;
import java.util.Set;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.homio.api.Context;
import org.homio.api.entity.BaseEntity;
import org.homio.api.entity.storage.BaseFileSystemEntity;
import org.homio.api.entity.types.IdentityEntity;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.FileContentType;
import org.homio.api.model.FileModel;
import org.homio.api.model.Icon;
import org.homio.api.model.OptionModel;
import org.homio.api.service.EntityService;
import org.homio.api.service.ssh.SshBaseEntity;
import org.homio.api.service.ssh.SshProviderService;
import org.homio.api.ui.UISidebarChildren;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldSlider;
import org.homio.api.ui.field.action.UIActionInput;
import org.homio.api.ui.field.action.UIContextMenuAction;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.util.SecureString;
import org.homio.app.ssh.SshGenericEntity.GenericWebSocketService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;

@Entity
@SuppressWarnings("unused")
@UISidebarChildren(icon = "fas fa-terminal", color = "#0088CC")
public class SshGenericEntity extends SshBaseEntity<SshGenericEntity, GenericWebSocketService>
    implements BaseFileSystemEntity<SshGenericFileSystem> {

    @Override
    public void configureOptionModel(@NotNull OptionModel optionModel, @NotNull Context context) {
        String user = getUser();
        optionModel.setDescription((user.isEmpty() ? "" : user + "@") + getHost() + ":" + getPort());
    }

    @UIField(order = 20, inlineEditWhenEmpty = true, required = true)
    @UIFieldGroup("GENERAL")
    public String getHost() {
        return getJsonData("host");
    }

    public void setHost(String value) {
        setJsonData("host", value);
    }

    @UIField(order = 25)
    @UIFieldGroup("GENERAL")
    public int getPort() {
        return getJsonData("port", 22);
    }

    public void setPort(int value) {
        setJsonData("port", value);
    }

    @UIField(order = 30)
    @UIFieldSlider(min = 1, max = 60)
    @UIFieldGroup("GENERAL")
    public int getConnectionTimeout() {
        return getJsonData("ct", 30);
    }

    public void setConnectionTimeout(int timeout) {
        setJsonData("ct", timeout);
    }

    @UIField(order = 1, inlineEditWhenEmpty = true)
    @UIFieldGroup(order = 15, value = "SECURITY", borderColor = "#23ADAB")
    public String getUser() {
        return getJsonData("user", "");
    }

    public void setUser(String value) {
        setJsonData("user", value);
    }

    @UIField(order = 2)
    @UIFieldGroup("SECURITY")
    public SecureString getPassword() {
        return getJsonSecure("pwd");
    }

    public void setPassword(String value) {
        setJsonDataSecure("pwd", value);
    }

    @UIField(order = 3)
    @UIFieldGroup("SECURITY")
    public PublicKeyAuthSign getPublicKeyAuthSign() {
        return getJsonDataEnum("pk_sign", PublicKeyAuthSign.SHA256);
    }

    public void setPublicKeyAuthSign(PublicKeyAuthSign value) {
        setJsonData("pk_sign", value);
    }

    @UIField(order = 4, hideInEdit = true)
    @UIFieldGroup("SECURITY")
    public boolean isHasPrivateKey() {
        return getJsonData().has("prv_key");
    }

    @UIField(order = 5, hideInEdit = true, hideOnEmpty = true)
    @UIFieldGroup("SECURITY")
    public boolean isPrivateKeyPasswordProtected() {
        return getJsonData().has("key_pwd");
    }

    @UIField(order = 6, hideInEdit = true, hideOnEmpty = true)
    @UIFieldGroup("SECURITY")
    public String getFingerprint() {
        return getJsonData("fp");
    }

    @UIField(order = 7, hideInEdit = true, hideOnEmpty = true)
    @UIFieldGroup("SECURITY")
    public String getAlgorithm() {
        return getJsonData("alg");
    }

    @UIField(order = 8, hideInEdit = true, hideOnEmpty = true)
    @UIFieldGroup("SECURITY")
    public String getKeyType() {
        return getJsonData("kt");
    }

    @UIField(order = 9, hideInEdit = true, hideOnEmpty = true)
    @UIFieldGroup("SECURITY")
    public String getKeyComment() {
        return getJsonData("pub_cmn");
    }

    @UIField(order = 1)
    @UIFieldGroup(order = 20, value = "FS", borderColor = "#914991")
    public @NotNull String getFileSystemRoot() {
        return Objects.requireNonNull(getJsonData("fs_root", "/home"));
    }

    public void setFileSystemRoot(String value) {
        setJsonData("fs_root", value);
    }

    @UIField(order = 2)
    @UIFieldGroup("FS")
    public boolean isShowHiddenFiles() {
        return getJsonData("shf", false);
    }

    public void setShowHiddenFiles(boolean value) {
        setJsonData("shf", value);
    }

    @JsonIgnore
    public String getPrivateKeyPassphrase() {
        return trimToNull(getJsonData("key_pwd"));
    }

    @JsonIgnore
    public String getPrivateKey() {
        return getJsonData("prv_key");
    }

    @JsonIgnore
    @SneakyThrows
    public String getPublicKey() {
        return getJsonData("pub_key");
    }

    @SneakyThrows
    @UIContextMenuAction(value = "GENERATE_PRIVATE_KEY", icon = "fab fa-creative-commons-sampling", inputs = {
            @UIActionInput(name = "algorithm", type = select, value = "SSH2_RSA", values = {"SSH2_RSA", "ECDSA", "ED25519"}),
            @UIActionInput(name = "passphrase", type = text),
            @UIActionInput(name = "comment", type = text),
            @UIActionInput(name = "bits", type = select, value = "3072", values = {"1024", "2048", "3072", "4096"})
    })
    public ActionResponseModel generatePrivateKey(Context context, JSONObject params) {
        if (isHasPrivateKey()) {
            return ActionResponseModel.showError("W.ERROR.PRIVATE_KEY_ALREADY_EXISTS");
        }
        String passphrase = trimToNull(params.optString("passphrase"));
        String algorithm = params.optString("algorithm", "SSH2_RSA");
        int bits = params.optInt("bits", 3072);
        String alg = "SSH2_RSA".equals(algorithm) ? SSH2_RSA : "ECDSA".equals(algorithm) ? ECDSA : ED25519;
        context.bgp().runWithProgress("generate-ssh")
                .onFinally(exception -> {
                    if (exception != null) {
                        context.ui().toastr().error("ERROR.SSH_GENERATE");
                    } else {
                        context.ui().toastr().success("ACTION.RESPONSE.SUCCESS_SSH_GENERATE");
                    }
                })
                .execute(progressBar -> {
                    progressBar.progress(10, "PROGRESS.GENERATE_SSH");
                    SshKeyPair keyPair = SshKeyPairGenerator.generateKeyPair(alg, bits);
                    SshPrivateKeyFile kf = SshPrivateKeyFileFactory.create(keyPair, passphrase, params.optString("comment"));
                    updateSSHData(this, passphrase, kf);

                    context.db().save(this);
                });
        return null;
    }

    @UIContextMenuAction(value = "UPLOAD_PRIVATE_KEY", icon = "fas fa-upload", inputs = {
            @UIActionInput(name = "privateKey", type = textarea),
            @UIActionInput(name = "passphrase", type = text)
    })
    public ActionResponseModel uploadPrivateKey(Context context, JSONObject params) {
        return execUploadPrivateKey(this, context, params);
    }

    @SneakyThrows
    public SshClient createSshClient() {
        int connectionTimeout = getConnectionTimeout() * 1000;
        if (isHasPrivateKey()) {
            return new SshClient(getHost(), getPort(), getUser(), connectionTimeout, buildSshKeyPair(this));
        } else if (!getPassword().asString().isEmpty()) {
            return new SshClient(getHost(), getPort(), getUser(), connectionTimeout,
                    getPassword().asString().toCharArray());
        } else {
            return new SshClient(getHost(), getPort(), getUser(), connectionTimeout);
        }
    }

    @SneakyThrows
    public static ActionResponseModel execUploadPrivateKey(IdentityEntity entity, Context context, JSONObject params) {
        if (entity.getJsonData().has("prv_key")) {
            return ActionResponseModel.showError("W.ERROR.PRIVATE_KEY_ALREADY_EXISTS");
        }
        String privateKey = params.getString("privateKey");
        String passphrase = trimToNull(params.optString("passphrase"));
        SshPrivateKeyFile kf = SshPrivateKeyFileFactory.parse(privateKey.getBytes());
        updateSSHData(entity, passphrase, kf);

        context.db().save((BaseEntity) entity);
        return ActionResponseModel.showSuccess("ACTION.SUCCESS");
    }

    @SneakyThrows
    public static void updateSSHData(IdentityEntity entity, String passphrase, SshPrivateKeyFile kf) {
        if (kf.isPassphraseProtected() && passphrase == null) {
            throw new IllegalArgumentException("Key protected with password");
        }
        SshKeyPair keyPair = kf.toKeyPair(passphrase);

        entity.setJsonData("key_pwd", passphrase);
        entity.setJsonData("prv_key", new String(kf.getFormattedKey()));

        SshPublicKeyFile publicKeyFile = SshPublicKeyFileFactory.create(keyPair.getPublicKey(),
                kf.getComment(), SshPublicKeyFileFactory.OPENSSH_FORMAT);

        entity.setJsonData("pub_key", new String(publicKeyFile.getFormattedKey()));
        entity.setJsonData("pub_cmn", kf.getComment());
        entity.setJsonData("fp", keyPair.getPublicKey().getFingerprint());
        entity.setJsonData("alg", keyPair.getPrivateKey().getAlgorithm());
        entity.setJsonData("kt", kf.getType());
    }

    @SneakyThrows
    @UIContextMenuAction(value = "TEST_CONNECTION", icon = "fas fa-flask-vial")
    public ActionResponseModel testConnection(Context context, JSONObject params) {
        getService().testServiceWithSetStatus();
        return getStatus().isOnline() ? ActionResponseModel.success() : ActionResponseModel.showError(getStatusMessage());
    }

    @SneakyThrows
    @UIContextMenuAction(value = "DOWNLOAD_PUBLIC_KEY", icon = "fas fa-download")
    public ActionResponseModel downloadPublicKey(Context context, JSONObject params) {
        if (!isHasPrivateKey()) {
            return ActionResponseModel.showError("ERROR.PRIVATE_KEY_NOT_FOUND");
        }
        String publicKey = getJsonData("pub_key");
        FileModel publicKeyModel = new FileModel("Public key", publicKey, FileContentType.plaintext);
        return ActionResponseModel.showFile(publicKeyModel);
    }

    @SneakyThrows
    @UIContextMenuAction(value = "DELETE_PRIVATE_KEY", icon = "fas fa-trash-can", inputs = {
            @UIActionInput(name = "passphrase", type = text)
    })
    public ActionResponseModel deletePrivateKey(Context context, JSONObject params) {
        return execDeletePrivateKey(this, context, params);
    }

    public static ActionResponseModel execDeletePrivateKey(IdentityEntity entity, Context context, JSONObject params) {
        if (!entity.getJsonData().has("prv_key")) {
            return ActionResponseModel.showError("ERROR.PRIVATE_KEY_NOT_FOUND");
        }
        String passphrase = trimToNull(params.optString("passphrase"));
        if (!Objects.equals(passphrase, trimToNull(entity.getJsonData("key_pwd")))) {
            throw new IllegalArgumentException("Provided passphrase not match");
        }
        entity.setJsonData("key_pwd", null);
        entity.setJsonData("prv_key", null);
        entity.setJsonData("pub_key", null);
        entity.setJsonData("fp", null);
        entity.setJsonData("alg", null);
        entity.setJsonData("kt", null);
        entity.setJsonData("pub_cmn", null);
        context.db().save(entity);
        return ActionResponseModel.showSuccess("ACTION.SUCCESS");
    }

    @SneakyThrows
    public static SshKeyPair buildSshKeyPair(IdentityEntity entity) {
        String passphrase = trimToNull(entity.getJsonData("key_pwd"));
        String privateKey = entity.getJsonData("prv_key");
        SshPrivateKeyFile keyFile = SshPrivateKeyFileFactory.parse(privateKey.getBytes());
        SshKeyPair keyPair = keyFile.toKeyPair(passphrase);
        return switch (entity.getJsonDataEnum("pk_sign", PublicKeyAuthSign.SHA256)) {
            case SHA256 -> SshKeyUtils.makeRSAWithSHA256Signature(keyPair);
            case SHA512 -> SshKeyUtils.makeRSAWithSHA512Signature(keyPair);
            default -> keyPair;
        };
    }

    @Override
    public String getDefaultName() {
        return "Generic SSH";
    }

    @Override
    protected @NotNull String getDevicePrefix() {
        return "ssh-generic";
    }

    @Override
    public @NotNull String getFileSystemAlias() {
        return "SSH[" + getTitle() + "]";
    }

    @Override
    public boolean isShowInFileManager() {
        return true;
    }

    @Override
    public @NotNull Icon getFileSystemIcon() {
        return new Icon("fas fa-road-spikes", "#37A987");
    }

    @Override
    public boolean requireConfigure() {
        return isEmpty(getHost());
    }

    @Override
    public @NotNull SshGenericFileSystem buildFileSystem(@NotNull Context context, int alias) {
        return new SshGenericFileSystem(this, context);
    }

    @Override
    public long getConnectionHashCode() {
        return getEntityServiceHashCode();
    }

    @Override
    public void assembleActions(UIInputBuilder uiInputBuilder) {

    }

    @Override
    public @NotNull Class<GenericWebSocketService> getEntityServiceItemClass() {
        return GenericWebSocketService.class;
    }

    @Override
    public @Nullable GenericWebSocketService createService(@NotNull Context context) {
        return new GenericWebSocketService(context, this);
    }

    @Override
    public @Nullable Set<String> getConfigurationErrors() {
        if(getHost().isEmpty()) {
            return Set.of("ERROR.NO_HOST");
        }
        return null;
    }

    @Override
    public long getEntityServiceHashCode() {
        return getJsonDataHashCode("host", "port", "user", "pwd", "key_pwd", "prv_key", "ct");
    }

    public enum PublicKeyAuthSign {
        SHA1, SHA256, SHA512
    }

    public static class GenericWebSocketService extends EntityService.ServiceInstance<SshGenericEntity> implements SshProviderService<SshGenericEntity> {

        private final SSHServerEndpoint sshServerEndpoint;

        public GenericWebSocketService(Context context, SshGenericEntity entity) {
            super(context, entity, true);
            this.sshServerEndpoint = context.getBean(SSHServerEndpoint.class);
        }

        @Override
        protected void initialize() {
            testServiceWithSetStatus();
        }

        @Override
        public void testService() {
            try (SshClient sshClient = entity.createSshClient()) {
                if (!sshClient.isConnected()) {
                    throw new IllegalStateException("SSH not connected");
                }
                if (!sshClient.isAuthenticated()) {
                    throw new IllegalStateException("SSH not authenticated");
                }
                sshClient.executeCommand("ls");
                // success tested
            } catch (Exception e) {
                if (StringUtils.defaultString(e.getMessage(), "").contains("Task did not succeed")) {
                    e = new RuntimeException("Unknown error during execute 'ls' command");
                }
                throw new RuntimeException(e);
            }
        }

        @Override
        public void destroy(boolean forRestart, Exception ex) {

        }

        @Override
        public SshSession<SshGenericEntity> openSshSession(@NotNull SshGenericEntity entity) {
            return sshServerEndpoint.openSession(entity);
        }

        @Override
        public void execute(@NotNull SshSession sshSession, @NotNull String command) {
            sshServerEndpoint.execute(sshSession, command);
        }

        @Override
        public void resizeSshConsole(@NotNull SshSession sshSession, int cols) {
            sshServerEndpoint.resizeSshConsole(sshSession, cols);
        }

        @Override
        public void closeSshSession(@Nullable SshSession<SshGenericEntity> session) {
            if (session == null) {
                return;
            }
            sshServerEndpoint.closeSession(session);
        }
    }
}
