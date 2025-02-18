package org.homio.app;

import com.pivovarit.function.ThrowingConsumer;
import com.sshtools.common.logger.DefaultLoggerContext;
import com.sshtools.common.logger.Log;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
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
import org.homio.api.Context;
import org.homio.api.Context.FileLogger;
import org.homio.api.entity.BaseEntity;
import org.homio.api.entity.log.HasEntityLog;
import org.homio.api.entity.log.HasEntityLog.EntityLogBuilder;
import org.homio.api.util.CommonUtils;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.spring.ContextCreated;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.FileHandler;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.logging.slf4j.Log4jLogger.FQCN;

@Log4j2
@Component
public class LogService implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, ContextCreated {

  private static final org.apache.logging.log4j.Logger maverickLogger = LogManager.getLogger("com.ssh.maverick");

  private static final GlobalAppender globalAppender = new GlobalAppender();
  /*private static final Set<String> excludeDebugPackages =
          Set.of(
                  "org.jmdns",
                  "su.litvak.chromecast",
                  "org.homio.app.auth.AccessFilter",
                  "org.springframework",
                  "com.mongodb",
                  "de.bwaldvogel",
                  "org.mongodb",
                  "com.zaxxer",
                  "org.hibernate");*/
  private static final BlockingQueue<LogEvent> eventQueue = new LinkedBlockingQueue<>();

  static {
    Log.setDefaultContext(new DefaultLoggerContext() {

      @Override
      public synchronized boolean isLogging(Log.Level level) {
        return true;
      }

      @Override
      public synchronized void log(Log.Level level, String msg, Throwable e, Object... args) {
        maverickLogger.log(getLogLevel(level), DefaultLoggerContext.prepareLog(level, msg, e, args));
      }

      private Level getLogLevel(Log.Level level) {
        return switch (level) {
          case ERROR -> Level.ERROR;
          case WARN -> Level.WARN;
          case TRACE -> Level.TRACE;
          default -> Level.DEBUG;
        };
      }
    });

    new Thread(() -> {
      while (true) {
        try {
          globalAppender.append(eventQueue.take());
        } catch (Exception ex) {
          log.error("Error while execute log event handler", ex);
        }
      }
    }, "EntityLogHandler").start();
  }

  private final Map<String, Map<String, FileLoggerImpl>> fileLoggers = new ConcurrentHashMap<>();

  @SneakyThrows
  private static void initLogAppender() {
    LoggerContext loggerContext = (LoggerContext) LogManager.getContext(false);
    if (loggerContext.getExternalContext() != null) {
      Configuration configuration = loggerContext.getConfiguration();
      Set<String> appenderNames = new HashSet<>(configuration.getAppenders().keySet());
      for (String appenderName : appenderNames) {
        Appender appender = configuration.getAppenders().get(appenderName);
        if (appender instanceof RollingFileAppender) {
          var defineAppender = new DefinedAppenderConsumer(fetchAppenderPrefixSet(configuration, appenderName),
            ((RollingFileAppender) appender).getFileName());
          globalAppender.definedAppender.put(appenderName, defineAppender);
        }
      }

      MessageFactory messageFactory = CommonUtils.newInstance(AbstractLogger.DEFAULT_MESSAGE_FACTORY_CLASS);
      DefaultLogEventFactory log4jLogEventFactory = new DefaultLogEventFactory();

      // hack: add global logger for every log since DEBUG level.
      configuration.addFilter(new AbstractFilter() {
        public void logIfRequire(Logger logger, Level level, Marker marker, String message, Throwable ignored, Object... params) {
          if (level.intLevel() == Level.DEBUG.intLevel() && disabledDebugPackage(logger.getName())) {
            return;
          }
          if (level.intLevel() <= Level.DEBUG.intLevel()) {
            Message data = params == null || params.length == 0 ? messageFactory.newMessage(message) : messageFactory.newMessage(message, params);
            LogEvent event = log4jLogEventFactory.createEvent(logger.getName(), marker, FQCN, level, data, null, data.getThrowable());
            eventQueue.add(event);
          }
        }

        @Override
        public Result filter(Logger logger, Level level, Marker marker, String msg, Object... params) {
          if (msg != null) {
            logIfRequire(logger, level, marker, msg, null, params);
          }
          return super.filter(logger, level, marker, msg, params);
        }

        @Override
        public Result filter(final Logger logger, final Level level, final Marker marker, final Object msg, final Throwable t) {
          if (msg != null) {
            logIfRequire(logger, level, marker, String.valueOf(msg), t);
          }
          return super.filter(logger, level, marker, msg, t);
        }
      });

      configuration.getRootLogger().addAppender(globalAppender, Level.DEBUG, null);
      loggerContext.updateLoggers();
    }
  }

  private static boolean disabledDebugPackage(String name) {
        /*for (String excludeDebugPackage : excludeDebugPackages) {
            if (name.startsWith(excludeDebugPackage)) {
                return true;
            }
        }
        return false;*/
    return true;
  }

  private static void scanEntityLogs(ContextImpl context) {
    for (BaseEntity entity : context.db().findAllBaseEntities()) {
      if (entity instanceof HasEntityLog) {
        addLogEntity(entity);
      }
    }
    context.event().addEntityUpdateListener(BaseEntity.class, "log-entity", baseEntity -> {
      if (baseEntity instanceof HasEntityLog && globalAppender.logConsumers.containsKey(baseEntity.getEntityID())) {
        globalAppender.logConsumers.get(baseEntity.getEntityID()).debug = ((HasEntityLog) baseEntity).isDebug();
      }
    });
    context.event().addEntityRemovedListener(BaseEntity.class, "log-entity", baseEntity -> {
      if (baseEntity instanceof HasEntityLog) {
        globalAppender.logConsumers.remove(baseEntity.getEntityID());
      }
    });
    context.event().addEntityCreateListener(BaseEntity.class, "log-entity", baseEntity -> {
      if (baseEntity instanceof HasEntityLog) {
        addLogEntity(baseEntity);
      }
    });
  }

  private static void addLogEntity(BaseEntity entity) {
    if (!globalAppender.logConsumers.containsKey(entity.getEntityID())) {
      LogConsumer logConsumer = new LogConsumer(
        entity.getEntityID(),
        entity.getClass(),
        createLogFile(entity, ""),
        ((HasEntityLog) entity).isDebug());
      EntityLogBuilderImpl entityLogBuilder = new EntityLogBuilderImpl(entity, logConsumer);
      ((HasEntityLog) entity).logBuilder(entityLogBuilder);

      if (!logConsumer.logTopics.isEmpty()) {
        globalAppender.logConsumers.put(entity.getEntityID(), logConsumer);
      }
    }
  }

  private static Path createLogFile(@NotNull BaseEntity entity, @NotNull String suffix) {
    Path path = CommonUtils.getLogsEntitiesPath().resolve(entity.getType());
    CommonUtils.createDirectoriesIfNotExists(path);
    Path file = path.resolve(entity.getEntityID() + suffix + ".log");
    try {
      CommonUtils.writeToFile(file, new byte[0], false);
    } catch (Exception ex) {
      System.out.printf("Error during truncate file: %s. Error: %s%n", path, CommonUtils.getErrorMessage(ex));
    }
    return file;
  }

  @SneakyThrows
  private static void sendLogEvent(LogEvent event, ThrowingConsumer<String, Exception> consumer) {
    try {
      boolean entityPrefix = Optional.ofNullable(event.getMessage().getFormat()).map(s -> s.startsWith("[{}]: ")).orElse(false);
      String message = event.getMessage().getFormattedMessage();
      if (entityPrefix) {
        message = message.substring(message.indexOf("]: ") + 3);
      }
      consumer.accept(formatLogMessage(event, message));

      if (event.getThrown() != null) {
        StringWriter outError = new StringWriter();
        event.getThrown().printStackTrace(new PrintWriter(outError));
        String errorString = outError.toString();
        consumer.accept(formatLogMessage(event, errorString));
      }
    } catch (Exception ex) {
      System.err.println("Error while logging event: " + CommonUtils.getErrorMessage(ex));
    }
  }

  private static String formatLogMessage(LogEvent event, String message) {
    return String.format(
      "%s %-5s [%-25s] [%-25s] - %s",
      CommonUtils.DATE_TIME_FORMAT.format(new Date(event.getTimeMillis())),
      event.getLevel(),
      maxLength(event.getThreadName()),
      maxLength(event.getLoggerName()),
      message);
  }

  private static String maxLength(String text) {
    return text.length() > 25 ? text.substring(text.length() - 25) : text;
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

  public Set<String> getTabs() {
    return globalAppender.definedAppender.keySet();
  }

  @SneakyThrows
  public List<String> getLogs(String tab) {
    DefinedAppenderConsumer definedAppender = globalAppender.definedAppender.get(tab);
    if (definedAppender != null) {
      return FileUtils.readLines(
        new File(definedAppender.fileName), Charset.defaultCharset());
    }
    return null;
  }

  @Override
  public void onApplicationEvent(@NotNull ApplicationEnvironmentPreparedEvent ignore) {
    initLogAppender();
  }

  @Override
  public void onContextCreated(ContextImpl context) throws Exception {
    LogService.scanEntityLogs(context);
    globalAppender.setContext(context);
  }

  public @Nullable Path getEntityLogsFile(BaseEntity baseEntity) {
    LogConsumer logConsumer = globalAppender.logConsumers.get(baseEntity.getEntityID());
    return logConsumer == null ? null : logConsumer.logFile;
  }

  public void deleteEntityLogsFile(BaseEntity baseEntity) {
    LogConsumer logConsumer = globalAppender.logConsumers.get(baseEntity.getEntityID());
    if (logConsumer != null) {
      try {
        Files.deleteIfExists(logConsumer.logFile);
      } catch (IOException ex) {
        log.error("Unable to delete entity log file: {}", CommonUtils.getErrorMessage(ex));
      }
    }
    Map<String, FileLoggerImpl> entityLoggers = fileLoggers.remove(baseEntity.getEntityID());
    if (entityLoggers != null) {
      for (FileLoggerImpl fileLogger : entityLoggers.values()) {
        try {
          Files.deleteIfExists(fileLogger.logPath);
        } catch (IOException ex) {
          log.error("Unable to delete entity file log handler: {}", CommonUtils.getErrorMessage(ex));
        }
      }
    }
  }

  public FileLogger getFileLogger(BaseEntity baseEntity, String suffix) {
    return fileLoggers.computeIfAbsent(baseEntity.getEntityID(), s -> new ConcurrentHashMap<>())
      .computeIfAbsent(suffix, file -> new FileLoggerImpl(suffix, baseEntity));
  }

  public static class GlobalAppender extends CountingNoOpAppender {

    private final Map<String, LogConsumer> logConsumers = new ConcurrentHashMap<>();
    private final Map<String, DefinedAppenderConsumer> definedAppender = new HashMap<>();

    // keep all logs in memory until we switch strategy via setContext(...) method
    private List<LogEvent> bufferedLogEvents = new CopyOnWriteArrayList<>();
    protected Consumer<LogEvent> logStrategy = event -> bufferedLogEvents.add(event);

    GlobalAppender() {
      super("Global", null);
      start();
    }

    public synchronized void setContext(ContextImpl context) {
      this.logStrategy = event -> sendLogs(context, event);
      flushBufferedLogs();
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

    // Scan LogConsumers and send to ui if match
    private void sendLogs(Context context, LogEvent event) {
      if (event.getLevel().intLevel() <= Level.DEBUG.intLevel()) {
        for (Entry<String, DefinedAppenderConsumer> entry : definedAppender.entrySet()) {
          if (entry.getValue().accept(event.getLoggerName())) {
            sendLogEvent(event, message ->
              context.ui().sendDynamicUpdate("appender-log-" + entry.getKey(), message));
          }
        }
      }
      for (LogConsumer logConsumer : logConsumers.values()) {
        if (!logConsumer.debug && event.getLevel() == Level.DEBUG) {
          return;
        }
        if (logConsumer.logTopics.stream().anyMatch(l -> l.test(event))) {
          sendLogEvent(event, message -> {
            Files.writeString(logConsumer.logFile, message + System.lineSeparator(), StandardOpenOption.APPEND);
            context.ui().sendDynamicUpdate("entity-log-" + logConsumer.entityID, message);
          });
        }
      }
    }
  }

  private record DefinedAppenderConsumer(Set<String> prefixSet, String fileName) {

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
  @AllArgsConstructor
  private static class LogConsumer {

    private final List<Predicate<LogEvent>> logTopics = new ArrayList<>();
    private final @NotNull String entityID;
    private final @NotNull Class<?> targetClass;
    private final @NotNull Path logFile;
    private boolean debug;

    @Override
    public String toString() {
      return entityID;
    }
  }

  private record EntityLogBuilderImpl(BaseEntity entity, LogConsumer logConsumer) implements EntityLogBuilder {

    @Override
    @SneakyThrows
    public void addTopic(@NotNull String topic, String filterByField) {
      String filterValue =
        isEmpty(filterByField) ? null : String.valueOf(MethodUtils.invokeMethod(entity, "get" + StringUtils.capitalize(filterByField)));
      logConsumer.logTopics.add(filterValue == null
        ? logEvent -> logEvent.getLoggerName().startsWith(topic)
        : logEvent -> logEvent.getLoggerName().startsWith(topic) && logEvent.getMessage().getFormattedMessage().contains(filterValue));
    }
  }

  private static class FileLoggerImpl implements FileLogger {

    private final Path logPath;
    private final FileHandler fileHandler;

    @SneakyThrows
    public FileLoggerImpl(@NotNull String suffix, @NotNull BaseEntity entity) {
      logPath = createLogFile(entity, suffix);
      fileHandler = new FileHandler(logPath.toString(), 1024 * 1024, 1, true);
      fileHandler.setFormatter(new SimpleFormatter());
    }

    @Override
    public void logDebug(@Nullable String message) {
      log(message, java.util.logging.Level.FINE);
    }

    @Override
    public void logInfo(String message) {
      log(message, java.util.logging.Level.INFO);
    }

    @Override
    public void logWarn(String message) {
      log(message, java.util.logging.Level.WARNING);
    }

    @Override
    public void logError(String message) {
      log(message, java.util.logging.Level.SEVERE);
    }

    @Override
    @SneakyThrows
    public @NotNull InputStream getFileInputStream() {
      return Files.newInputStream(logPath);
    }

    @Override
    public @NotNull String getName() {
      return logPath.getFileName().toString();
    }

    private void log(String message, java.util.logging.Level level) {
      if (message != null) {
        fileHandler.publish(new LogRecord(level, message));
      }
    }
  }
}
