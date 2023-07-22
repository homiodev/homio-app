package org.homio.app.setting.console.lines;

import org.homio.api.console.ConsolePlugin;
import org.homio.api.setting.SettingPluginBoolean;
import org.homio.api.setting.console.ConsoleSettingPlugin;

public class ConsoleShowClassSetting
    implements ConsoleSettingPlugin<Boolean>, SettingPluginBoolean {

    @Override
    public int order() {
        return 920;
    }

    @Override
    public boolean defaultValue() {
        return true;
    }

    @Override
    public ConsolePlugin.RenderType[] renderTypes() {
        return new ConsolePlugin.RenderType[]{ConsolePlugin.RenderType.lines};
    }
}
