package org.touchhome.app.setting.console.ssh;

import org.touchhome.bundle.api.BundleConsoleSettingPlugin;

public class ConsoleSshFontSizeSetting implements BundleConsoleSettingPlugin<Integer> {

    @Override
    public String getDefaultValue() {
        return "12";
    }

    @Override
    public String[] getAvailableValues() {
        return new String[]{"5", "24", "1"};
    }

    @Override
    public SettingType getSettingType() {
        return SettingType.Slider;
    }

    @Override
    public int order() {
        return 400;
    }

    @Override
    public String[] pages() {
        return new String[]{"ssh"};
    }
}
