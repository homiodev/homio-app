package org.touchhome.app.setting.console.lines;

import org.touchhome.bundle.api.console.ConsolePlugin;
import org.touchhome.bundle.api.setting.SettingPluginBoolean;
import org.touchhome.bundle.api.setting.console.ConsoleSettingPlugin;

public class ConsoleStickToLastLineOnOpenSetting
        implements ConsoleSettingPlugin<Boolean>, SettingPluginBoolean {

    @Override
    public int order() {
        return 700;
    }

    @Override
    public ConsolePlugin.RenderType[] renderTypes() {
        return new ConsolePlugin.RenderType[] {
            ConsolePlugin.RenderType.lines, ConsolePlugin.RenderType.comm
        };
    }
}
