package org.touchhome.app;

import static org.touchhome.bundle.api.util.TouchHomeUtils.getOrCreatePath;

import com.pivovarit.function.ThrowingConsumer;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import lombok.SneakyThrows;
import lombok.ToString;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.touchhome.app.manager.common.ClassFinder;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.app.setting.console.lines.log.ConsoleLogLevelSetting;
import org.touchhome.app.spring.ContextCreated;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.ui.UIEntityLog;
import org.touchhome.bundle.api.ui.UIEntityLogs;
import org.touchhome.bundle.api.util.TouchHomeUtils;

@Component
public class LogService implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, ContextCreated {

  private static final Path LOGS_DIR = getOrCreatePath("logs/entities");
  private static final Map<String, LogAppenderHandler> logAppenderHandlers = new HashMap<>();
  private static final Map<String, LogConsumer> LOG_CONSUMERS = new HashMap<>();

  private static void initLogAppender() {
    LoggerContext loggerContext = (LoggerContext) LogManager.getContext(false);
    if (loggerContext.getExternalContext() != null) {
      Configuration configuration = loggerContext.getConfiguration();
      Set<String> appenderNames = new HashSet<>(configuration.getAppenders().keySet());
      for (String appenderName : appenderNames) {
        Appender appender = configuration.getAppenders().get(appenderName);
        if (appender instanceof RollingFileAppender) {
          LogAppenderHandler handler = new LogAppenderHandler((RollingFileAppender) appender);
          logAppenderHandlers.put(appenderName, handler);
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

      loggerContext.updateLoggers();
    }
  }

  private static void scanEntityLogs(ClassFinder classFinder, EntityContextImpl entityContext) {
    for (BaseEntity entity : entityContext.findAllBaseEntities()) {
      for (Class<?> entityClass : classFinder.getClassesWithAnnotation(UIEntityLogs.class)) {
        LogConsumer logConsumer = new LogConsumer(entityClass);
        LOG_CONSUMERS.put(entity.getEntityID(), logConsumer);
        for (UIEntityLog uiEntityLog : entityClass.getDeclaredAnnotationsByType(UIEntityLog.class)) {
          String topic = uiEntityLog.topic().getName();
          if (!topic.equals(Void.class.getSimpleName())) {
            logConsumer.topics.add(topic);
          }
          String rawTopic = uiEntityLog.rawTopic();
          if (StringUtils.hasLength(rawTopic)) {
            logConsumer.topics.add(topic);
          }
          if (logConsumer.topics.isEmpty()) {
            throw new IllegalStateException("Unable to find topics @UIEntityLog for entity: " + entity);
          }
        }
      }
    }
  }

  /**
   * Scan LogConsumers and send to ui if match
   */
  private static void sendEntityLogs(EntityContext entityContext, LogEvent event) {
    System.out.println("asdasd");
    /*for (LogConsumer logConsumer : LOG_CONSUMERS) {
      if (logConsumer.accept(event)) {
        sendLogEvent(event, message -> {
          Files.writeString(logConsumer.path, message + System.lineSeparator(), StandardOpenOption.APPEND);
          ((EntityContextUIImpl) entityContext.ui()).sendDynamicUpdate(
              "entity-log-" + logConsumer.className, "String", message);
        });
      }
    }*/
  }

  private static void sendLogEvent(String appenderName, LogEvent event, EntityContext entityContext) {
    sendLogEvent(event, message -> entityContext.ui().sendNotification("-lines-" + appenderName, message));
  }

  @SneakyThrows
  private static void sendLogEvent(LogEvent event, ThrowingConsumer<String, Exception> consumer) {
    consumer.accept(formatLogMessage(event, event.getMessage().getFormattedMessage()));

    if (event.getThrown() != null) {
      StringWriter outError = new StringWriter();
      event.getThrown().printStackTrace(new PrintWriter(outError));
      String errorString = outError.toString();
      consumer.accept(formatLogMessage(event, errorString));
    }
  }

  private static String formatLogMessage(LogEvent event, String message) {
    return String.format("%s %-5s [%-25s] [%-25s] - %s",
        TouchHomeUtils.DATE_TIME_FORMAT.format(new Date(event.getTimeMillis())),
        event.getLevel(), maxLength(event.getThreadName()), maxLength(event.getLoggerName()), message);
  }

  private static String maxLength(String text) {
    return text.length() > 25 ? text.substring(text.length() - 25) : text;
  }

  public Set<String> getTabs() {
    return logAppenderHandlers.keySet();
  }

  @SneakyThrows
  public List<String> getLogs(String tab) {
    LogAppenderHandler logAppenderHandler = logAppenderHandlers.get(tab);
    if (logAppenderHandler != null) {
      return FileUtils.readLines(new File(logAppenderHandler.fileName), Charset.defaultCharset());
    }
    return null;
  }

  @Override
  public void onApplicationEvent(@NotNull ApplicationEnvironmentPreparedEvent ignore) {
    initLogAppender();
  }

  @Override
  public void onContextCreated(EntityContextImpl entityContext) throws Exception {
    LogService.scanEntityLogs(entityContext.getBean(ClassFinder.class), entityContext);
    for (LogAppenderHandler logAppenderHandler : logAppenderHandlers.values()) {
      logAppenderHandler.setEntityContext(entityContext);
    }
  }

  public @Nullable Path getEntityLogsFile(BaseEntity baseEntity) {
    return Optional.ofNullable(LOG_CONSUMERS.get(baseEntity.getEntityID())).map(c -> c.path).orElse(null);
  }

  public static class LogAppenderHandler extends CountingNoOpAppender {

    private final String appenderName;
    private final String fileName;
    private List<LogEvent> bufferedLogEvents = new CopyOnWriteArrayList<>();
    private int intLevel = Level.DEBUG.intLevel();
    // keep all logs in memory until we switch strategy via setEntityContext(...) method
    private Consumer<LogEvent> logStrategy = event -> bufferedLogEvents.add(event);

    LogAppenderHandler(RollingFileAppender appender) {
      super("Smart " + appender.getName() + " Counting Appender", null);
      this.appenderName = appender.getName();
      this.fileName = appender.getFileName();
      this.start();
    }

    public synchronized void setEntityContext(EntityContext entityContext) {
      listenAndUpdateLogLevel(entityContext);

      // attach extra handler for appLog appender
      if (this.appenderName.equals("appLog")) {
        this.logStrategy = event -> {
          sendEntityLogs(entityContext, event);
          sendLogEvent(this.appenderName, event, entityContext);
        };
      } else {
        this.logStrategy = event -> sendLogEvent(this.appenderName, event, entityContext);
      }

      // flush buffered log events and clean buffer
      for (LogEvent bufferedLogEvent : bufferedLogEvents) {
        this.logStrategy.accept(bufferedLogEvent);
      }
      bufferedLogEvents.clear();
      bufferedLogEvents = null;
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
      Level level = entityContext.setting().getValue(ConsoleLogLevelSetting.class).getLevel();
      if (level != Level.DEBUG) {
        updateLogLevel(level);
      }
      entityContext.setting()
          .listenValue(ConsoleLogLevelSetting.class, "log-set-level", logLevel -> updateLogLevel(logLevel.getLevel()));
    }

    private void updateLogLevel(Level level) {
      for (LogAppenderHandler logAppenderHandler : logAppenderHandlers.values()) {
        logAppenderHandler.intLevel = level.intLevel();
      }
    }
  }

  @ToString
  private static class LogConsumer {

    private final Class<?> targetClass;
    private final String className;

    private final Set<String> topics = new HashSet<>();
    public Path path;

    @SneakyThrows
    public LogConsumer(Class<?> targetClass) {
      this.targetClass = targetClass;
      this.className = targetClass.getSimpleName();
      this.path = LogService.LOGS_DIR.resolve(className + ".log");
      Files.write(path, new byte[0], StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      LogConsumer that = (LogConsumer) o;

      return className.equals(that.className);
    }

    @Override
    public int hashCode() {
      return className.hashCode();
    }

    public boolean accept(LogEvent event) {
      if (event.getThreadName() != null) {
        if (topics.contains(event.getLoggerName())) {
          return true;
        }
        String message = event.getMessage().getFormattedMessage();
      }
      return false;
    }

    public boolean accept(String line) {
      for (String topic : topics) {
        if (line.contains(topic)) {
          return true;
        }
      }
      return false;
    }
  }
}
