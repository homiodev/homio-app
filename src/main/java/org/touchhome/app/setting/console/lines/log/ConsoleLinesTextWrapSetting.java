package org.touchhome.app.setting.console.lines.log;

import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.console.ConsolePlugin;
import org.touchhome.bundle.api.model.OptionModel;
import org.touchhome.bundle.api.setting.SettingPluginOptions;
import org.touchhome.bundle.api.setting.console.ConsoleSettingPlugin;

import java.util.List;

public class ConsoleLinesTextWrapSetting implements ConsoleSettingPlugin<String>, SettingPluginOptions<String> {

    @Override
    public SettingType getSettingType() {
        return SettingType.SelectBoxButton;
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
    public List<OptionModel> getOptions(EntityContext entityContext) {
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
