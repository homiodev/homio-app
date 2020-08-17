package org.touchhome.app.setting.console.ssh;

import org.json.JSONObject;
import org.touchhome.bundle.api.BundleConsoleSettingPlugin;
import org.touchhome.bundle.api.EntityContext;

public class ConsoleSshFontSizeSetting implements BundleConsoleSettingPlugin<Integer> {

    @Override
    public String getDefaultValue() {
        return "12";
    }

    @Override
    public JSONObject getParameters(EntityContext entityContext, String value) {
        return new JSONObject().put("min", 5).put("max", 24).put("step", 1);
    }

    @Override
    public SettingType getSettingType() {
        return SettingType.Slider;
    }

    @Override
    public int order() {
        return 400;
    }

    @Override
    public String[] pages() {
        return new String[]{"ssh"};
    }
}
