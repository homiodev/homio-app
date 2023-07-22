package org.homio.app.setting.console;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import org.homio.api.EntityContext;
import org.homio.api.console.ConsolePlugin;
import org.homio.api.model.Icon;
import org.homio.api.model.OptionModel;
import org.homio.api.setting.SettingPluginOptions;
import org.homio.api.setting.console.ConsoleSettingPlugin;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

public class ConsoleRefreshContentPeriodSetting
    implements ConsoleSettingPlugin<Integer>, SettingPluginOptions<Integer> {

    @Override
    public @NotNull String getDefaultValue() {
        return "0";
    }

    @Override
    public @NotNull Class<Integer> getType() {
        return Integer.class;
    }

    @Override
    public Icon getIcon() {
        return new Icon("fas fa-clock");
    }

    @Override
    public @NotNull Collection<OptionModel> getOptions(EntityContext entityContext, JSONObject params) {
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
