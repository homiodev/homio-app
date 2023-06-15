package org.homio.app.setting.system;

import org.homio.api.model.Icon;
import org.homio.api.setting.SettingPluginButton;
import org.homio.app.setting.CoreSettingPlugin;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

public class SystemClearCacheButtonSetting
    implements CoreSettingPlugin<JSONObject>, SettingPluginButton {

    @Override
    public GroupKey getGroupKey() {
        return GroupKey.system;
    }

    @Override
    public String getConfirmMsg() {
        return "W.CONFIRM.CLEAR_CACHE";
    }

    @Override
    public String getDialogColor() {
        return "#672E18";
    }

    @Override
    public @NotNull Icon getIcon() {
        return new Icon("fas fa-trash");
    }

    @Override
    public int order() {
        return 200;
    }
}
