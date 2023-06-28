package org.homio.app.setting.console.ssh;

import org.homio.api.setting.console.ConsoleSettingPlugin;
import org.homio.api.ui.field.UIFieldType;
import org.jetbrains.annotations.NotNull;

public class ConsoleSshFgColorSetting implements ConsoleSettingPlugin<String> {

    @Override
    public @NotNull Class<String> getType() {
        return String.class;
    }

    @Override
    public @NotNull String getDefaultValue() {
        return "#D9D9D9";
    }

    @Override
    public @NotNull UIFieldType getSettingType() {
        return UIFieldType.ColorPicker;
    }

    @Override
    public int order() {
        return 300;
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
