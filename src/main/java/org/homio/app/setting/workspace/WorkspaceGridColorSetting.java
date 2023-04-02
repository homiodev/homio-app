package org.homio.app.setting.workspace;

import org.homio.app.setting.CoreSettingPlugin;
import org.homio.bundle.api.ui.UI;
import org.homio.bundle.api.ui.field.UIFieldType;

public class WorkspaceGridColorSetting implements CoreSettingPlugin<String> {

    @Override
    public GroupKey getGroupKey() {
        return GroupKey.workspace;
    }

    @Override
    public Class<String> getType() {
        return String.class;
    }

    @Override
    public String getDefaultValue() {
        return UI.Color.PRIMARY_COLOR;
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
    public boolean isReverted() {
        return true;
    }
}
