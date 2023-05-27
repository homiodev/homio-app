package org.homio.app.setting.console;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import org.homio.api.EntityContext;
import org.homio.api.console.ConsolePlugin;
import org.homio.api.model.OptionModel;
import org.homio.api.setting.SettingPluginOptions;
import org.homio.api.setting.console.ConsoleSettingPlugin;
import org.homio.api.ui.field.UIFieldType;
import org.json.JSONObject;

public class ConsoleRefreshContentPeriodSetting
    implements ConsoleSettingPlugin<Integer>, SettingPluginOptions<Integer> {

    @Override
    public UIFieldType getSettingType() {
        return UIFieldType.SelectBox;
    }

    @Override
    public String getDefaultValue() {
        return "0";
    }

    @Override
    public Class<Integer> getType() {
        return Integer.class;
    }

    @Override
    public String getIcon() {
        return "far fa-clock";
    }

    @Override
    public Collection<OptionModel> getOptions(EntityContext entityContext, JSONObject params) {
        return new ArrayList<>(
            Arrays.asList(
                OptionModel.of("0", "time.NEVER"),
                OptionModel.of("5", "time.SEC_5"),
                OptionModel.of("10", "time.SEC_10"),
                OptionModel.of("30", "time.SEC_30"),
                OptionModel.of("60", "time.SEC_60")));
    }

    @Override
    public int order() {
        return 700;
    }

    @Override
    public boolean acceptConsolePluginPage(ConsolePlugin consolePlugin) {
        return consolePlugin.hasRefreshIntervalSetting();
    }
}
