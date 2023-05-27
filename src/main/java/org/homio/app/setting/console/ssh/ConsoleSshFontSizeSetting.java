package org.homio.app.setting.console.ssh;

import org.homio.api.setting.SettingPluginSlider;
import org.homio.api.setting.console.ConsoleSettingPlugin;

public class ConsoleSshFontSizeSetting
        implements ConsoleSettingPlugin<Integer>, SettingPluginSlider {

    @Override
    public Integer getMin() {
        return 5;
    }

    @Override
    public Integer getMax() {
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
        return new String[] {"ssh"};
    }
}
