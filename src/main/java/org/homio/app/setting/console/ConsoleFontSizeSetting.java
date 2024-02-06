package org.homio.app.setting.console;

import org.homio.api.console.ConsolePlugin;
import org.homio.api.console.ConsolePlugin.RenderType;
import org.homio.api.setting.SettingPluginSlider;
import org.homio.api.setting.console.ConsoleSettingPlugin;

public class ConsoleFontSizeSetting implements ConsoleSettingPlugin<Integer>, SettingPluginSlider {

    @Override
    public int defaultValue() {
        return 16;
    }

    @Override
    public int getMin() {
        return 6;
    }

    @Override
    public int getMax() {
        return 20;
    }

    @Override
    public int order() {
        return 1000;
    }

    @Override
    public ConsolePlugin.RenderType[] renderTypes() {
        return new ConsolePlugin.RenderType[]{ConsolePlugin.RenderType.lines, RenderType.comm};
    }
}
