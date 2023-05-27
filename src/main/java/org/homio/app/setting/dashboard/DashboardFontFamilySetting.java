package org.homio.app.setting.dashboard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import org.homio.api.EntityContext;
import org.homio.api.model.OptionModel;
import org.homio.api.setting.SettingPluginOptions;
import org.homio.api.ui.field.UIFieldType;
import org.homio.app.setting.CoreSettingPlugin;
import org.json.JSONObject;

public class DashboardFontFamilySetting
    implements CoreSettingPlugin<String>, SettingPluginOptions<String> {

    @Override
    public GroupKey getGroupKey() {
        return GroupKey.dashboard;
    }

    @Override
    public String getSubGroupKey() {
        return "WIDGET";
    }

    @Override
    public UIFieldType getSettingType() {
        return UIFieldType.SelectBox;
    }

    @Override
    public Class<String> getType() {
        return String.class;
    }

    @Override
    public String getDefaultValue() {
        return "sans-serif";
    }

    @Override
    public Collection<OptionModel> getOptions(EntityContext entityContext, JSONObject params) {
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
