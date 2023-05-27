package org.homio.app.setting.system;

import static org.homio.api.util.Constants.DANGER_COLOR;

import org.homio.api.setting.SettingPluginButton;
import org.homio.app.setting.CoreSettingPlugin;
import org.json.JSONObject;

public class SystemClearWorkspaceButtonSetting
        implements CoreSettingPlugin<JSONObject>, SettingPluginButton {

    @Override
    public GroupKey getGroupKey() {
        return GroupKey.system;
    }

    @Override
    public String getSubGroupKey() {
        return "WORKSPACE";
    }

    @Override
    public String getIconColor() {
        return DANGER_COLOR;
    }

    @Override
    public String getConfirmMsg() {
        return "W.CONFIRM.CONFIRM_CLEAR_WORKSPACE";
    }

    @Override
    public String getIcon() {
        return "fas fa-trash";
    }

    @Override
    public int order() {
        return 100;
    }
}
