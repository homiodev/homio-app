package org.touchhome.app.setting;

import org.touchhome.bundle.api.setting.SettingPluginButton;

public class SendBroadcastSetting implements SettingPluginButton {

    @Override
    public int order() {
        return 0;
    }

    @Override
    public String getIcon() {
        return "fas fa-play";
    }
}
