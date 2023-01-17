package org.touchhome.app.setting.console.ssh;

import org.touchhome.bundle.api.setting.console.ConsoleSettingPlugin;
import org.touchhome.bundle.api.ui.field.UIFieldType;

public class ConsoleSshCursorColorSetting implements ConsoleSettingPlugin<String> {

    @Override
    public Class<String> getType() {
        return String.class;
    }

    @Override
    public String getDefaultValue() {
        return "#D9D9D9";
    }

    @Override
    public UIFieldType getSettingType() {
        return UIFieldType.ColorPicker;
    }

    @Override
    public int order() {
        return 200;
    }

    @Override
    public String[] pages() {
        return new String[] {"ssh"};
    }

    @Override
    public boolean isReverted() {
        return true;
    }
}
