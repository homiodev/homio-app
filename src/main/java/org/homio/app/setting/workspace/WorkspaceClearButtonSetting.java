package org.homio.app.setting.workspace;

import org.homio.api.model.Icon;
import org.homio.api.setting.SettingPluginButton;
import org.homio.api.ui.UI.Color;
import org.homio.app.setting.CoreSettingPlugin;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

public class WorkspaceClearButtonSetting
    implements CoreSettingPlugin<JSONObject>, SettingPluginButton {

    @Override
    public @NotNull GroupKey getGroupKey() {
        return GroupKey.workspace;
    }

    @Override
    public @NotNull Icon getIcon() {
        return new Icon("fas fa-trash");
    }

    @Override
    public String getConfirmMsg() {
        return "W.CONFIRM.CLEAR_WORKSPACE";
    }

    @Override
    public String getDialogColor() {
        return Color.ERROR_DIALOG;
    }

    @Override
    public int order() {
        return 999;
    }
}
