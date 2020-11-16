package org.touchhome.app.setting.console.ssh;


import org.touchhome.bundle.api.setting.BundleSettingPluginBoolean;
import org.touchhome.bundle.api.setting.console.BundleConsoleSettingPlugin;

public class ConsoleSshBlinkCursorSetting implements BundleConsoleSettingPlugin<Boolean>, BundleSettingPluginBoolean {

    @Override
    public int order() {
        return 500;
    }

    @Override
    public String[] pages() {
        return new String[]{"ssh"};
    }
}
