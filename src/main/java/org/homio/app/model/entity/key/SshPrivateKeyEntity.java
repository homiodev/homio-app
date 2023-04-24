package org.homio.app.model.entity.key;

import com.sshtools.common.publickey.SshKeyUtils;
import com.sshtools.common.publickey.SshPrivateKeyFile;
import com.sshtools.common.publickey.SshPrivateKeyFileFactory;
import com.sshtools.common.ssh.components.SshKeyPair;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import javax.persistence.Entity;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;
import org.homio.bundle.api.EntityContext;
import org.homio.bundle.api.entity.types.KeyEntity;
import org.homio.bundle.api.model.ActionResponseModel;
import org.homio.bundle.api.model.FileContentType;
import org.homio.bundle.api.model.FileModel;
import org.homio.bundle.api.ui.field.UIField;
import org.homio.bundle.api.ui.field.action.UIActionInput;
import org.homio.bundle.api.ui.field.action.UIActionInput.Type;
import org.homio.bundle.api.ui.field.action.UIContextMenuAction;
import org.json.JSONObject;

@Entity
@Accessors(chain = true)
public class SshPrivateKeyEntity extends KeyEntity<SshPrivateKeyEntity> {

    public static final String PREFIX = "sshprv_";

    @Override
    public String getDefaultName() {
        return "Ssh private key";
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    @UIField(order = 1, hideInEdit = true)
    public String getFingerprint() {
        return getJsonData("fp");
    }

    @UIField(order = 2, hideInEdit = true)
    public String getAlgorithm() {
        return getJsonData("alg");
    }

    @UIField(order = 3, hideInEdit = true)
    public boolean isPasswordProtected() {
        return StringUtils.isNotEmpty(getJsonData("pwd"));
    }

    @SneakyThrows
    @UIContextMenuAction(value = "CONTEXT.ACTION.upload_key", icon = "fas fa-upload", inputs = {
        @UIActionInput(name = "privateKey", type = Type.text),
        @UIActionInput(name = "password", type = Type.text)
    })
    public ActionResponseModel uploadCredentials(EntityContext entityContext, JSONObject params) {
        String privateKey = params.getString("privateKey");
        String password = params.getString("password");
        SshPrivateKeyFile keyFile = SshPrivateKeyFileFactory.parse(privateKey.getBytes(StandardCharsets.UTF_8));
        if (keyFile.isPassphraseProtected() && StringUtils.isEmpty(password)) {
            throw new IllegalArgumentException("Key protected with password");
        }
        // verify password
        SshKeyPair keyPair = keyFile.toKeyPair(password);
        if (StringUtils.isNotEmpty(password)) {
            setJsonData("pwd", password);
        }
        setJsonData("key", privateKey);
        setJsonData("fp", keyPair.getPublicKey().getFingerprint());
        setJsonData("alg", keyPair.getPrivateKey().getAlgorithm());
        entityContext.save(this);
        return ActionResponseModel.showSuccess("ACTION.SUCCESS");
    }

    @SneakyThrows
    @UIContextMenuAction(value = "CONTEXT.ACTION.download_public_key", icon = "fas fa-download", inputs = {
        @UIActionInput(name = "comment", type = Type.text)
    })
    public ActionResponseModel downloadPublicKey(EntityContext entityContext, JSONObject params) {
        String privateKey = getJsonData("key");
        SshPrivateKeyFile keyFile = SshPrivateKeyFileFactory.parse(privateKey.getBytes(StandardCharsets.UTF_8));
        SshKeyPair keyPair = keyFile.toKeyPair(getJsonData("password"));

        String publicKey = SshKeyUtils.getFormattedKey(keyPair.getPublicKey(), params.getString("comment"));
        FileModel publicKeyModel = new FileModel("Public key", publicKey, FileContentType.plaintext, true);
        return ActionResponseModel.showFiles(Collections.singleton(publicKeyModel));
    }
}
