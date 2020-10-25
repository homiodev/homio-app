package org.touchhome.app.setting.console.log;

import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.json.Option;
import org.touchhome.bundle.api.setting.BundleConsoleSettingPlugin;

import java.util.List;

public class ConsoleLogTextWrapSetting implements BundleConsoleSettingPlugin<String> {

    @Override
    public SettingType getSettingType() {
        return SettingType.SelectBoxButton;
    }

    @Override
    public String getIcon() {
        return "fas fa-text-width";
    }

    @Override
    public List<Option> loadAvailableValues(EntityContext entityContext) {
        return Option.list("nowrap", "pre", "pre-wrap", "break-spaces");
    }

    @Override
    public int order() {
        return 800;
    }

    @Override
    public String[] pages() {
        return new String[]{"log"};
    }
}
