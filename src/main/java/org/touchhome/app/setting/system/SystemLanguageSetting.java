package org.touchhome.app.setting.system;

import lombok.RequiredArgsConstructor;
import org.touchhome.app.setting.SettingPlugin;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.json.Option;

import java.util.List;

public class SystemLanguageSetting implements SettingPlugin<String> {

    @Override
    public GroupKey getGroupKey() {
        return GroupKey.system;
    }

    @Override
    public SettingType getSettingType() {
        return SettingType.SelectBox;
    }

    @Override
    public List<Option> loadAvailableValues(EntityContext entityContext) {
        return Option.enumList(Lang.class);
    }

    @Override
    public String getDefaultValue() {
        return Lang.en.name();
    }

    @Override
    public int order() {
        return 600;
    }

    @RequiredArgsConstructor
    public enum Lang {
        en("English");

        private final String description;

        @Override
        public String toString() {
            return description;
        }
    }
}
