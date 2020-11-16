package org.touchhome.app.setting.console;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.touchhome.app.rest.ConsoleController;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.console.ConsolePlugin;
import org.touchhome.bundle.api.json.Option;

import org.touchhome.bundle.api.setting.BundleSettingPluginText;
import org.touchhome.bundle.api.setting.console.BundleConsoleSettingPlugin;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class ConsoleVisibleTabsSetting implements BundleConsoleSettingPlugin<String>, BundleSettingPluginText {

    @Override
    public int order() {
        return 0;
    }

    @Override
    public Collection<Option> loadAvailableValues(EntityContext entityContext) {
        Map<String, Option> result = new HashMap<>();
        result.put("logs", Option.key("logs").json(json -> json.put("available", true)));
        Map<String, ConsolePlugin<?>> map = entityContext.getBean(ConsoleController.class).getConsolePluginsMap();
        for (Map.Entry<String, ConsolePlugin<?>> entry : map.entrySet()) {
            String parentTab = entry.getValue().getParentTab();
            if (StringUtils.isNotEmpty(parentTab)) {
                result.putIfAbsent(parentTab, Option.key(parentTab));
                if (!result.get(parentTab).getJson().has("children")) {
                    result.get(parentTab).getJson().put("children", new JSONArray());
                }
                result.get(parentTab).getJson().getJSONArray("children")
                        .put(new JSONObject().put("subTab", entry.getKey()).put("available", entry.getValue().isEnabled()));
            } else {
                result.put(entry.getKey(), Option.key(entry.getKey()).json(json -> json.put("available", entry.getValue().isEnabled())));
            }
        }
        return result.values();
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
