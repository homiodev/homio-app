package org.touchhome.app;

import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.CountingNoOpAppender;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.touchhome.app.setting.console.ConsoleLogLevelSetting;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.util.SmartUtils;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.function.Consumer;

@Component
public class LogService implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    private static Map<String, LogAppenderHandler> logAppenderHandlers = new HashMap<>();

    private static void initLogAppender() {
        LoggerContext lc = (LoggerContext) LogManager.getContext(false);
        if (lc.getExternalContext() != null) {
            Configuration configuration = lc.getConfiguration();
            Set<String> appenderNames = new HashSet<>(configuration.getAppenders().keySet());
            for (String appenderName : appenderNames) {
                Appender appender = configuration.getAppenders().get(appenderName);
                if (appender instanceof RollingFileAppender) {
                    logAppenderHandlers.put(appenderName, new LogAppenderHandler((RollingFileAppender) appender));
                }
            }
            logAppenderHandlers.values().forEach(configuration::addAppender);

            ArrayList<LoggerConfig> loggerConfigs = new ArrayList<>(configuration.getLoggers().values());
            for (String appender : appenderNames) {
                for (LoggerConfig loggerConfig : loggerConfigs) {
                    if (loggerConfig.getAppenders().containsKey(appender) && logAppenderHandlers.containsKey(appender)) {
                        loggerConfig.addAppender(logAppenderHandlers.get(appender), Level.DEBUG, null);
                    }
                }
            }

            lc.updateLoggers();
        }
    }

    public Set<String> getTabs() {
        return logAppenderHandlers.keySet();
    }

    @SneakyThrows
    public List<String> getLogs(String tab) {
        LogAppenderHandler logAppenderHandler = logAppenderHandlers.get(tab);
        if (logAppenderHandler != null) {
            return FileUtils.readLines(new File(logAppenderHandler.fileName));
        }
        return null;
    }

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent ignore) {
        initLogAppender();
    }

    public void setEntityContext(EntityContext entityContext) {
        for (LogAppenderHandler logAppenderHandler : logAppenderHandlers.values()) {
            logAppenderHandler.setEntityContext(entityContext);
        }
    }

    public static class LogAppenderHandler extends CountingNoOpAppender {
        private final String appenderName;
        private final String fileName;
        private List<LogEvent> bufferedLogEvents = new ArrayList<>();
        private int intLevel = Level.DEBUG.intLevel();
        private Consumer<LogEvent> logStrategy = event -> bufferedLogEvents.add(event);

        LogAppenderHandler(RollingFileAppender appender) {
            super("Smart " + appender.getName() + " Counting Appender", null);
            this.appenderName = appender.getName();
            this.fileName = appender.getFileName();
            this.start();
        }

        static void sendLogEvent(String appenderName, LogEvent event, EntityContext entityContext) {
            entityContext.sendNotification("-logs-" + appenderName, formatLogMessage(event, event.getMessage().getFormattedMessage()));

            if (event.getThrown() != null) {
                StringWriter outError = new StringWriter();
                event.getThrown().printStackTrace(new PrintWriter(outError));
                String errorString = outError.toString();
                entityContext.sendNotification("-logs-" + appenderName, formatLogMessage(event, errorString));
            }
        }

        private static String formatLogMessage(LogEvent event, String message) {
            return String.format("%s %-5s [%-25s] [%-25s] - %s",
                    SmartUtils.dateFormat.format(new Date(event.getTimeMillis())),
                    event.getLevel(), maxLength(event.getThreadName()), maxLength(event.getLoggerName()), message);
        }

        private static String maxLength(String text) {
            return text.length() > 25 ? text.substring(text.length() - 25) : text;
        }

        public synchronized void setEntityContext(EntityContext entityContext) {
            listenAndUpdateLogLevel(entityContext);

            for (LogEvent bufferedLogEvent : bufferedLogEvents) {
                sendLogEvent(this.appenderName, bufferedLogEvent, entityContext);
            }
            bufferedLogEvents = null;

            this.logStrategy = event -> sendLogEvent(this.appenderName, event, entityContext);
        }

        @Override
        public void append(LogEvent event) {
            if (intLevel < event.getLevel().intLevel()) {
                return;
            }
            this.logStrategy.accept(event);
            super.append(event);
        }

        private void listenAndUpdateLogLevel(EntityContext entityContext) {
            Level level = entityContext.getSettingValue(ConsoleLogLevelSetting.class).getLevel();
            if (level != Level.DEBUG) {
                updateLogLevel(level);
            }
            entityContext.listenSettingValue(ConsoleLogLevelSetting.class, logLevel -> updateLogLevel(logLevel.getLevel()));
        }

        private void updateLogLevel(Level level) {
            for (LogAppenderHandler logAppenderHandler : logAppenderHandlers.values()) {
                logAppenderHandler.intLevel = level.intLevel();
            }
        }
    }
}
