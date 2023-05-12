package org.homio.app.setting;

import org.homio.bundle.api.setting.SettingPluginButton;

public class SendBroadcastSetting implements SettingPluginButton {

    @Override
    public int order() {
        return 0;
    }

    @Override
    public String getConfirmMsg() {
        return null;
    }

    @Override
    public String getIcon() {
        return "fas fa-play";
    }
}
