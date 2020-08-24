package org.touchhome.app.setting.console.ssh;

import org.touchhome.bundle.api.setting.BundleConsoleSettingPlugin;

public class ConsoleSshBlinkCursorSetting implements BundleConsoleSettingPlugin<Boolean> {

    @Override
    public SettingType getSettingType() {
        return SettingType.Boolean;
    }

    @Override
    public int order() {
        return 500;
    }

    @Override
    public String[] pages() {
        return new String[]{"ssh"};
    }
}
