package org.homio.app.setting.system.db;

import org.homio.api.Context;
import org.homio.api.model.Icon;
import org.homio.api.setting.SettingPluginButton;
import org.homio.api.ui.field.action.ActionInputParameter;
import org.homio.app.setting.CoreSettingPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;

import java.util.List;

public class SystemDatabaseSetting
        implements CoreSettingPlugin<JSONObject>, SettingPluginButton {

    @Override
    public @NotNull GroupKey getGroupKey() {
        return GroupKey.system;
    }

    @Override
    public int order() {
        return 2500;
    }

    @Override
    public String getInputParametersDialogTitle() {
        return "NEW_DATABASE";
    }

    @Override
    public @Nullable String getConfirmTitle() {
        return "TITLE.NEW_DATABASE";
    }

    @Override
    public @Nullable String getConfirmMsg() {
        return "";
    }

    @Override
    public List<ActionInputParameter> getInputParameters(Context context, String value) {
        return List.of(ActionInputParameter.text("URL", "jdbc:postgresql://<host>:<port>/<database>?user=<username>&password=<password>"));
    }

    @Override
    public @Nullable Icon getIcon() {
        return new Icon("fas fa-database", "#C926C9");
    }
}
