package org.touchhome.app.setting.console.log;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.Level;
import org.touchhome.bundle.api.BundleConsoleSettingPlugin;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.json.Option;

import java.util.List;

public class ConsoleLogLevelSetting implements BundleConsoleSettingPlugin<ConsoleLogLevelSetting.LogLevel> {

    @Override
    public SettingType getSettingType() {
        return SettingType.SelectBoxButton;
    }

    @Override
    public LogLevel parseValue(EntityContext entityContext, String value) {
        return LogLevel.valueOf(value);
    }

    @Override
    public String getDefaultValue() {
        return LogLevel.Info.toString();
    }

    @Override
    public String getIcon() {
        return "fas fa-layer-group";
    }

    @Override
    public List<Option> loadAvailableValues(EntityContext entityContext) {
        return Option.list(LogLevel.class);
    }

    @Override
    public int order() {
        return 700;
    }

    @RequiredArgsConstructor
    public enum LogLevel {
        Debug(Level.DEBUG),
        Info(Level.INFO),
        Warn(Level.WARN),
        Error(Level.ERROR);

        @Getter
        private final Level level;
    }

    @Override
    public String[] pages() {
        return new String[]{"log"};
    }
}
