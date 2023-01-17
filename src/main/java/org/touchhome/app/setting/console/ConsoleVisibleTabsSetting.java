package org.touchhome.app.setting.console;

import static org.touchhome.common.util.CommonUtils.OBJECT_MAPPER;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.touchhome.app.manager.common.impl.EntityContextUIImpl;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.console.ConsolePlugin;
import org.touchhome.bundle.api.model.OptionModel;
import org.touchhome.bundle.api.setting.SettingPluginOptions;
import org.touchhome.bundle.api.setting.SettingPluginText;
import org.touchhome.bundle.api.setting.console.ConsoleSettingPlugin;

public class ConsoleVisibleTabsSetting
        implements ConsoleSettingPlugin<String>, SettingPluginText, SettingPluginOptions<String> {

    @Override
    public int order() {
        return 0;
    }

    @Override
    public Collection<OptionModel> getOptions(EntityContext entityContext, JSONObject params) {
        Map<String, OptionModel> result = new HashMap<>();
        result.put("logs", OptionModel.key("logs").json(json -> json.put("available", true)));
        Map<String, ConsolePlugin<?>> map = EntityContextUIImpl.consolePluginsMap;
        for (Map.Entry<String, ConsolePlugin<?>> entry : map.entrySet()) {
            if (entry.getKey().equals("icl")) {
                continue;
            }
            String parentTab = entry.getValue().getParentTab();
            if (StringUtils.isNotEmpty(parentTab)) {
                result.putIfAbsent(parentTab, OptionModel.key(parentTab));
                result.get(parentTab)
                        .json(
                                jsonNodes ->
                                        jsonNodes.putIfAbsent(
                                                "children", OBJECT_MAPPER.createArrayNode()));
                result.get(parentTab)
                        .getJson()
                        .withArray("children")
                        .add(
                                OBJECT_MAPPER
                                        .createObjectNode()
                                        .put("subTab", entry.getKey())
                                        .put("available", entry.getValue().isEnabled()));
            } else {
                result.put(
                        entry.getKey(),
                        OptionModel.key(entry.getKey())
                                .json(json -> json.put("available", entry.getValue().isEnabled())));
            }
        }
        for (String pluginName : EntityContextUIImpl.customConsolePluginNames) {
            result.putIfAbsent(
                    pluginName,
                    OptionModel.key(pluginName).json(json -> json.put("available", false)));
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
