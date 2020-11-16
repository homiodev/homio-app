package org.touchhome.app.setting.console.lines;

import org.touchhome.bundle.api.console.ConsolePlugin;

import org.touchhome.bundle.api.setting.BundleSettingPluginBoolean;
import org.touchhome.bundle.api.setting.console.BundleConsoleSettingPlugin;

public class ConsoleStickToLastLineOnOpenSetting implements BundleConsoleSettingPlugin<Boolean>, BundleSettingPluginBoolean {

    @Override
    public int order() {
        return 700;
    }

    @Override
    public ConsolePlugin.RenderType[] renderTypes() {
        return new ConsolePlugin.RenderType[]{ConsolePlugin.RenderType.lines, ConsolePlugin.RenderType.comm};
    }
}
