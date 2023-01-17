package org.touchhome.app.setting.console;

import org.touchhome.bundle.api.console.ConsolePlugin;
import org.touchhome.bundle.api.console.ConsolePlugin.RenderType;
import org.touchhome.bundle.api.setting.SettingPluginSlider;
import org.touchhome.bundle.api.setting.console.ConsoleSettingPlugin;

public class ConsoleFontSizeSetting implements ConsoleSettingPlugin<Integer>, SettingPluginSlider {

    @Override
    public int defaultValue() {
        return 16;
    }

    @Override
    public Integer getMin() {
        return 6;
    }

    @Override
    public Integer getMax() {
        return 20;
    }

    @Override
    public int order() {
        return 1000;
    }

    @Override
    public ConsolePlugin.RenderType[] renderTypes() {
        return new ConsolePlugin.RenderType[] {ConsolePlugin.RenderType.lines, RenderType.comm};
    }
}
