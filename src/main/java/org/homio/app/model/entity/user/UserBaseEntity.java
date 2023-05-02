package org.homio.app.model.entity.user;

import static org.homio.bundle.api.util.Constants.ADMIN_ROLE;
import static org.homio.bundle.api.util.Constants.GUEST_ROLE;
import static org.homio.bundle.api.util.Constants.PRIVILEGED_USER_ROLE;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Set;
import javax.persistence.Entity;
import org.hibernate.validator.internal.constraintvalidators.hv.EmailValidator;
import org.homio.bundle.api.EntityContext;
import org.homio.bundle.api.entity.UserEntity;
import org.homio.bundle.api.entity.types.IdentityEntity;
import org.homio.bundle.api.exception.ProhibitedExecution;
import org.homio.bundle.api.model.ActionResponseModel;
import org.homio.bundle.api.model.Status;
import org.homio.bundle.api.ui.UI.Color;
import org.homio.bundle.api.ui.field.UIField;
import org.homio.bundle.api.ui.field.UIFieldIgnore;
import org.homio.bundle.api.ui.field.action.HasDynamicContextMenuActions;
import org.homio.bundle.api.ui.field.action.v1.UIInputBuilder;
import org.homio.bundle.api.util.SecureString;
import org.json.JSONObject;
import org.springframework.security.crypto.password.PasswordEncoder;

@Entity
public abstract class UserBaseEntity<T extends UserBaseEntity> extends IdentityEntity<T>
    implements UserEntity,
    HasDynamicContextMenuActions {

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
    public String getLang() {
        return getJsonData("lang", "en");
    }

    public void setLang(String value) {
        setJsonData("lang", value);
    }

    @JsonIgnore
    public abstract UserType getUserType();

    public Set<String> getRoles() {
        switch (getUserType()) {
            case ADMIN:
                return Set.of(ADMIN_ROLE, PRIVILEGED_USER_ROLE, GUEST_ROLE);
            case PRIVILEGED:
                return Set.of(PRIVILEGED_USER_ROLE, GUEST_ROLE);
            default:
                return Set.of(GUEST_ROLE);
        }
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
