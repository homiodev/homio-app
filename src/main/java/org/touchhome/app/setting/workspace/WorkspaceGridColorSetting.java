package org.touchhome.app.setting.workspace;

import org.touchhome.app.setting.CoreSettingPlugin;
import org.touchhome.bundle.api.ui.field.UIFieldType;

import static org.touchhome.bundle.api.util.TouchHomeUtils.PRIMARY_COLOR;

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
        return PRIMARY_COLOR;
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
