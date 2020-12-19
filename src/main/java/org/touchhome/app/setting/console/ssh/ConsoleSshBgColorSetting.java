package org.touchhome.app.setting.console.ssh;


import org.touchhome.bundle.api.setting.console.ConsoleSettingPlugin;

public class ConsoleSshBgColorSetting implements ConsoleSettingPlugin<String> {

    @Override
    public Class<String> getType() {
        return String.class;
    }

    @Override
    public String getDefaultValue() {
        return "#363636";
    }

    @Override
    public SettingType getSettingType() {
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
