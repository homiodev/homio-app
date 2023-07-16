package org.homio.app.ssh;

import static org.homio.api.ui.field.action.UIActionInput.Type.text;
import static org.homio.api.ui.field.action.UIActionInput.Type.textarea;
import static org.homio.app.ssh.SshGenericEntity.execDeletePrivateKey;
import static org.homio.app.ssh.SshGenericEntity.execUploadPrivateKey;
import static org.homio.app.ssh.SshGenericEntity.updateSSHData;

import com.sshtools.common.publickey.SshPrivateKeyFile;
import com.sshtools.common.publickey.SshPrivateKeyFileFactory;
import jakarta.persistence.Entity;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.homio.api.EntityContext;
import org.homio.api.entity.log.HasEntityLog;
import org.homio.api.entity.types.IdentityEntity;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.service.CloudProviderService;
import org.homio.api.ui.UI.Color;
import org.homio.api.ui.UISidebarChildren;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldPort;
import org.homio.api.ui.field.UIFieldSlider;
import org.homio.api.ui.field.action.UIActionInput;
import org.homio.api.ui.field.action.UIContextMenuAction;
import org.homio.api.ui.field.selection.UIFieldBeanSelection;
import org.homio.api.util.DataSourceUtil;
import org.homio.api.util.DataSourceUtil.DataSourceContext;
import org.homio.app.manager.common.EntityContextImpl;
import org.homio.app.service.cloud.CloudService;
import org.homio.app.service.cloud.SshTunnelCloudProviderService;
import org.homio.app.ssh.SshGenericEntity.PublicKeyAuthSign;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

@Log4j2
@Entity
@UISidebarChildren(icon = "fas fa-cloud", color = "#644DAB")
public class SshCloudEntity extends IdentityEntity<SshCloudEntity> implements
    CloudProviderService.SshCloud<SshCloudEntity>, HasEntityLog
    /*, HasDynamicContextMenuActions*/ {

    public static final String PREFIX = "sshcloud_";
    private static final String DEFAULT_CLOUD_ENTITY_ID = PREFIX + "primary";

    public static SshCloudEntity ensureEntityExists(EntityContextImpl entityContext) {
        SshCloudEntity entity = entityContext.getEntity(DEFAULT_CLOUD_ENTITY_ID);
        if (entity == null) {
            entity = new SshCloudEntity()
                .setEntityID(DEFAULT_CLOUD_ENTITY_ID)
                .setName("Homio cloud")
                .setHostname("homio.org")
                .setProvider(DataSourceUtil.buildBeanSource(SshTunnelCloudProviderService.class))
                .setSyncUrl("https://homio.org/server/sync")
                .setPort(2222)
                .setPrimary(true);
            entity.setJsonData("dis_del", true);
            entity.setJsonData("dis_edit", true);
            entityContext.save(entity);
        }
        return entity;
    }

    @UIField(order = 1, hideInEdit = true, disableEdit = true)
    @UIFieldGroup(value = "SSH", order = 5)
    public boolean isPrimary() {
        return getJsonData("primary", false);
    }

    public SshCloudEntity setPrimary(boolean value) {
        setJsonData("primary", value);
        return this;
    }

    @UIField(order = 2, required = true)
    @UIFieldBeanSelection(value = CloudProviderService.class, lazyLoading = true)
    @UIFieldGroup("SSH")
    public String getProvider() {
        return getJsonData("prv");
    }

    public SshCloudEntity setProvider(String value) {
        setJsonData("prv", value);
        return this;
    }

    @UIField(order = 3, required = true)
    @UIFieldGroup("SSH")
    public String getHostname() {
        return getJsonData("host");
    }

    public SshCloudEntity setHostname(String value) {
        setJsonData("host", value);
        return this;
    }

    @UIField(order = 4, required = true)
    @UIFieldPort(min = 22)
    @UIFieldGroup("SSH")
    public int getPort() {
        return getJsonData("port", 22);
    }

    public SshCloudEntity setPort(int value) {
        setJsonData("port", value);
        return this;
    }

    @UIField(order = 5)
    @UIFieldSlider(min = 1, max = 60)
    @UIFieldGroup("SSH")
    public int getConnectionTimeout() {
        return getJsonData("ci", 10);
    }

    public SshCloudEntity setConnectionTimeout(int value) {
        setJsonData("ci", value);
        return this;
    }

    @UIField(order = 6)
    @UIFieldGroup("SSH")
    public String getSyncUrl() {
        return getJsonData("sync");
    }

    public SshCloudEntity setSyncUrl(String value) {
        setJsonData("sync", value);
        return this;
    }

    @UIField(order = 7)
    @UIFieldGroup("SSH")
    public boolean isEnableWatchdog() {
        return true;
    }

    public boolean isRestartOnFailure() {
        return isEnableWatchdog();
    }

    @UIField(order = 2, required = true, inlineEditWhenEmpty = true)
    @UIFieldGroup(order = 7, value = "SECURITY", borderColor = "#23ADAB")
    public String getUser() {
        return getJsonData("user");
    }

    public SshCloudEntity setUser(String value) {
        setJsonData("user", value);
        return this;
    }

    @UIField(order = 3)
    @UIFieldGroup("SECURITY")
    public PublicKeyAuthSign getPublicKeyAuthSign() {
        return getJsonDataEnum("pk_sign", PublicKeyAuthSign.SHA256);
    }

    public void setPublicKeyAuthSign(PublicKeyAuthSign value) {
        setJsonData("pk_sign", value);
    }

    @UIField(order = 4, disableEdit = true, hideInEdit = true)
    @UIFieldGroup("SECURITY")
    public boolean isHasPrivateKey() {
        return getJsonData().has("prv_key");
    }

    @Override
    public CloudProviderService<SshCloudEntity> getCloudProviderService(@NotNull EntityContext entityContext) {
        try {
            DataSourceContext sourceContext = DataSourceUtil.getSource(entityContext, getProvider());
            CloudProviderService service = (CloudProviderService) sourceContext.getSource();
            return service == null ? entityContext.getBean(SshTunnelCloudProviderService.class) : service;
        } catch (Exception ex) {
            log.error("Unable to fetch provider for entity: {}", this);
        }
        return null;
    }

    @Override
    public String getDefaultName() {
        return "Cloud SSH";
    }

    @Override
    public @NotNull String getEntityPrefix() {
        return PREFIX;
    }

    @Override
    public void beforeDelete(@NotNull EntityContext entityContext) {
        if (isPrimary()) {
            throw new IllegalStateException("W.ERROR.DELETE_CLOUD_PRIMARY_SSH");
        }
    }

    @UIContextMenuAction(value = "SET_CLOUD_PRIMARY", icon = "fas fa-star", iconColor = Color.PRIMARY_COLOR)
    public ActionResponseModel setPrimary(EntityContext entityContext) {
        if (!this.isPrimary()) {
            for (SshCloudEntity sshCloudEntity : entityContext.findAll(SshCloudEntity.class)) {
                if (sshCloudEntity.isPrimary()) {
                    sshCloudEntity.setPrimary(false);
                    entityContext.save(sshCloudEntity);
                }
            }
            entityContext.save(setPrimary(true));
            return ActionResponseModel.success();
        }
        return ActionResponseModel.showInfoAlreadyDone();
    }

    @UIContextMenuAction(value = "UNSET_CLOUD_PRIMARY", icon = "fas fa-star-half-stroke")
    public ActionResponseModel unsetPrimary(EntityContext entityContext) {
        if (this.isPrimary()) {
            entityContext.save(setPrimary(false));
            return ActionResponseModel.success();
        }
        return ActionResponseModel.showInfoAlreadyDone();
    }

    @SneakyThrows
    @UIContextMenuAction(value = "UPLOAD_PRIVATE_KEY", icon = "fas fa-upload", inputs = {
        @UIActionInput(name = "privateKey", type = textarea),
        @UIActionInput(name = "passphrase", type = text)
    })
    public ActionResponseModel uploadPrivateKey(EntityContext entityContext, JSONObject params) {
        return execUploadPrivateKey(this, entityContext, params);
    }

    @SneakyThrows
    @UIContextMenuAction(value = "DELETE_PRIVATE_KEY", icon = "fas fa-trash-can", inputs = {
        @UIActionInput(name = "passphrase", type = text)
    })
    public ActionResponseModel deletePrivateKey(EntityContext entityContext, JSONObject params) {
        return execDeletePrivateKey(this, entityContext, params);
    }

    @SneakyThrows
    @UIContextMenuAction(value = "CONNECT", icon = "fas fa-rss")
    public ActionResponseModel connect(EntityContext entityContext) {
        entityContext.getBean(CloudService.class).restart(this);
        return null;
    }

    @SneakyThrows
    @UIContextMenuAction(value = "STOP", icon = "fas fa-circle-stop")
    public ActionResponseModel stop(EntityContext entityContext) {
        entityContext.getBean(CloudService.class).stop(this);
        return ActionResponseModel.success();
    }

    @SneakyThrows
    public void uploadAndSavePrivateKey(EntityContext entityContext, String privateKey, String passphrase) {
        SshPrivateKeyFile kf = SshPrivateKeyFileFactory.parse(privateKey.getBytes());
        updateSSHData(this, passphrase, kf);
        entityContext.save(this);
    }

    @Override
    public long getChangesHashCode() {
        return getEntityID().hashCode() + getJsonDataHashCode("host", "port", "user", "pk_sign", "prv_key", "key_pwd");
    }

    @Override
    public void logBuilder(EntityLogBuilder builder) {
        builder.addTopic("com.ssh.maverick");
    }

    /*@UIContextMenuAction(value = "CLOUD_SYNC", icon = "fas fa-right-to-bracket", iconColor = Color.RED, inputs = {
        @UIActionInput(name = "field.email", type = Type.text),
        @UIActionInput(name = "field.password", type = Type.text),
        @UIActionInput(name = "field.passphrase", type = Type.text)
    })
    public ActionResponseModel sync(EntityContext entityContext, ObjectNode params) {
        val service = entityContext.getBean(SshTunnelCloudProviderService.class);
        service.handleSync(entityContext, params);
        return ActionResponseModel.success();
    }

    @Override
    public void assembleActions(UIInputBuilder uiInputBuilder) {
        if(getCloudProviderService() instanceof SshTunnelCloudProviderService) {

        }
    }*/
}
