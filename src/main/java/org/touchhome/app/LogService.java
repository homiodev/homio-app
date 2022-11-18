package org.touchhome.app;

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
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.CountingNoOpAppender;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.core.impl.DefaultLogEventFactory;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.MessageFactory;
import org.apache.logging.log4j.spi.AbstractLogger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.app.setting.console.lines.ConsoleDebugLevelSetting;
import org.touchhome.app.spring.ContextCreated;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.model.HasEntityLog;
import org.touchhome.bundle.api.model.HasEntityLog.EntityLogBuilder;
import org.touchhome.bundle.api.util.TouchHomeUtils;

@Component
public class LogService implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, ContextCreated {

  private static final GlobalAppender globalAppender = new GlobalAppender();

  @SneakyThrows
  private static void initLogAppender() {
    LoggerContext loggerContext = (LoggerContext) LogManager.getContext(false);
    if (loggerContext.getExternalContext() != null) {
      Configuration configuration = loggerContext.getConfiguration();
      Set<String> appenderNames = new HashSet<>(configuration.getAppenders().keySet());
      for (String appenderName : appenderNames) {
        Appender appender = configuration.getAppenders().get(appenderName);
        if (appender instanceof RollingFileAppender) {
          var defineAppender = new DefinedAppenderConsumer(fetchAppenderPrefixSet(configuration, appenderName), ((RollingFileAppender) appender).getFileName());
          globalAppender.definedAppender.put(appenderName, defineAppender);
        }
      }

      MessageFactory messageFactory = AbstractLogger.DEFAULT_MESSAGE_FACTORY_CLASS.newInstance();
      DefaultLogEventFactory log4jLogEventFactory = new DefaultLogEventFactory();

      // hack: add global logger for every log since DEBUG level.
      configuration.addFilter(new AbstractFilter() {
        public void logIfRequire(Logger logger, Level level, Marker marker, String message, Throwable t, Object... params) {
          if (level.intLevel() <= Level.DEBUG.intLevel()) {
            Message msg = messageFactory.newMessage(message, params);
            globalAppender.append(log4jLogEventFactory.createEvent(logger.getName(), marker, null, level, msg, null, msg.getThrowable()));
          }
        }

        @Override
        public Result filter(Logger logger, Level level, Marker marker, String msg, Object... params) {
          logIfRequire(logger, level, marker, msg, null, params);
          return super.filter(logger, level, marker, msg, params);
        }

        @Override
        public Result filter(final Logger logger, final Level level, final Marker marker, final Object msg, final Throwable t) {
          logIfRequire(logger, level, marker, String.valueOf(msg), t);
          return super.filter(logger, level, marker, msg, t);
        }
      });

      configuration.getRootLogger().addAppender(globalAppender, Level.DEBUG, null);
      loggerContext.updateLoggers();
    }
  }

  private static void scanEntityLogs(EntityContextImpl entityContext) {
    for (BaseEntity entity : entityContext.findAllBaseEntities()) {
      if (entity instanceof HasEntityLog) {
        addLogEntity(entity);
      }
    }
    entityContext.event().addEntityUpdateListener(BaseEntity.class, "log-entity",
        baseEntity -> {
          if (baseEntity instanceof HasEntityLog && globalAppender.logConsumers.containsKey(baseEntity.getEntityID())) {
            globalAppender.logConsumers.get(baseEntity.getEntityID()).debug = ((HasEntityLog) baseEntity).isDebug();
          }
        });
    entityContext.event().addEntityRemovedListener(BaseEntity.class, "log-entity",
        baseEntity -> {
          if (baseEntity instanceof HasEntityLog) {
            globalAppender.logConsumers.remove(baseEntity.getEntityID());
          }
        });
    entityContext.event().addEntityCreateListener(BaseEntity.class, "log-entity", baseEntity -> {
      if (baseEntity instanceof HasEntityLog) {
        addLogEntity(baseEntity);
      }
    });
  }

  private static void addLogEntity(BaseEntity entity) {
    if (!globalAppender.logConsumers.containsKey(entity.getEntityID())) {
      LogConsumer logConsumer = new LogConsumer(entity.getEntityID(), entity.getClass(), ((HasEntityLog) entity).isDebug());
      EntityLogBuilderImpl entityLogBuilder = new EntityLogBuilderImpl(entity, logConsumer);
      ((HasEntityLog) entity).logBuilder(entityLogBuilder);

      if (!logConsumer.logTopics.isEmpty()) {
        globalAppender.logConsumers.put(entity.getEntityID(), logConsumer);
      }
    }
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
    return globalAppender.definedAppender.keySet();
  }

  @SneakyThrows
  public List<String> getLogs(String tab) {
    DefinedAppenderConsumer definedAppender = globalAppender.definedAppender.get(tab);
    if (definedAppender != null) {
      return FileUtils.readLines(new File(definedAppender.fileName), Charset.defaultCharset());
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
    globalAppender.setEntityContext(entityContext);
  }

  public @Nullable Path getEntityLogsFile(BaseEntity baseEntity) {
    return Optional.ofNullable(globalAppender.logConsumers.get(baseEntity.getEntityID())).map(c -> c.path).orElse(null);
  }

  public static class GlobalAppender extends CountingNoOpAppender {

    private final Map<String, LogConsumer> logConsumers = new HashMap<>();
    private final Map<String, DefinedAppenderConsumer> definedAppender = new HashMap<>();
    // allow debug level for appender
    private boolean allowDebugLevel;

    // keep all logs in memory until we switch strategy via setEntityContext(...) method
    private List<LogEvent> bufferedLogEvents = new CopyOnWriteArrayList<>();
    protected Consumer<LogEvent> logStrategy = event -> bufferedLogEvents.add(event);

    GlobalAppender() {
      super("Global", null);
    }

    public synchronized void setEntityContext(EntityContextImpl entityContext) {
      entityContext.setting().listenValueAndGet(ConsoleDebugLevelSetting.class, "log-set-level",
          logLevel -> this.allowDebugLevel = entityContext.setting().getValue(ConsoleDebugLevelSetting.class));
      this.logStrategy = event -> sendLogs(entityContext, event);
      flushBufferedLogs();
    }

    // Scan LogConsumers and send to ui if match
    private void sendLogs(EntityContextImpl entityContext, LogEvent event) {
      if (allowDebugLevel && event.getLevel().intLevel() <= Level.DEBUG.intLevel() ||
          !allowDebugLevel && event.getLevel().intLevel() <= Level.INFO.intLevel()) {
        for (Entry<String, DefinedAppenderConsumer> entry : definedAppender.entrySet()) {
          if (entry.getValue().accept(event.getLoggerName())) {
            sendLogEvent(event, message -> {
              entityContext.ui().sendDynamicUpdate("appender-log-" + entry.getKey(), "String", message);
            });
          }
        }
      }
      for (LogConsumer logConsumer : logConsumers.values()) {
        if (!logConsumer.debug && event.getLevel() == Level.DEBUG) {
          return;
        }
        if (logConsumer.logTopics.stream().anyMatch(l -> l.test(event))) {
          sendLogEvent(event, message -> {
            Files.writeString(logConsumer.path, message + System.lineSeparator(), StandardOpenOption.APPEND);
            entityContext.ui().sendDynamicUpdate("entity-log-" + logConsumer.entityID, "String", message);
          });
        }
      }
    }

    @Override
    public void append(LogEvent event) {
      this.logStrategy.accept(event);
    }

    // flush buffered log events and clean buffer
    void flushBufferedLogs() {
      for (LogEvent bufferedLogEvent : bufferedLogEvents) {
        this.logStrategy.accept(bufferedLogEvent);
      }
      bufferedLogEvents.clear();
      bufferedLogEvents = null;
    }
  }

  @Getter
  @RequiredArgsConstructor
  private static class DefinedAppenderConsumer {

    private final Set<String> prefixSet;
    private final String fileName;

    public boolean accept(String loggerName) {
      for (String prefix : prefixSet) {
        if (loggerName.startsWith(prefix)) {
          return true;
        }
      }
      return false;
    }
  }

  @Getter
  private static class LogConsumer {

    private final List<Predicate<LogEvent>> logTopics = new ArrayList<>();
    private final String entityID;
    private final Class<?> targetClass;
    private final String className;
    public final Path path;

    private boolean debug;

    @SneakyThrows
    public LogConsumer(String entityID, Class<?> targetClass, boolean debug) {
      this.entityID = entityID;
      this.targetClass = targetClass;
      this.className = targetClass.getSimpleName();
      this.path = TouchHomeUtils.getOrCreatePath("logs/entities/" + className).resolve(entityID + ".log");
      this.debug = debug;
      Files.write(path, new byte[0], StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }
  }

  private static Set<String> fetchAppenderPrefixSet(Configuration configuration, String appenderName) {
    Set<String> prefixSet = new HashSet<>();
    for (LoggerConfig config : configuration.getLoggers().values()) {
      if (config.getAppenders().containsKey(appenderName)) {
        prefixSet.add(config.getName());
      }
    }
    return prefixSet;
  }

  @RequiredArgsConstructor
  private static class EntityLogBuilderImpl implements EntityLogBuilder {

    private final BaseEntity entity;
    private final LogConsumer logConsumer;

    @SneakyThrows
    @Override
    public void addTopic(Class<?> topicClass, String filterByField) {
      String filterValue = isEmpty(filterByField) ? null : String.valueOf(MethodUtils.invokeMethod(entity, "get" + StringUtils.capitalize(filterByField)));
      String topic = topicClass.getName();
      logConsumer.logTopics.add(filterValue == null ?
          logEvent -> logEvent.getLoggerName().startsWith(topic) :
          logEvent -> logEvent.getLoggerName().startsWith(topic) && logEvent.getMessage().getFormattedMessage().contains(filterValue));
    }

    @Override
    @SneakyThrows
    public void addTopic(String topic, String filterByField) {
      String filterValue = isEmpty(filterByField) ? null : String.valueOf(MethodUtils.invokeMethod(entity, "get" + StringUtils.capitalize(filterByField)));
      logConsumer.logTopics.add(filterValue == null ?
          logEvent -> logEvent.getLoggerName().startsWith(topic) :
          logEvent -> logEvent.getLoggerName().startsWith(topic) && logEvent.getMessage().getFormattedMessage().contains(filterValue));
    }
  }
}
