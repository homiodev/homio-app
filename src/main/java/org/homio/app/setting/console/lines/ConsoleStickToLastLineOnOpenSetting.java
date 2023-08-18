package org.homio.app.setting.console.lines;

import org.homio.api.console.ConsolePlugin;
import org.homio.api.setting.SettingPluginBoolean;
import org.homio.api.setting.console.ConsoleSettingPlugin;

public class ConsoleStickToLastLineOnOpenSetting
        implements ConsoleSettingPlugin<Boolean>, SettingPluginBoolean {

    @Override
    public int order() {
        return 700;
    }

    @Override
    public ConsolePlugin.RenderType[] renderTypes() {
        return new ConsolePlugin.RenderType[]{
                ConsolePlugin.RenderType.lines, ConsolePlugin.RenderType.comm
        };
    }
}
