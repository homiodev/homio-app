package org.homio.app.model.entity.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.validator.internal.constraintvalidators.hv.EmailValidator;
import org.homio.api.Context;
import org.homio.api.entity.UserEntity;
import org.homio.api.entity.device.DeviceBaseEntity;
import org.homio.api.entity.log.HasEntityLog;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.Icon;
import org.homio.api.model.Status;
import org.homio.api.ui.UI.Color;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldIgnore;
import org.homio.api.ui.field.action.HasDynamicContextMenuActions;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.util.SecureString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static org.homio.api.util.Constants.ADMIN_ROLE;

@Entity
public abstract class UserBaseEntity extends DeviceBaseEntity
  implements UserEntity, HasEntityLog, HasDynamicContextMenuActions {

  public static Logger log = LogManager.getLogger(UserBaseEntity.class);

  public static void logInfo(String entityID, String message) {
      log.info("{}: {}", entityID, message);
  }

  @Override
  public @Nullable Status.EntityStatus getEntityStatus() {
    return null;
  }

  @UIField(order = 5, required = true, inlineEditWhenEmpty = true, disableEdit = true)
  public @NotNull String getEmail() {
    return Objects.toString(getIeeeAddress(), "");
  }

  public UserBaseEntity setEmail(String value) {
    if (value == null || value.isEmpty() || !new EmailValidator().isValid(value, null)) {
      throw new IllegalArgumentException("W.ERROR.EMAIL_NOT_VALID");
    }
    setIeeeAddress(value);
    return this;
  }

  @UIField(order = 10, required = true, inlineEditWhenEmpty = true, disableEdit = true)
  public SecureString getPassword() {
    return getJsonSecure("pwd");
  }

  public void setPassword(String value) {
    if (value == null || value.length() < 4) {
      throw new IllegalArgumentException("USER.PASSWORD_TOO_SHORT");
    }
    setJsonDataSecure("pwd", value);
  }

  @UIField(order = 10, disableEdit = true)
  public abstract @NotNull UserType getUserType();

  @Override
  public void log(@NotNull String message, @NotNull Level level) {
    log.log(level, getEntityID() + ": " + message);
  }

  @Override
  public void logBuilder(EntityLogBuilder logBuilder) {
    logBuilder.addTopicFilterByEntityID(UserBaseEntity.class);
  }

  @JsonIgnore
  public @NotNull Set<String> getRoles() {
    Set<String> roles = new HashSet<>();
    if (getUserType() == UserType.ADMIN) {
      roles.add(ADMIN_ROLE);
    }
    return roles;
  }

  public boolean matchPassword(String password, PasswordEncoder passwordEncoder) {
    String currentPassword = getPassword().asString();
    return currentPassword.equals(password) || passwordEncoder != null && passwordEncoder.matches(password, currentPassword);
  }

  public void setPassword(String password, PasswordEncoder passwordEncoder) {
    if (passwordEncoder != null) {
      try {
        passwordEncoder.upgradeEncoding(password);
      } catch (Exception ex) {
        password = passwordEncoder.encode(password);
      }
    }
    setPassword(password);
  }

  @Override
  @UIFieldIgnore
  @JsonIgnore
  public @NotNull Status getStatus() {
    return Status.ONLINE;
  }

  @Override
  public void assembleActions(UIInputBuilder uiInputBuilder) {
    UserEntity user = uiInputBuilder.context().user().getLoggedInUser();
    if (user != null && user.isAdmin()) {
      uiInputBuilder.addOpenDialogSelectableButton("CHANGE_PASSWORD", new Icon("fas fa-unlock-keyhole",
          Color.RED), this::changePassword)
        .editDialog(dialogBuilder -> dialogBuilder.addFlex("main", flex -> {
          if (this.getUserType() == UserType.ADMIN) {
            flex.addTextInput("currentPassword", "", true);
          }
          flex.addTextInput("newPassword", "", true);
          flex.addTextInput("repeatNewPassword", "", true);
        }));
      uiInputBuilder.addOpenDialogSelectableButton("CHANGE_EMAIL", new Icon("fas fa-at",
          Color.PRIMARY_COLOR), this::changeEmail)
        .editDialog(dialogBuilder -> dialogBuilder.addFlex("main", flex -> {
          if (this.getUserType() == UserType.ADMIN) {
            flex.addTextInput("currentPassword", "", true);
          }
          flex.addTextInput("email", "", true);
        }));
    }
  }

  @Override
  @UIFieldIgnore
  @JsonIgnore
  public @Nullable String getIeeeAddress() {
    return super.getIeeeAddress();
  }

  @Override
  public String toString() {
    return super.getIeeeAddress();
  }

  private ActionResponseModel changeEmail(Context context, JSONObject json) {
    assertCorrectPassword(context, json);
    setEmail(json.getString("email"));
    context.db().save(setEmail(json.getString("email")));
    return ActionResponseModel.showSuccess("USER.ALTERED");
  }

  private ActionResponseModel changePassword(Context context, JSONObject json) {
    assertCorrectPassword(context, json);
    String newPassword = json.getString("newPassword");
    if (!newPassword.equals(json.getString("repeatNewPassword"))) {
      throw new IllegalArgumentException("USER.PASSWORD_NOT_MATCH");
    }
    if (newPassword.length() < 6) {
      throw new IllegalArgumentException("USER.PASSWORD_TOO_SHORT");
    }
    PasswordEncoder passwordEncoder = context.getBean(PasswordEncoder.class);
    setPassword(newPassword, passwordEncoder);
    context.db().save(this);

    context.ui().dialog().reloadWindow("USER.ALTERED_RELOAD");
    return ActionResponseModel.showSuccess("USER.ALTERED");
  }

  private void assertCorrectPassword(Context context, JSONObject json) {
    if (this.getUserType() == UserType.ADMIN && !matchPassword(json.getString("currentPassword"), context.getBean(PasswordEncoder.class))) {
      throw new IllegalArgumentException("W.ERROR.PASSWORD_NOT_MATCH");
    }
  }

  @Override
  protected void assembleMissingMandatoryFields(@NotNull Set<String> fields) {
    if (getEmail().isEmpty()) {
      fields.add("email");
    }
    if (getPassword().isEmpty()) {
      fields.add("password");
    }
  }

  @Override
  public boolean isDisableDelete() {
    return !context().user().isAdminLoggedUser();
  }

  @Override
  public boolean isDisableEdit() {
    if (context().user().getLoggedInUser() == null) {
      return false;
    }
    return !context().user().isAdminLoggedUser();
  }

  @Override
  @UIFieldIgnore
  @JsonIgnore
  public Set<String> getHideForUsers() {
    return super.getHideForUsers();
  }

  @Override
  @UIFieldIgnore
  @JsonIgnore
  public Set<String> getDisableEditForUsers() {
    return super.getDisableEditForUsers();
  }
}
