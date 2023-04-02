package org.homio.app.setting.system;

import static org.homio.bundle.api.util.Constants.DANGER_COLOR;

import java.util.Set;
import org.homio.app.setting.CoreSettingPlugin;
import org.homio.bundle.api.setting.SettingPluginTextSet;
import org.homio.bundle.api.util.Lang;

public class SystemPlaceSetting implements CoreSettingPlugin<Set<String>>, SettingPluginTextSet {

    @Override
    public GroupKey getGroupKey() {
        return GroupKey.system;
    }

    @Override
    public String getIconColor() {
        return DANGER_COLOR;
    }

    @Override
    public int order() {
        return 200;
    }

    @Override
    public String[] defaultValue() {
        return Lang.getServerMessage("DEFAULT_PLACES").split(";");
    }
}
