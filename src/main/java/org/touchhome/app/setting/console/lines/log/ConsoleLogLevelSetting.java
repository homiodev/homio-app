package org.touchhome.app.setting.console.lines.log;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.Level;
import org.touchhome.bundle.api.setting.SettingPluginOptionsEnum;
import org.touchhome.bundle.api.setting.console.ConsoleSettingPlugin;
import org.touchhome.bundle.api.ui.field.UIFieldType;

public class ConsoleLogLevelSetting implements ConsoleSettingPlugin<ConsoleLogLevelSetting.LogLevel>,
        SettingPluginOptionsEnum<ConsoleLogLevelSetting.LogLevel> {

    @Override
    public UIFieldType getSettingType() {
        return UIFieldType.SelectBoxButton;
    }

    @Override
    public Class<LogLevel> getType() {
        return LogLevel.class;
    }

    @Override
    public String getIcon() {
        return "fas fa-layer-group";
    }

    @Override
    public int order() {
        return 700;
    }

    @Override
    public String[] pages() {
        return new String[]{"logs"};
    }

    @RequiredArgsConstructor
    public enum LogLevel {
        Info(Level.INFO),
        Warn(Level.WARN),
        Error(Level.ERROR),
        Debug(Level.DEBUG);

        @Getter
        private final Level level;
    }
}
