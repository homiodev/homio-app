package org.touchhome.app.setting.console.ssh;

import org.touchhome.bundle.api.setting.BundleConsoleSettingPlugin;

public class ConsoleSshCursorColorSetting implements BundleConsoleSettingPlugin<String> {

    @Override
    public String getDefaultValue() {
        return "#D9D9D9";
    }

    @Override
    public SettingType getSettingType() {
        return SettingType.ColorPicker;
    }

    @Override
    public int order() {
        return 200;
    }

    @Override
    public String[] pages() {
        return new String[]{"ssh"};
    }
}
