package org.homio.app.setting.console.lines;

import java.util.Collection;
import org.homio.api.Context;
import org.homio.api.console.ConsolePlugin;
import org.homio.api.model.Icon;
import org.homio.api.model.OptionModel;
import org.homio.api.setting.SettingPluginOptions;
import org.homio.api.setting.SettingType;
import org.homio.api.setting.console.ConsoleSettingPlugin;
import org.json.JSONObject;

public class ConsoleLinesTextWrapSetting
        implements ConsoleSettingPlugin<String>, SettingPluginOptions<String> {

    @Override
    public SettingType getSettingType() {
        return SettingType.SelectBoxButton;
    }

    @Override
    public Class<String> getType() {
        return String.class;
    }

    @Override
    public Icon getIcon() {
        return new Icon("fas fa-text-width");
    }

    @Override
    public Collection<OptionModel> getOptions(Context context, JSONObject params) {
        return OptionModel.list("nowrap", "pre", "pre-wrap", "break-spaces");
    }

    @Override
    public int order() {
        return 800;
    }

    @Override
    public ConsolePlugin.RenderType[] renderTypes() {
        return new ConsolePlugin.RenderType[]{ConsolePlugin.RenderType.lines};
    }
}
