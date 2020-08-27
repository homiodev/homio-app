package org.touchhome.app.setting.dashboard;

import org.json.JSONObject;
import org.touchhome.app.setting.SettingPlugin;
import org.touchhome.bundle.api.EntityContext;

public class DashboardHorizontalBlockCountSetting implements SettingPlugin {

    @Override
    public GroupKey getGroupKey() {
        return GroupKey.dashboard;
    }

    @Override
    public String getSubGroupKey() {
        return "BLOCKS";
    }

    @Override
    public String getDefaultValue() {
        return "8";
    }

    @Override
    public JSONObject getParameters(EntityContext entityContext, String value) {
        return new JSONObject().put("min", 1).put("max", 10);
    }

    @Override
    public SettingType getSettingType() {
        return SettingType.Slider;
    }

    @Override
    public int order() {
        return 100;
    }
}
