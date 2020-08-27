package org.touchhome.app.setting.console.ssh;

import org.touchhome.bundle.api.setting.BundleConsoleSettingPlugin;
import org.touchhome.bundle.api.setting.BundleSettingPluginSlider;

public class ConsoleSshFontSizeSetting implements BundleConsoleSettingPlugin<Integer>, BundleSettingPluginSlider {

    @Override
    public int getMin() {
        return 5;
    }

    @Override
    public int getMax() {
        return 24;
    }

    @Override
    public int defaultValue() {
        return 12;
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
