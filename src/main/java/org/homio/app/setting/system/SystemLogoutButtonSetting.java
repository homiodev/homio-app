package org.homio.app.setting.system;

import static org.homio.bundle.api.util.Constants.DANGER_COLOR;

import org.homio.app.setting.CoreSettingPlugin;
import org.homio.bundle.api.setting.SettingPluginButton;
import org.json.JSONObject;

public class SystemLogoutButtonSetting
    implements CoreSettingPlugin<JSONObject>, SettingPluginButton {

    @Override
    public GroupKey getGroupKey() {
        return GroupKey.system;
    }

    @Override
    public String getIconColor() {
        return DANGER_COLOR;
    }

    @Override
    public String getIcon() {
        return "fas fa-right-from-bracket";
    }

    @Override
    public int order() {
        return 1;
    }

    @Override
    public String getConfirmMsg() {
        return "W.CONFIRM.LOGOUT";
    }
}
