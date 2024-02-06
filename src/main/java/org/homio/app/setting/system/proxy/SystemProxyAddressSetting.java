package org.homio.app.setting.system.proxy;

import org.homio.api.setting.SettingPluginTextInput;
import org.homio.app.setting.CoreSettingPlugin;
import org.jetbrains.annotations.NotNull;

public class SystemProxyAddressSetting
    implements CoreSettingPlugin<String>, SettingPluginTextInput {

    @Override
    public @NotNull GroupKey getGroupKey() {
        return GroupKey.system;
    }

    @Override
    public @NotNull String getSubGroupKey() {
        return "PROXY";
    }

    @Override
    public int order() {
        return 100;
    }

    @Override
    public @NotNull String getDefaultValue() {
        return "";
    }
}
