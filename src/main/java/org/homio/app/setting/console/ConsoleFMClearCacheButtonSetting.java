package org.homio.app.setting.console;

import org.homio.api.model.Icon;
import org.homio.api.setting.SettingPluginButton;
import org.homio.api.setting.console.ConsoleSettingPlugin;
import org.homio.api.ui.UI.Color;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

public class ConsoleFMClearCacheButtonSetting implements ConsoleSettingPlugin<JSONObject>, SettingPluginButton {

    @Override
    public @NotNull Icon getIcon() {
        return new Icon("fas fa-brush");
    }

    @Override
    public int order() {
        return 100;
    }

    @Override
    public String getConfirmMsg() {
        return "W.CONFIRM.FM_CLEAR";
    }

    @Override
    public String getDialogColor() {
        return Color.ERROR_DIALOG;
    }

    @Override
    public String[] pages() {
        return new String[]{"fm"};
    }
}
