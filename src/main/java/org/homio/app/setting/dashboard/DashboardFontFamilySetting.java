package org.homio.app.setting.dashboard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import org.homio.api.EntityContext;
import org.homio.api.model.OptionModel;
import org.homio.api.setting.SettingPluginOptions;
import org.homio.app.setting.CoreSettingPlugin;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

public class DashboardFontFamilySetting
        implements CoreSettingPlugin<String>, SettingPluginOptions<String> {

    @Override
    public @NotNull GroupKey getGroupKey() {
        return GroupKey.dashboard;
    }

    @Override
    public @NotNull String getSubGroupKey() {
        return "WIDGET";
    }

    @Override
    public @NotNull Class<String> getType() {
        return String.class;
    }

    @Override
    public @NotNull String getDefaultValue() {
        return "sans-serif";
    }

    @Override
    public @NotNull Collection<OptionModel> getOptions(EntityContext entityContext, JSONObject params) {
        return new ArrayList<>(
                Arrays.asList(
                        OptionModel.of("inherit"),
                        OptionModel.of("serif"),
                        OptionModel.of("sans-serif"),
                        OptionModel.of("monospace"),
                        OptionModel.of("cursive"),
                        OptionModel.of("'FontAwesome'")));
    }

    @Override
    public int order() {
        return 700;
    }
}
