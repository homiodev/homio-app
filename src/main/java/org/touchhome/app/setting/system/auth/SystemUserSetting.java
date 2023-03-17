package org.touchhome.app.setting.system.auth;

import java.util.Arrays;
import java.util.List;
import org.json.JSONObject;
import org.touchhome.app.setting.CoreSettingPlugin;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.entity.UserEntity;
import org.touchhome.bundle.api.setting.SettingPluginButton;
import org.touchhome.bundle.api.ui.field.action.ActionInputParameter;

public class SystemUserSetting implements CoreSettingPlugin<JSONObject>, SettingPluginButton {

    @Override
    public GroupKey getGroupKey() {
        return GroupKey.system;
    }

    @Override
    public String getSubGroupKey() {
        return "AUTH";
    }

    @Override
    public boolean transientState() {
        return true;
    }

    @Override
    public String getIcon() {
        return "fas fa-user";
    }

    @Override
    public List<ActionInputParameter> getInputParameters(EntityContext entityContext, String value) {
        UserEntity user = entityContext.getUserRequire();
        return Arrays.asList(
            ActionInputParameter.email("email", user.getUserId()),
            ActionInputParameter.text("name", user.getName(), "pattern:.{3}"),
            ActionInputParameter.password("currentPassword", ""),
            ActionInputParameter.password("newPassword", ""),
            ActionInputParameter.password("repeatNewPassword", "")
        );
    }

    @Override
    public int order() {
        return 500;
    }
}
