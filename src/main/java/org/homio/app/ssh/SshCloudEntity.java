package org.homio.app.ssh;

import static org.homio.api.ui.field.action.UIActionInput.Type.text;
import static org.homio.api.ui.field.action.UIActionInput.Type.textarea;
import static org.homio.api.util.Constants.PRIMARY_DEVICE;
import static org.homio.app.ssh.SshGenericEntity.PublicKeyAuthSign;
import static org.homio.app.ssh.SshGenericEntity.execDeletePrivateKey;
import static org.homio.app.ssh.SshGenericEntity.execUploadPrivateKey;
import static org.homio.app.ssh.SshGenericEntity.updateSSHData;

import com.sshtools.client.SshClient;
import com.sshtools.common.publickey.SshPrivateKeyFile;
import com.sshtools.common.publickey.SshPrivateKeyFileFactory;
import jakarta.persistence.Entity;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.resource.beans.container.internal.NoSuchBeanException;
import org.homio.api.Context;
import org.homio.api.entity.log.HasEntityLog;
import org.homio.api.entity.types.IdentityEntity;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.Icon;
import org.homio.api.service.CloudProviderService;
import org.homio.api.ui.UI.Color;
import org.homio.api.ui.UISidebarChildren;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldPort;
import org.homio.api.ui.field.UIFieldSlider;
import org.homio.api.ui.field.action.HasDynamicContextMenuActions;
import org.homio.api.ui.field.action.UIActionInput;
import org.homio.api.ui.field.action.UIContextMenuAction;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.ui.field.selection.UIFieldBeanSelection;
import org.homio.api.util.DataSourceUtil;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.service.cloud.CloudService;
import org.homio.app.service.cloud.SshTunnelCloudProviderService;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.util.Set;

@Log4j2
@Entity
@UISidebarChildren(icon = "fas fa-cloud", color = "#644DAB")
public class SshCloudEntity extends IdentityEntity implements
        CloudProviderService.SshCloud<SshCloudEntity>, HasEntityLog, HasDynamicContextMenuActions {

    public static SshCloudEntity ensureEntityExists(ContextImpl context) {
        SshCloudEntity entity = context.db().getEntity(SshCloudEntity.class, PRIMARY_DEVICE);
        if (entity == null) {
            entity = new SshCloudEntity()
                    .setHostname("ssh.homio.org")
                    .setProvider(StringUtils.uncapitalize(SshTunnelCloudProviderService.class.getSimpleName()))
                    .setSyncUrl("https://homio.org/server/sync")
                    .setPort(2222)
                    .setPrimary(true);
            entity.setEntityID(PRIMARY_DEVICE);
            entity.setName("Homio cloud");
            entity.setJsonData("dis_del", true);
            context.db().save(entity);
        }
        return entity;
    }

    @UIField(order = 1, hideInEdit = true, disableEdit = true)
    @UIFieldGroup(order = 5, value = "SSH")
    public boolean isPrimary() {
        return getJsonData("primary", false);
    }

    public SshCloudEntity setPrimary(boolean value) {
        setJsonData("primary", value);
        return this;
    }

    @UIField(order = 2, required = true)
    @UIFieldBeanSelection(CloudProviderService.class)
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
    public CloudProviderService<SshCloudEntity> getCloudProviderService(@NotNull Context context) {
        try {
            try {
                return context.getBean(DataSourceUtil.getSelection(getProvider()).getValue(), CloudProviderService.class);
            } catch (NoSuchBeanException ne) {
                log.warn("Unable to find ssh cloud provider: {}", getProvider());
                return context.getBean(SshTunnelCloudProviderService.class);
            }
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
    protected void assembleMissingMandatoryFields(@NotNull Set<String> fields) {
        if(getUser().isEmpty()) {
            fields.add("user");
        }
        if(getHostname().isEmpty()) {
            fields.add("hostname");
        }
        if(getProvider().isEmpty()) {
            fields.add("provider");
        }
    }

    @Override
    protected @NotNull String getDevicePrefix() {
        return "ssh-cloud";
    }

    @Override
    public void beforeDelete() {
        if (isPrimary()) {
            throw new IllegalStateException("W.ERROR.DELETE_CLOUD_PRIMARY_SSH");
        }
    }

    @UIContextMenuAction(value = "SET_CLOUD_PRIMARY", icon = "fas fa-star", iconColor = Color.PRIMARY_COLOR)
    public ActionResponseModel setPrimary(Context context) {
        if (!this.isPrimary()) {
            for (SshCloudEntity sshCloudEntity : context.db().findAll(SshCloudEntity.class)) {
                if (sshCloudEntity.isPrimary()) {
                    sshCloudEntity.setPrimary(false);
                    context.db().save(sshCloudEntity);
                }
            }
            context.db().save(setPrimary(true));
            return ActionResponseModel.success();
        }
        return ActionResponseModel.showInfoAlreadyDone();
    }

    @UIContextMenuAction(value = "UNSET_CLOUD_PRIMARY", icon = "fas fa-star-half-stroke")
    public ActionResponseModel unsetPrimary(Context context) {
        if (this.isPrimary()) {
            context.db().save(setPrimary(false));
            return ActionResponseModel.success();
        }
        return ActionResponseModel.showInfoAlreadyDone();
    }

    @SneakyThrows
    @UIContextMenuAction(value = "UPLOAD_PRIVATE_KEY", icon = "fas fa-upload", inputs = {
            @UIActionInput(name = "privateKey", type = textarea),
            @UIActionInput(name = "passphrase", type = text)
    })
    public ActionResponseModel uploadPrivateKey(Context context, JSONObject params) {
        return execUploadPrivateKey(this, context, params);
    }

    @UIContextMenuAction(value = "DELETE_PRIVATE_KEY", icon = "fas fa-trash-can", inputs = {
            @UIActionInput(name = "passphrase", type = text)
    })
    public ActionResponseModel deletePrivateKey(Context context, JSONObject params) {
        return execDeletePrivateKey(this, context, params);
    }

    @UIContextMenuAction(value = "CONNECT", icon = "fas fa-rss")
    public ActionResponseModel connect(Context context) {
        context.getBean(CloudService.class).restart(this);
        return ActionResponseModel.fired();
    }

    @UIContextMenuAction(value = "STOP", icon = "fas fa-circle-stop")
    public ActionResponseModel stop(Context context) {
        context.getBean(CloudService.class).stop(this);
        return ActionResponseModel.success();
    }

    @SneakyThrows
    public void uploadAndSavePrivateKey(Context context, String privateKey, String passphrase) {
        SshPrivateKeyFile kf = SshPrivateKeyFileFactory.parse(privateKey.getBytes());
        updateSSHData(this, passphrase, kf);
        context.db().save(this);
    }

    @Override
    public long getChangesHashCode() {
        return getEntityID().hashCode() + getJsonDataHashCode("host", "port", "user", "pk_sign", "prv_key", "key_pwd");
    }

    @Override
    public void logBuilder(EntityLogBuilder builder) {
        builder.addTopic(SshClient.class);
    }

    @Override
    public void assembleActions(UIInputBuilder uiInputBuilder) {
        if (!isHasPrivateKey() || "Auth fail".equals(getStatusMessage())) {
            CloudProviderService<SshCloudEntity> service = getCloudProviderService(uiInputBuilder.context());
            if (service != null) {
                uiInputBuilder.addSelectableButton("sync", new Icon("fas fa-right-to-bracket", Color.GREEN),
                    (context, params) -> service.sync());
            }
        }
    }

    /*@UIContextMenuAction(value = "CLOUD_SYNC", icon = "fas fa-right-to-bracket", iconColor = Color.RED, inputs = {
        @UIActionInput(name = "email", type = Type.text),
        @UIActionInput(name = "password", type = Type.text),
        @UIActionInput(name = "passphrase", type = Type.text)
    })
    public ActionResponseModel sync(Context context, ObjectNode params) {
        val service = context.getBean(SshTunnelCloudProviderService.class);
        service.handleSync(context, params);
        return ActionResponseModel.success();
    }

    @Override
    public void assembleActions(UIInputBuilder uiInputBuilder) {
        if(getCloudProviderService() instanceof SshTunnelCloudProviderService) {

        }
    }*/
}
