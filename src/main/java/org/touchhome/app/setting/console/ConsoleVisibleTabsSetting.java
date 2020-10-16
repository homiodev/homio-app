package org.touchhome.app.setting.console;

import org.json.JSONObject;
import org.touchhome.app.rest.ConsoleController;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.console.ConsolePlugin;
import org.touchhome.bundle.api.json.Option;
import org.touchhome.bundle.api.setting.BundleConsoleSettingPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ConsoleVisibleTabsSetting implements BundleConsoleSettingPlugin<String> {

    @Override
    public SettingType getSettingType() {
        return SettingType.Text;
    }

    @Override
    public int order() {
        return 0;
    }

    @Override
    public List<Option> loadAvailableValues(EntityContext entityContext) {
        List<Option> list = new ArrayList<>();
        list.add(Option.key("fake")); // fake need to avoid set setting as empty value, because if empty value that all tabs available
        list.add(Option.key("log"));
        list.add(Option.key("ssh"));
        for (Map.Entry<String, ConsolePlugin> entry : entityContext.getBean(ConsoleController.class).getConsolePluginsMap()
                .entrySet()) {
            list.add(Option.key(entry.getKey()).setJson(new JSONObject().put("available", entry.getValue().isEnabled()).toString()));
        }
        return list;
    }

    @Override
    public String[] pages() {
        return new String[0];
    }

    @Override
    public boolean acceptConsolePluginPage(ConsolePlugin consolePlugin) {
        return false;
    }
}