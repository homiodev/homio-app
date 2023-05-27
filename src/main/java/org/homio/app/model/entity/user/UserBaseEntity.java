package org.homio.app.model.entity.user;

import static org.homio.api.util.Constants.ADMIN_ROLE;
import static org.homio.api.util.Constants.GUEST_ROLE;
import static org.homio.api.util.Constants.PRIVILEGED_USER_ROLE;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import java.util.HashSet;
import java.util.Set;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.validator.internal.constraintvalidators.hv.EmailValidator;
import org.homio.api.EntityContext;
import org.homio.api.entity.UserEntity;
import org.homio.api.entity.types.IdentityEntity;
import org.homio.api.exception.ProhibitedExecution;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.HasEntityLog;
import org.homio.api.model.Status;
import org.homio.api.ui.UI.Color;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldIgnore;
import org.homio.api.ui.field.action.HasDynamicContextMenuActions;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.util.SecureString;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.springframework.security.crypto.password.PasswordEncoder;

@Entity
public abstract class UserBaseEntity<T extends UserBaseEntity> extends IdentityEntity<T>
    implements UserEntity, HasEntityLog,
    HasDynamicContextMenuActions {

    public static final String LOG_RESOURCE = "ROLE_LOG";
    public static final String LOG_RESOURCE_AUTHORIZE = "hasRole('" + LOG_RESOURCE + "')";
    public static final String SSH_RESOURCE = "ROLE_SSH";
    public static final String SSH_RESOURCE_AUTHORIZE = "hasRole('" + SSH_RESOURCE + "')";
    public static final String MQTT_RESOURCE = "ROLE_MQTT";
    public static final String FILE_MANAGER_RESOURCE = "ROLE_FILE_MANAGER";
    public static final String FILE_MANAGER_RESOURCE_AUTHORIZE = "hasRole('" + FILE_MANAGER_RESOURCE + "')";
    private static final Set<String> RESOURCES = new HashSet<>();
    public static Logger log = LogManager.getLogger(UserBaseEntity.class);

    static {
        RESOURCES.add(LOG_RESOURCE);
        RESOURCES.add(SSH_RESOURCE);
        RESOURCES.add(MQTT_RESOURCE);
        RESOURCES.add(FILE_MANAGER_RESOURCE);
    }

    public static void registerResource(String resource) {
        RESOURCES.add(resource);
    }

    @UIField(order = 5, required = true, inlineEditWhenEmpty = true)
    public String getEmail() {
        return getIeeeAddress();
    }

    public UserBaseEntity setEmail(String value) {
        if (value == null || value.isEmpty() || !new EmailValidator().isValid(value, null)) {
            throw new IllegalArgumentException("W.ERROR.EMAIL_NOT_VALID");
        }
        setIeeeAddress(value);
        return this;
    }

    @UIField(order = 10, required = true, inlineEditWhenEmpty = true)
    public SecureString getPassword() {
        return getJsonSecure("pwd");
    }

    public void setPassword(String value) {
        if (value == null || value.length() < 6) {
            throw new IllegalArgumentException("USER.PASSWORD_TOO_SHORT");
        }
        setJsonData("pwd", value);
    }

    @UIField(order = 10, inlineEdit = true)
    public @NotNull String getLang() {
        return getJsonData("lang", "en");
    }

    public void setLang(String value) {
        setJsonData("lang", value);
    }

    @JsonIgnore
    public abstract @NotNull UserType getUserType();

    @Override
    public void log(@NotNull String message, @NotNull Level level) {
        log.log(level, getEntityID() + ": " + message);
    }

    @Override
    public void logBuilder(EntityLogBuilder logBuilder) {
        logBuilder.addTopicFilterByEntityID("org.homio.app.model.entity.user");
    }

    public @NotNull Set<String> getRoles() {
        Set<String> roles = new HashSet<>();
        roles.add(GUEST_ROLE);
        switch (getUserType()) {
            case ADMIN:
                roles.add(ADMIN_ROLE);
                roles.add(PRIVILEGED_USER_ROLE);
                roles.addAll(RESOURCES);
                break;
            case PRIVILEGED:
                roles.add(PRIVILEGED_USER_ROLE);
                break;
        }
        return roles;
    }

    @Override
    public boolean isDisableEdit() {
        return true;
    }

    public boolean matchPassword(String password, PasswordEncoder passwordEncoder) {
        String currentPassword = getPassword().asString();
        return currentPassword.equals(password) || passwordEncoder != null && passwordEncoder.matches(password, currentPassword);
    }

    public T setPassword(String password, PasswordEncoder passwordEncoder) {
        if (passwordEncoder != null) {
            try {
                passwordEncoder.upgradeEncoding(password);
            } catch (Exception ex) {
                password = passwordEncoder.encode(password);
            }
        }
        setPassword(password);
        return (T) this;
    }

    @Override
    @UIFieldIgnore
    @JsonIgnore
    public Status getStatus() {
        throw new ProhibitedExecution();
    }

    @Override
    public void assembleActions(UIInputBuilder uiInputBuilder) {
        UserEntity user = uiInputBuilder.getEntityContext().getUser();
        if (user != null && user.isAdmin()) {
            uiInputBuilder.addOpenDialogSelectableButton("CHANGE_PASSWORD", "fas fa-unlock-keyhole",
                              Color.RED, null, this::changePassword)
                          .editDialog(dialogBuilder -> dialogBuilder.addFlex("main", flex -> {
                              if (this.getUserType() == UserType.ADMIN) {
                                  flex.addTextInput("field.currentPassword", "", true);
                              }
                              flex.addTextInput("field.newPassword", "", true);
                              flex.addTextInput("field.repeatNewPassword", "", true);
                          }));
            uiInputBuilder.addOpenDialogSelectableButton("CHANGE_EMAIL", "fas fa-at",
                              Color.PRIMARY_COLOR, null, this::changeEmail)
                          .editDialog(dialogBuilder -> dialogBuilder.addFlex("main", flex -> {
                              if (this.getUserType() == UserType.ADMIN) {
                                  flex.addTextInput("field.currentPassword", "", true);
                              }
                              flex.addTextInput("field.email", "", true);
                          }));
        }
    }

    @Override
    @UIFieldIgnore
    @JsonIgnore
    public String getIeeeAddress() {
        return super.getIeeeAddress();
    }

    @Override
    public String toString() {
        return super.getIeeeAddress();
    }

    public static void logInfo(String entityID, String message) {
        log.info(entityID + ": " + message);
    }

    private ActionResponseModel changeEmail(EntityContext entityContext, JSONObject json) {
        assertCorrectPassword(entityContext, json);
        setEmail(json.getString("field.email"));
        entityContext.save(setEmail(json.getString("field.email")));
        return ActionResponseModel.showSuccess("USER.ALTERED");
    }

    private ActionResponseModel changePassword(EntityContext entityContext, JSONObject json) {
        assertCorrectPassword(entityContext, json);
        String newPassword = json.getString("field.newPassword");
        if (!newPassword.equals(json.getString("field.repeatNewPassword"))) {
            throw new IllegalArgumentException("USER.PASSWORD_NOT_MATCH");
        }
        if (newPassword.length() < 6) {
            throw new IllegalArgumentException("USER.PASSWORD_TOO_SHORT");
        }
        PasswordEncoder passwordEncoder = entityContext.getBean(PasswordEncoder.class);
        entityContext.save(setPassword(newPassword, passwordEncoder));

        entityContext.ui().reloadWindow("USER.ALTERED_RELOAD");
        return ActionResponseModel.showSuccess("USER.ALTERED");
    }

    private void assertCorrectPassword(EntityContext entityContext, JSONObject json) {
        if (this.getUserType() == UserType.ADMIN && !matchPassword(json.getString("field.currentPassword"), entityContext.getBean(PasswordEncoder.class))) {
            throw new IllegalArgumentException("W.ERROR.PASSWORD_NOT_MATCH");
        }
    }
}
