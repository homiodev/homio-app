package org.touchhome.app.setting.console.ssh;

import org.touchhome.bundle.api.BundleConsoleSettingPlugin;

public class ConsoleSshFgColorSetting implements BundleConsoleSettingPlugin<String> {

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
        return 300;
    }

    @Override
    public String[] pages() {
        return new String[]{"ssh"};
    }
}
