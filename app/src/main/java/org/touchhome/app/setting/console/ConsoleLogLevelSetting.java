package org.touchhome.app.setting.console;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.Level;
import org.touchhome.bundle.api.BundleSettingPlugin;
import org.touchhome.bundle.api.json.Option;

import java.util.List;

public class ConsoleLogLevelSetting implements BundleSettingPlugin<ConsoleLogLevelSetting.LogLevel> {

    @Override
    public SettingType getSettingType() {
        return SettingType.SelectBoxButton;
    }

    @Override
    public LogLevel parseValue(String value) {
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
    public List<Option> loadAvailableValues() {
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
}
