package org.homio.app.setting.console.lines;

import java.util.Collection;
import org.homio.bundle.api.EntityContext;
import org.homio.bundle.api.console.ConsolePlugin;
import org.homio.bundle.api.model.OptionModel;
import org.homio.bundle.api.setting.SettingPluginOptions;
import org.homio.bundle.api.setting.console.ConsoleSettingPlugin;
import org.homio.bundle.api.ui.field.UIFieldType;
import org.json.JSONObject;

public class ConsoleLinesTextWrapSetting
        implements ConsoleSettingPlugin<String>, SettingPluginOptions<String> {

    @Override
    public UIFieldType getSettingType() {
        return UIFieldType.SelectBoxButton;
    }

    @Override
    public Class<String> getType() {
        return String.class;
    }

    @Override
    public String getIcon() {
        return "fas fa-text-width";
    }

    @Override
    public Collection<OptionModel> getOptions(EntityContext entityContext, JSONObject params) {
        return OptionModel.list("nowrap", "pre", "pre-wrap", "break-spaces");
    }

    @Override
    public int order() {
        return 800;
    }

    @Override
    public ConsolePlugin.RenderType[] renderTypes() {
        return new ConsolePlugin.RenderType[] {ConsolePlugin.RenderType.lines};
    }
}
