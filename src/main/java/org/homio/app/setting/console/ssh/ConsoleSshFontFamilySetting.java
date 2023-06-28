package org.homio.app.setting.console.ssh;

import static java.lang.String.format;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.homio.api.EntityContext;
import org.homio.api.model.OptionModel;
import org.homio.api.setting.SettingPluginOptions;
import org.homio.api.setting.console.ConsoleSettingPlugin;
import org.homio.api.ui.field.UIFieldType;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

public class ConsoleSshFontFamilySetting
    implements ConsoleSettingPlugin<String>, SettingPluginOptions<String> {

    @Override
    public @NotNull UIFieldType getSettingType() {
        return UIFieldType.SelectBox;
    }

    @Override
    public @NotNull Class<String> getType() {
        return String.class;
    }

    @Override
    public @NotNull String getDefaultValue() {
        return "DejaVu Sans Mono";
    }

    @Override
    public @NotNull Collection<OptionModel> getOptions(EntityContext entityContext, JSONObject params) {
        List<OptionModel> result = new ArrayList<>();
        for (String fontFamily : new String[]{"DejaVu Sans Mono", "Liberation Mono", "Cascadia Code", "Courier New", "Ubuntu Mono"}) {
            result.add(OptionModel.of(fontFamily,
                format("<div style=\"font-family:%s\">%s</div>", fontFamily, fontFamily)));
        }
        return result;
    }

    @Override
    public int order() {
        return 450;
    }

    @Override
    public String[] pages() {
        return new String[]{"ssh"};
    }
}
