package org.touchhome.app.setting.console.lines;

import org.touchhome.bundle.api.console.ConsolePlugin;
import org.touchhome.bundle.api.setting.SettingPluginBoolean;
import org.touchhome.bundle.api.setting.console.ConsoleSettingPlugin;

public class ConsoleShowDateSetting implements ConsoleSettingPlugin<Boolean>, SettingPluginBoolean {

    @Override
    public int order() {
        return 900;
    }

    @Override
    public boolean defaultValue() {
        return true;
    }

    @Override
    public ConsolePlugin.RenderType[] renderTypes() {
        return new ConsolePlugin.RenderType[] {ConsolePlugin.RenderType.lines};
    }
}
