package org.touchhome.app.setting.console.lines.log;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.Level;

import org.touchhome.bundle.api.setting.BundleSettingPluginSelectBoxEnum;
import org.touchhome.bundle.api.setting.console.BundleConsoleSettingPlugin;

public class ConsoleLogLevelSetting implements BundleConsoleSettingPlugin<ConsoleLogLevelSetting.LogLevel>,
        BundleSettingPluginSelectBoxEnum<ConsoleLogLevelSetting.LogLevel> {

    @Override
    public SettingType getSettingType() {
        return SettingType.SelectBoxButton;
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
