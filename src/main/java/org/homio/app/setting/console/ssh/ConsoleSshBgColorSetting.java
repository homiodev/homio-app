package org.homio.app.setting.console.ssh;

import org.homio.api.setting.SettingType;
import org.homio.api.setting.console.ConsoleSettingPlugin;
import org.jetbrains.annotations.NotNull;

public class ConsoleSshBgColorSetting implements ConsoleSettingPlugin<String> {

    @Override
    public @NotNull Class<String> getType() {
        return String.class;
    }

    @Override
    public @NotNull String getDefaultValue() {
        return "#363636";
    }

    @Override
    public @NotNull SettingType getSettingType() {
        return SettingType.ColorPicker;
    }

    @Override
    public int order() {
        return 100;
    }

    @Override
    public String[] pages() {
        return new String[]{"ssh"};
    }

    @Override
    public boolean isReverted() {
        return true;
    }
}
