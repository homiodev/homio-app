package org.homio.app.setting.workspace;

import org.homio.api.ui.field.UIFieldType;
import org.homio.app.setting.CoreSettingPlugin;

public class WorkspaceToolboxColorSetting implements CoreSettingPlugin<String> {

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
        return "#F9F9F9";
    }

    @Override
    public UIFieldType getSettingType() {
        return UIFieldType.ColorPicker;
    }

    @Override
    public int order() {
        return 300;
    }

    @Override
    public boolean isReverted() {
        return true;
    }
}
