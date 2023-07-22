package org.homio.app.setting.system;

import static org.homio.api.util.Constants.DANGER_COLOR;

import org.homio.api.EntityContext;
import org.homio.api.entity.UserEntity;
import org.homio.api.model.Icon;
import org.homio.api.setting.SettingPluginButton;
import org.homio.api.ui.UI.Color;
import org.homio.api.util.ApplicationContextHolder;
import org.homio.app.setting.CoreSettingPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;

public class SystemLogoutButtonSetting
    implements CoreSettingPlugin<JSONObject>, SettingPluginButton {

    @Override
    public @NotNull GroupKey getGroupKey() {
        return GroupKey.system;
    }

    @Override
    public @NotNull Icon getIcon() {
        return new Icon("fas fa-right-from-bracket", DANGER_COLOR);
    }

    @Override
    public boolean isPrimary() {
        return true;
    }

    @Override
    public String getText() {
        UserEntity user = ApplicationContextHolder.getBean(EntityContext.class).getUser();
        if (user != null) {
            return user.getEmail();
        }
        return null;
    }

    @Override
    public int order() {
        return 1;
    }

    @Override
    public String getConfirmMsg() {
        return "W.CONFIRM.LOGOUT";
    }

    @Override
    public String getDialogColor() {
        return Color.ERROR_DIALOG;
    }

    @Override
    public void assertUserAccess(@NotNull EntityContext entityContext, @Nullable UserEntity user) {
        // allow logout for everyone
    }
}
