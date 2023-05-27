package org.homio.app.setting.console;

import static org.homio.app.model.entity.user.UserBaseEntity.LOG_RESOURCE;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.homio.api.EntityContext;
import org.homio.api.console.ConsolePlugin;
import org.homio.api.model.OptionModel;
import org.homio.api.setting.SettingPluginOptions;
import org.homio.api.setting.SettingPluginText;
import org.homio.api.setting.console.ConsoleSettingPlugin;
import org.homio.app.manager.common.impl.EntityContextUIImpl;
import org.json.JSONObject;

public class ConsoleVisibleTabsSetting
    implements ConsoleSettingPlugin<String>, SettingPluginText, SettingPluginOptions<String> {

    @Override
    public int order() {
        return 0;
    }

    @Override
    public Collection<OptionModel> getOptions(EntityContext entityContext, JSONObject params) {
        Map<String, OptionModel> result = new HashMap<>();
        result.put("logs", OptionModel.key("logs").setDisabled(!entityContext.accessEnabled(LOG_RESOURCE)));
        Map<String, ConsolePlugin<?>> map = EntityContextUIImpl.consolePluginsMap;
        for (Map.Entry<String, ConsolePlugin<?>> entry : map.entrySet()) {
            if (entry.getKey().equals("icl")) {
                continue;
            }
            String parentTab = entry.getValue().getParentTab();
            if (StringUtils.isNotEmpty(parentTab)) {
                OptionModel parent = result.computeIfAbsent(parentTab, key -> OptionModel.key(parentTab));
                parent.addChild(OptionModel.of(entry.getKey()).setDisabled(!entry.getValue().isEnabled()));
            } else {
                result.put(entry.getKey(), OptionModel.key(entry.getKey()).setDisabled(!entry.getValue().isEnabled()));
            }
        }
        for (String pluginName : EntityContextUIImpl.customConsolePluginNames.keySet()) {
            result.putIfAbsent(pluginName, OptionModel.key(pluginName).setDisabled(true));
        }
        return result.values();
    }

    @Override
    public boolean acceptConsolePluginPage(ConsolePlugin consolePlugin) {
        return false;
    }
}
