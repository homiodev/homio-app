package org.touchhome.app;

import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;
import static org.apache.commons.lang3.StringUtils.isEmpty;

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
import java.util.function.Predicate;
import lombok.SneakyThrows;
import lombok.ToString;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
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
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.app.setting.console.lines.ConsoleDebugLevelSetting;
import org.touchhome.app.spring.ContextCreated;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.ui.UIEntityLog;
import org.touchhome.bundle.api.util.TouchHomeUtils;

@Component
public class LogService implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, ContextCreated {

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

  private static void scanEntityLogs(EntityContextImpl entityContext) {
    for (BaseEntity entity : entityContext.findAllBaseEntities()) {
      UIEntityLog[] annotations = entity.getClass().getDeclaredAnnotationsByType(UIEntityLog.class);
      if (annotations.length > 0) {
        LogConsumer logConsumer = new LogConsumer(entity.getEntityID(), entity.getClass());
        LOG_CONSUMERS.put(entity.getEntityID(), logConsumer);
        for (UIEntityLog uiEntityLog : annotations) {
          logConsumer.add(uiEntityLog.topic(), uiEntityLog.rawTopic(), uiEntityLog.filterByField(), entity);
        }
      }
    }
  }

  /**
   * Scan LogConsumers and send to ui if match
   */
  private static void sendEntityLogs(EntityContextImpl entityContext, LogEvent event) {
    for (LogConsumer logConsumer : LOG_CONSUMERS.values()) {
      if (logConsumer.logTopics.stream().anyMatch(l -> l.test(event))) {
        sendLogEvent(event, message -> {
          Files.writeString(logConsumer.path, message + System.lineSeparator(), StandardOpenOption.APPEND);
          entityContext.ui().sendDynamicUpdate("entity-log-" + logConsumer.entityID, "String", message);
        });
      }
    }
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
    LogService.scanEntityLogs(entityContext);
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

    public synchronized void setEntityContext(EntityContextImpl entityContext) {
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
      entityContext.setting().listenValueAndGet(ConsoleDebugLevelSetting.class, "log-set-level",
          logLevel -> updateLogLevel(entityContext.setting().getValue(ConsoleDebugLevelSetting.class)));
    }

    private void updateLogLevel(boolean includeDebug) {
      Level level = includeDebug ? Level.DEBUG : Level.INFO;
      if (level.intLevel() != this.intLevel) {
        for (LogAppenderHandler logAppenderHandler : logAppenderHandlers.values()) {
          logAppenderHandler.intLevel = level.intLevel();
        }
      }
    }
  }

  @ToString
  private static class LogConsumer {

    private final String entityID;
    private final Class<?> targetClass;
    private final String className;

    private final List<Predicate<LogEvent>> logTopics = new ArrayList<>();
    public Path path;

    @SneakyThrows
    public LogConsumer(String entityID, Class<?> targetClass) {
      this.entityID = entityID;
      this.targetClass = targetClass;
      this.className = targetClass.getSimpleName();
      this.path = TouchHomeUtils.getOrCreatePath("logs/entities/" + className).resolve(entityID + ".log");
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

    @SneakyThrows
    public void add(Class<?> topicClass, String rawTopic, String filterByField, BaseEntity entity) {
      if (topicClass == Void.class && isEmpty(rawTopic)) {
        throw new IllegalStateException("Unable to find topics @UIEntityLog for entity: " + entity);
      }
      String topic = defaultIfEmpty(rawTopic, topicClass.getName());
      Predicate<LogEvent> predicate;
      String filterValue = isEmpty(filterByField) ? null : String.valueOf(MethodUtils.invokeMethod(entity, "get" + StringUtils.capitalize(filterByField)));
      if (isEmpty(rawTopic)) {
        predicate = filterValue == null ?
            logEvent -> logEvent.getLoggerName().equals(topic) :
            logEvent -> logEvent.getLoggerName().equals(topic) && logEvent.getMessage().getFormattedMessage().contains(filterValue);
      } else {
        predicate = filterValue == null ?
            logEvent -> logEvent.getLoggerName().startsWith(topic) :
            logEvent -> logEvent.getLoggerName().startsWith(topic) && logEvent.getMessage().getFormattedMessage().contains(filterValue);
      }
      logTopics.add(predicate);
    }
  }
}
