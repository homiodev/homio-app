package org.homio.app.setting.console.lines;

import org.homio.bundle.api.console.ConsolePlugin;
import org.homio.bundle.api.setting.SettingPluginBoolean;
import org.homio.bundle.api.setting.console.ConsoleSettingPlugin;

public class ConsoleShowLineNumberSetting
        implements ConsoleSettingPlugin<Boolean>, SettingPluginBoolean {

    @Override
    public int order() {
        return 800;
    }

    @Override
    public boolean defaultValue() {
        return true;
    }

    @Override
    public ConsolePlugin.RenderType[] renderTypes() {
        return new ConsolePlugin.RenderType[] {
            ConsolePlugin.RenderType.lines, ConsolePlugin.RenderType.comm
        };
    }
}
