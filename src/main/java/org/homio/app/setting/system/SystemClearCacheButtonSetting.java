package org.homio.app.setting.system;

import static org.homio.api.util.Constants.DANGER_COLOR;

import org.homio.api.setting.SettingPluginButton;
import org.homio.app.setting.CoreSettingPlugin;
import org.json.JSONObject;

public class SystemClearCacheButtonSetting
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
    public String getConfirmMsg() {
        return "W.CONFIRM.CONFIRM_CLEAR_CACHE";
    }

    @Override
    public String getIcon() {
        return "fas fa-trash";
    }

    @Override
    public int order() {
        return 200;
    }
}
