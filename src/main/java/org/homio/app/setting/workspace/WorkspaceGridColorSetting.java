package org.homio.app.setting.workspace;

import org.homio.api.setting.SettingType;
import org.homio.api.ui.UI;
import org.homio.app.setting.CoreSettingPlugin;
import org.jetbrains.annotations.NotNull;

public class WorkspaceGridColorSetting implements CoreSettingPlugin<String> {

    @Override
    public @NotNull GroupKey getGroupKey() {
        return GroupKey.workspace;
    }

    @Override
    public @NotNull Class<String> getType() {
        return String.class;
    }

    @Override
    public @NotNull String getDefaultValue() {
        return UI.Color.PRIMARY_COLOR;
    }

    @Override
    public @NotNull SettingType getSettingType() {
        return SettingType.ColorPicker;
    }

    @Override
    public int order() {
        return 200;
    }

    @Override
    public boolean isReverted() {
        return true;
    }
}
