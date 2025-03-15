package org.homio.app.manager.common.impl;

import com.pivovarit.function.ThrowingBiConsumer;
import com.pivovarit.function.ThrowingBiFunction;
import com.pivovarit.function.ThrowingConsumer;
import com.pivovarit.function.ThrowingFunction;
import com.pivovarit.function.ThrowingRunnable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.SystemUtils;
import org.apache.logging.log4j.Logger;
import org.homio.api.ContextBGP;
import org.homio.api.entity.HasStatusAndMsg;
import org.homio.api.exception.ServerException;
import org.homio.api.model.HasEntityIdentifier;
import org.homio.api.service.EntityService.WatchdogService;
import org.homio.api.util.CommonUtils;
import org.homio.app.json.BgpProcessResponse;
import org.homio.app.manager.bgp.InternetAvailabilityBgpService;
import org.homio.app.manager.bgp.WatchdogBgpService;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.utils.CollectionUtils.LastBytesBuffer;
import org.homio.hquery.ProgressBar;
import org.homio.hquery.StreamGobbler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.jvnet.winp.WinProcess;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

@Log4j2
public class ContextBGPImpl implements ContextBGP {

  @Getter
  private final @Accessors(fluent = true) ContextImpl context;
  private final ThreadPoolTaskScheduler taskScheduler;
  private final Map<String, ThrowingRunnable<Exception>> lowPriorityRequests = new ConcurrentHashMap<>();

  @Getter
  private final Map<String, ThreadContextImpl<?>> schedulers = new ConcurrentHashMap<>();

  // not all threads may be run inside ThreadContextImpl, so we need addon able to register
  // custom
  // threads to show them on ui
  @Getter
  private final Map<String, Consumer<ThreadPuller>> threadsPullers = new ConcurrentHashMap<>();

  private final Map<String, BatchRunContext<?>> batchRunContextMap = new ConcurrentHashMap<>();

  @Getter
  private final InternetAvailabilityBgpService internetAvailabilityService;
  @Getter
  private final WatchdogBgpService watchdogBgpService;

  private final Map<String, Map<String, Consumer<Boolean>>> pingMap = new ConcurrentHashMap<>();
  private ThreadContext<Void> pingProcess;

  public ContextBGPImpl(ContextImpl context, ThreadPoolTaskScheduler taskScheduler) {
    this.context = context;
    this.taskScheduler = taskScheduler;
    this.internetAvailabilityService = new InternetAvailabilityBgpService(context, this);
    this.watchdogBgpService = new WatchdogBgpService(this);
  }

  public static void stopProcess(Process process, String name) {
    if (SystemUtils.IS_OS_WINDOWS) {
      WinProcess p = new WinProcess(process);
      try {
        boolean sent = p.sendCtrlC();
        log.info("Sending Ctrl-C to process: {}. Result: {}", name, sent);
      } catch (Exception ex) {
        log.error(CommonUtils.getErrorMessage(ex));
      }
      sleep(1000);
      log.info("Killing process tree: {}", name);
      p.killRecursively();
    } else {
      try {
        Process exec = Runtime.getRuntime().exec("kill -SIGINT " + process.pid());
        StreamGobbler.streamAndStop(exec, 200, 500, log::info, log::error);
      } catch (IOException ignore) {
      }
    }
  }

  private static void executeOnExitImpl(@NotNull String name, @NotNull ThrowingRunnable runnable) {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      try {
        runnable.run();
      } catch (Exception ex) {
        System.err.println("Unable to execute shutdown hook: " + name);
      }
    }));
  }

  private static void sleep(int timeout) {
    try {
      Thread.sleep(timeout);
    } catch (Exception ignore) {
    }
  }

  public void onContextCreated() {
    // send update to UI about changed processes. Send only of user open console with thread/scheduler tab!
    builder("send-bgp-to-ui")
      .interval(Duration.ofSeconds(1))
      .cancelOnError(false)
      .execute(() -> this.context.ui().sendDynamicUpdate("bgp", getProcesses()));

    builder("low-priority-requests")
      .interval(Duration.ofSeconds(60))
      .cancelOnError(false)
      .execute(() -> {
        for (Entry<String, ThrowingRunnable<Exception>> entry : lowPriorityRequests.entrySet()) {
          try {
            entry.getValue().run();
          } catch (Exception ex) {
            log.warn("Unable to run low priority request: {}. {}", entry.getKey(), CommonUtils.getErrorMessage(ex));
          }
        }
      });
  }

  public BgpProcessResponse getProcesses() {
    BgpProcessResponse response = new BgpProcessResponse();
    for (ContextBGPImpl.ThreadContextImpl<?> context : schedulers.values()) {
      response.add(context);
    }
    return response;
  }

  @Override
  public void cancelThread(@NotNull String name) {
    ThreadContextImpl<?> context = this.schedulers.get(name);
    if (context != null) {
      log.info("Cancel process: <{}>", context.getName());
      context.cancel();
    } else if (this.batchRunContextMap.containsKey(name)) {
      log.info("Stop batch process: <{}>", name);
      this.batchRunContextMap.get(name).executor.shutdownNow();
    } else {
      for (Map.Entry<String, BatchRunContext<?>> entry : this.batchRunContextMap.entrySet()) {
        if (entry.getValue().processes.containsKey(name)) {
          log.info("Stop single process <{}> in batch <{}", name, entry.getKey());
          Future<?> future = entry.getValue().processes.get(name);
          if (future.isDone()) {
            log.warn("Process {} already done", name);
          } else {
            future.cancel(true);
          }
        }
      }
    }
  }

  @Override
  public void registerThreadsPuller(@NotNull String entityId, @NotNull Consumer<ThreadPuller> threadPullerConsumer) {
    this.threadsPullers.put(entityId, threadPullerConsumer);
  }

  @Override
  public <P extends HasEntityIdentifier, T> void runInBatch(
    @NotNull String batchName,
    @Nullable Duration maxTerminateTimeout,
    @NotNull Collection<P> batchItems,
    @NotNull Function<P, Callable<T>> callableProducer,
    @NotNull Consumer<Integer> progressConsumer,
    @NotNull Consumer<List<T>> finallyProcessBlockHandler) {
    BatchRunContext<T> batchRunContext = prepareBatchProcessContext(batchName, batchItems.size());
    for (P batchItem : batchItems) {
      Callable<T> callable = callableProducer.apply(batchItem);
      if (callable != null) {
        batchRunContext.processes.put(batchItem.getEntityID(), batchRunContext.executor.submit(callable));
      }
    }
    builder("batch-wait_" + batchName).execute(() -> {
      List<T> result = waitBatchToDone(batchName, maxTerminateTimeout, progressConsumer, batchRunContext);
      finallyProcessBlockHandler.accept(result);
    });
  }

  @SneakyThrows
  @Override
  public <T> @NotNull List<T> runInBatchAndGet(
    @NotNull String batchName,
    @Nullable Duration maxTerminateTimeout,
    int threadsCount,
    @NotNull Map<String, Callable<T>> runnableTasks,
    @NotNull Consumer<Integer> progressConsumer) {
    BatchRunContext<T> batchRunContext = prepareBatchProcessContext(batchName, threadsCount);
    for (Map.Entry<String, Callable<T>> entry : runnableTasks.entrySet()) {
      batchRunContext.processes.put(entry.getKey(), batchRunContext.executor.submit(entry.getValue()));
    }
    return waitBatchToDone(batchName, maxTerminateTimeout, progressConsumer, batchRunContext);
  }

  @Override
  public void executeOnExit(@NotNull String name, @NotNull ThrowingRunnable runnable) {
    executeOnExitImpl(name, runnable);
  }

  @Override
  public void addLowPriorityRequest(String key, ThrowingRunnable<Exception> handler) {
    lowPriorityRequests.put(key, handler);
  }

  @Override
  public void removeLowPriorityRequest(String key) {
    lowPriorityRequests.remove(key);
  }

  @Override
  public boolean isThreadExists(@NotNull String name, boolean checkOnlyRunningThreads) {
    ThreadContextImpl<?> threadContext = this.schedulers.get(name);
    if (checkOnlyRunningThreads) {
      return threadContext != null && !threadContext.stopped;
    }
    return threadContext != null;
  }

  public void addWatchDogService(String key, WatchdogService watchdogService) {
    this.watchdogBgpService.addWatchDogService(key, watchdogService);
  }

  @Override
  public synchronized void ping(@NotNull String discriminator, @NotNull String ipAddress, @NotNull Consumer<Boolean> availableStatus) {
    pingMap.computeIfAbsent(discriminator, s -> new ConcurrentHashMap<>())
      .put(ipAddress, availableStatus);
    if (pingProcess == null) {
      pingProcess = createPingService();
    }
  }

  @Override
  public synchronized void unPing(@NotNull String discriminator, @Nullable String ipAddress) {
    if (ipAddress == null) {
      pingMap.remove(discriminator);
    } else {
      Map<String, Consumer<Boolean>> map = pingMap.get(discriminator);
      if (map != null && map.remove(ipAddress) != null && map.isEmpty()) {
        pingMap.remove(discriminator);
      }
    }
    if (pingMap.isEmpty() && pingProcess != null) {
      ContextBGP.cancel(pingProcess);
      pingProcess = null;
    }
  }

  @Override
  public void execute(@Nullable Duration delay, @NotNull ThrowingRunnable<Exception> runnable) {
    Date startDate = new Date();
    if (delay != null && delay.toMillis() > 100) {
      startDate = new Date(System.currentTimeMillis() + delay.toMillis());
    }
    taskScheduler.schedule(() -> {
      try {
        runnable.run();
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    }, startDate.toInstant());
  }

  @Override
  public <T> @NotNull ScheduleBuilder<T> builder(@NotNull String name) {
    ThreadContextImpl<T> context = new ThreadContextImpl<>();
    context.name = name;
    context.scheduleType = ScheduleType.SINGLE;
    return new ScheduleBuilder<>() {
      private Function<Runnable, ScheduledFuture<?>> scheduleHandler;

      @Override
      public @NotNull ThreadContext<T> execute(@NotNull ThrowingFunction<ThreadContext<T>, T, Exception> command, boolean start) {
        context.command = command;
        if (scheduleHandler == null) {
          scheduleHandler = runnable -> {
            Date startDate = new Date();
            if (context.delay != null) {
              startDate = new Date(System.currentTimeMillis() + context.delay.toMillis());
            }
            if (context.scheduleType == ScheduleType.SINGLE) {
              return taskScheduler.schedule(runnable, startDate.toInstant());
            } else {
              return taskScheduler.scheduleWithFixedDelay(runnable, startDate.toInstant(), context.period);
            }
          };
        }

        if (start) {
          createSchedule(context, scheduleHandler);
        } else {
          context.postponeScheduleHandler = scheduleHandler;
          cancelThread(context.name);
        }
        schedulers.put(context.name, context);
        return context;
      }

      @Override
      public @NotNull ThreadContext<Void> execute(@NotNull ThrowingRunnable<Exception> command, boolean start) {
        context.command = arg -> {
          if (context.authentication == null) {
            command.run();
          } else {
            try {
              SecurityContextHolder.getContext().setAuthentication(context.authentication);
              command.run();
            } finally {
              SecurityContextHolder.getContext().setAuthentication(null);
            }
          }
          return null;
        };
        return (ThreadContext<Void>) execute(context.command, start);
      }

      @Override
      public @NotNull ScheduleBuilder<T> interval(@NotNull String cron) {
        context.scheduleType = ScheduleType.CRON;
        scheduleHandler = runnable -> taskScheduler.schedule(runnable, new CronTrigger(cron));
        return this;
      }

      @Override
      public @NotNull ScheduleBuilder<T> interval(@NotNull Duration duration) {
        context.scheduleType = ScheduleType.DELAY;
        context.period = duration;
        return this;
      }

      @Override
      public @NotNull ScheduleBuilder<T> throwOnError(boolean value) {
        context.throwOnError = value;
        return this;
      }

      @Override
      public @NotNull ScheduleBuilder<T> onError(@NotNull Consumer<Exception> errorListener) {
        context.errorListener = errorListener;
        return this;
      }

      @Override
      public @NotNull ScheduleBuilder<T> onFinally(@NotNull Runnable finallyListener) {
        context.finallyListener = finallyListener;
        return this;
      }

      @Override
      public @NotNull ScheduleBuilder<T> metadata(@NotNull String key, @NotNull Object value) {
        context.metadata.put(key, value);
        return this;
      }

      @Override
      public @NotNull ScheduleBuilder<T> tap(Consumer<ThreadContext<T>> handler) {
        handler.accept(context);
        return this;
      }

      @Override
      public @NotNull ScheduleBuilder<T> delay(@NotNull Duration duration) {
        context.delay = duration;
        return this;
      }

      @Override
      public @NotNull ScheduleBuilder<T> auth() {
        context.authentication = SecurityContextHolder.getContext().getAuthentication();
        return this;
      }

      @Override
      public @NotNull ScheduleBuilder<T> hideOnUI(boolean value) {
        context.showOnUI = !value;
        return this;
      }

      @Override
      public @NotNull ScheduleBuilder<T> hideOnUIAfterCancel(boolean value) {
        context.hideOnUIAfterCancel = value;
        return this;
      }

      @Override
      public @NotNull ScheduleBuilder<T> cancelOnError(boolean value) {
        context.cancelOnError = value;
        return this;
      }

      @Override
      public @NotNull ScheduleBuilder<T> linkLogFile(@NotNull Path logFile) {
        context.logFile = logFile;
        return this;
      }

      @Override
      public @NotNull ScheduleBuilder<T> valueListener(@NotNull String name, @NotNull ThrowingBiFunction<T, T, Boolean, Exception> valueListener) {
        context.addValueListener(name, valueListener);
        return this;
      }
    };
  }

  @Override
  public @NotNull ProcessBuilder processBuilder(@NotNull String name) {
    ProcessContextImpl processContext = new ProcessContextImpl();
    return new ProcessBuilder() {

      @Override
      public @NotNull ProcessBuilder setInputLoggerOutput(@Nullable Consumer<String> inputConsumer) {
        processContext.inputConsumer = inputConsumer;
        return this;
      }

      @Override
      public @NotNull ProcessBuilder setErrorLoggerOutput(@Nullable Consumer<String> errorConsumer) {
        processContext.errorConsumer = errorConsumer;
        return this;
      }

      @Override
      public @NotNull ProcessBuilder onStarted(@NotNull ThrowingConsumer<ProcessContext, Exception> startedHandler) {
        processContext.startedHandler = startedHandler;
        return this;
      }

      @Override
      public @NotNull ProcessBuilder onFinished(@NotNull ThrowingBiConsumer<@Nullable Exception, @NotNull Integer, Exception> finishHandler) {
        processContext.finishHandler = finishHandler;
        return this;
      }

      @Override
      public @NotNull ProcessBuilder attachLogger(@NotNull Logger log) {
        processContext.logger = log;
        return this;
      }

      @Override
      public @NotNull ProcessBuilder attachEntityStatus(@NotNull HasStatusAndMsg entity) {
        processContext.entity = entity;
        return this;
      }

      @NotNull
      @Override
      public ProcessBuilder workingDir(@NotNull Path dir) {
        processContext.workingDir = dir;
        return this;
      }

      @Override
      public @NotNull ProcessContext execute(@NotNull String... command) {
        processContext.threadContext = builder(name)
          .onError(processContext::onError)
          .execute(() -> processContext.run(name, command));
        return processContext;
      }
    };
  }

  @Override
  @SneakyThrows
  public @NotNull ThreadContext<Void> runDirectoryWatchdog(@NotNull Path dir, @NotNull ThrowingConsumer<WatchEvent<Path>, Exception> onUpdateCommand,
                                                           Kind<?>... eventsToListen) {
    String threadKey = "dir-watchdog-" + dir.getFileName().toString();
    if (isThreadExists(threadKey, true)) {
      throw new RuntimeException("Directory already watching");
    }
    if (!Files.isDirectory(dir)) {
      throw new IllegalArgumentException("Path: " + dir + " is not a directory");
    }
    if (eventsToListen.length == 0) {
      eventsToListen = new Kind<?>[]{ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY};
    }
    WatchService watchService = FileSystems.getDefault().newWatchService();
    dir.register(watchService, eventsToListen);

    return builder(threadKey)
      .interval(Duration.ofSeconds(1))
      .execute(() -> {
        WatchKey key;
        while ((key = watchService.take()) != null) {
          for (WatchEvent<?> event : key.pollEvents()) {
            try {
              onUpdateCommand.accept((WatchEvent<Path>) event);
            } catch (Exception ex) {
              log.error("Error during handle on watch directory {}/{}", event.kind(), event.context());
            }
          }
          key.reset();
        }
      });
  }

  @Override
  @SneakyThrows
  public @NotNull ThreadContext<Void> runFileWatchdog(@NotNull Path file, String key, @NotNull ThrowingRunnable<Exception> onUpdateCommand) {
    if (!Files.exists(file)) {
      throw new IllegalArgumentException("File: " + file + " does not exists");
    }
    if (!Files.isRegularFile(file)) {
      throw new IllegalArgumentException("File: " + file + " is not a regular file");
    }
    if (!Files.isReadable(file)) {
      throw new IllegalArgumentException("File: " + file + " is not readable");
    }

    ThreadContext<Void> fileWatchDog = (ThreadContext<Void>) this.schedulers.get("file-watchdog" + file);
    if (fileWatchDog != null) {
      ((ThreadContextImpl) fileWatchDog).simpleWorkUnitListeners.put(key, onUpdateCommand);
    } else {
      ScheduleBuilder<Void> scheduleBuilder = builder("file-watchdog" + file);
      final AtomicLong lastModified = new AtomicLong(Files.readAttributes(file, BasicFileAttributes.class).lastModifiedTime().toMillis());
      fileWatchDog = scheduleBuilder.intervalWithDelay(Duration.ofSeconds(10))
        .execute(context -> {
          checkFileModifiedAndFireHandler(file, lastModified, (ThreadContextImpl) context);
          return null;
        });
      ((ThreadContextImpl) fileWatchDog).simpleWorkUnitListeners = new ConcurrentHashMap<>();
      ((ThreadContextImpl) fileWatchDog).simpleWorkUnitListeners.put(key, onUpdateCommand);
    }
    return fileWatchDog;
  }

  @Override
  public @NotNull ProgressBuilder runWithProgress(@NotNull String key, boolean cancellable) {
    return new ProgressBuilderImpl(key).setCancellable(cancellable);
  }

  private <T> ContextBGPImpl.BatchRunContext<T> prepareBatchProcessContext(@NotNull String batchName, int threadsCount) {
    if (batchRunContextMap.containsKey(batchName)) {
      throw new IllegalStateException("Batch processes with name " + batchName + " already in progress");
    }
    ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(threadsCount);
    BatchRunContext<T> batchRunContext = new BatchRunContext<>(executor);
    batchRunContextMap.put(batchName, batchRunContext);
    return batchRunContext;
  }

  private <T> List<T> waitBatchToDone(
    @NotNull String batchName,
    @Nullable Duration maxTerminateTimeout,
    @NotNull Consumer<Integer> progressConsumer,
    @NotNull BatchRunContext<T> batchRunContext)
    throws InterruptedException, ExecutionException {
    ThreadPoolExecutor executor = batchRunContext.executor;
    executor.shutdown();
    long batchStarted = System.currentTimeMillis();
    long maxTerminatedMillis = maxTerminateTimeout == null ? 0 : maxTerminateTimeout.toMillis();
    try {
      while (!executor.isTerminated()) {
        try {
          //noinspection ResultOfMethodCallIgnored
          executor.awaitTermination(1, TimeUnit.SECONDS);
          long completedTaskCount = executor.getCompletedTaskCount();
          progressConsumer.accept((int) completedTaskCount);

          if (maxTerminatedMillis > 0 && System.currentTimeMillis() - batchStarted > maxTerminatedMillis) {
            log.warn("Exceeded await limit for batch run <{}> (Max {} sec.)", batchName, maxTerminatedMillis);
            executor.shutdownNow();
          }
        } catch (Exception ignore) {
          log.error("Error while await termination for batch run: <{}>", batchName);
          executor.shutdownNow();
        }
      }
      List<T> list = new ArrayList<>();
      for (Map.Entry<String, Future<T>> entry : batchRunContext.processes.entrySet()) {
        try {
          if (entry.getValue().isDone()) { // in case of cancellation from UI
            list.add(entry.getValue().get());
          }
        } catch (CancellationException ex) {
          log.warn("Process <{}> in batch <{}> has been cancelled. Ignore result", entry.getKey(), batchName);
        }
      }
      return list;
    } finally {
      batchRunContextMap.remove(batchName);
      log.info("Finish batch run <{}>", batchName);
    }
  }

  private void checkFileModifiedAndFireHandler(@NotNull Path file, AtomicLong lastModified, ThreadContextImpl context) throws Exception {
    BasicFileAttributes attr = Files.readAttributes(file, BasicFileAttributes.class);
    if (attr.lastModifiedTime().toMillis() > lastModified.get()) {
      lastModified.set(attr.lastModifiedTime().toMillis());
      Map<String, ThrowingRunnable<Exception>> listeners = context.simpleWorkUnitListeners;
      for (ThrowingRunnable<Exception> runnable : listeners.values()) {
        runnable.run();
      }
    }
  }

  private <T> void createSchedule(ThreadContextImpl<T> threadContext, @NotNull Function<Runnable, ScheduledFuture<?>> scheduleHandler) {

    this.cancelThread(threadContext.name);
    this.schedulers.put(threadContext.name, threadContext);

    Runnable runnable =
      () -> {
        try {
          threadContext.runCount++;
          threadContext.state = "TITLE.STARTED";
          threadContext.setRetValue(threadContext.getCommand().apply(threadContext));
          threadContext.state = "TITLE.FINISHED";
          if (threadContext.scheduleType == ScheduleType.SINGLE) {
            if (threadContext.scheduledFuture == null) {
              try {
                Thread.sleep(100); // hack: sleep 100ms to allow assign
              } catch (InterruptedException ignore) {
              }
              // threadContext.scheduledFuture = ....
            }
            threadContext.processFinished();
          }
        } catch (Exception ex) {
          if (ex instanceof CancellationException) {
            threadContext.state = "TITLE.FINISHED";
            threadContext.processFinished();
            return;
          }
          threadContext.state = "W.ERROR.FINISHED_WITH_ERROR";
          threadContext.error = CommonUtils.getErrorMessage(ex);
          if (threadContext.throwOnError) {
            throw new ServerException(threadContext.error);
          }

          log.error("Exception in thread: <{}>. Message: <{}>", threadContext.name, CommonUtils.getErrorMessage(ex), ex);
          context.ui().toastr().error(ex);
          if (threadContext.errorListener != null) {
            try {
              threadContext.errorListener.accept(ex);
            } catch (Exception uex) {
              log.error("Unexpected error in thread error listener <{}>", CommonUtils.getErrorMessage(uex));
            }
          }

          if (threadContext.cancelOnError || threadContext.scheduleType == ScheduleType.SINGLE) {
            threadContext.processFinished();
          }
        }
      };
    threadContext.scheduledFuture = (ScheduledFuture<T>) scheduleHandler.apply(runnable);
  }

  private ThreadContext<Void> createPingService() {
    return context
      .bgp()
      .builder("ping")
      .interval(Duration.ofSeconds(60))
      .cancelOnError(false)
      .execute(() -> {
        Map<String, List<Consumer<Boolean>>> pingers =
          pingMap.values().stream()
            .flatMap(value -> value.entrySet().stream())
            .collect(Collectors.groupingBy(
              Entry::getKey,
              Collectors.mapping(Entry::getValue, Collectors.toList())
            ));
        pingers.entrySet().stream().parallel().forEach(ping -> {
          AtomicBoolean isReachable = new AtomicBoolean(false);
          try {
            InetAddress address = InetAddress.getByName(ping.getKey());
            isReachable.set(address.isReachable(5000));
          } catch (IOException ignore) {
          }
          ping.getValue().forEach(booleanConsumer -> booleanConsumer.accept(isReachable.get()));
        });
      });
  }

  @RequiredArgsConstructor
  public enum ScheduleType {
    DELAY,
    RATE,
    SINGLE,
    CRON
  }

  @RequiredArgsConstructor
  private static class BatchRunContext<T> {

    private final ThreadPoolExecutor executor;
    private final Map<String, Future<T>> processes = new HashMap<>();
  }

  @Getter
  public static class ProcessContextImpl implements ProcessContext {

    private final Date creationTime = new Date();
    public @Nullable ThrowingConsumer<ProcessContext, Exception> startedHandler;
    public @Nullable ThrowingBiConsumer<Exception, Integer, Exception> finishHandler;
    public Process process;
    public ThreadContext<Void> threadContext;
    public Consumer<String> inputConsumer;
    public Consumer<String> errorConsumer;
    public @Nullable Logger logger;
    public @Nullable HasStatusAndMsg entity;
    public Path workingDir;
    private StreamGobbler streamGobbler;

    @Override
    public @NotNull String getName() {
      return threadContext.getName();
    }

    @Override
    public boolean isStopped() {
      return process == null || !process.isAlive();
    }

    @Override
    @SneakyThrows
    public void cancel(boolean sendSignal) {
      cancelProcess(sendSignal, false);
      threadContext.cancel(true);
    }

    private void cancelProcess(boolean sendSignal, boolean shutdownHook) {
      if (process != null) {
        if (sendSignal) {
          stopProcess(process, getName());
        }

        if (streamGobbler != null && !shutdownHook) {
          this.streamGobbler.stopStream(1000, 5000);
        }
        process.destroy();
      }
    }

    public void run(@NotNull String name, @NotNull String[] command) throws Exception {
      getLogger().info("[{}]: Starting process command: '{}'", name, command);
      if (workingDir != null) {
        process = Runtime.getRuntime().exec(command, null, workingDir.toFile());
      } else {
        process = Runtime.getRuntime().exec(command);
      }
      executeOnExitImpl("Process: " + name, () -> cancelProcess(true, true));
      if (entity != null) {
        entity.setStatusOnline();
      }
      if (startedHandler != null) {
        try {
          startedHandler.accept(this);
        } catch (Exception ex) {
          if (entity != null) {
            entity.setStatusError(ex);
          }
          getLogger().error("[{}]: Error during run process start handler. Err: {}", name, CommonUtils.getErrorMessage(ex));
          throw ex;
        }
      }

      if (errorConsumer != null || inputConsumer != null || logger != null) {
        if (errorConsumer == null) {
          if (logger != null) {
            errorConsumer = msg -> logger.error("[{}]: {}", name, msg);
          } else {
            errorConsumer = msg -> {
            };
          }
        }
        if (inputConsumer == null) {
          if (logger != null) {
            inputConsumer = msg -> logger.info("[{}]: {}", name, msg);
          } else {
            inputConsumer = msg -> {
            };
          }
        }
        streamGobbler = new StreamGobbler(name, inputConsumer, errorConsumer);
        streamGobbler.stream(process);
      }

      getLogger().info("[{}]: wait process to finish.", name);
      sleep(1000);
      int responseCode = process.waitFor();
      sleep(1000);
      executeFinishHandler(null, responseCode);
    }

    private @NotNull Logger getLogger() {
      return logger == null ? log : logger;
    }

    public void onError(Exception ex) {
      executeFinishHandler(ex, -1);
    }

    private void executeFinishHandler(Exception ex, int exitCode) {
      if (ex != null) {
        getLogger().error("[{}]: Process finished with status: '{}' and error: '{}'", getName(), exitCode, CommonUtils.getErrorMessage(ex));
        if (entity != null) {
          entity.setStatusError(ex);
        }
      } else {
        getLogger().info("[{}]: Process finished with status: {}", getName(), exitCode);
      }

      if (finishHandler != null) {
        try {
          finishHandler.accept(ex, exitCode);
        } catch (Exception fex) {
          getLogger().error("[{}]: Error occurred during finish process", getName(), fex);
        }
      }
      if (streamGobbler != null) {
        this.streamGobbler.stopStream(100, 1000);
      }
    }
  }

  @Setter
  @Accessors(chain = true)
  @RequiredArgsConstructor
  private class ProgressBuilderImpl implements ProgressBuilder {

    private @NotNull
    final String key;
    private boolean cancellable;
    private @Setter boolean logToConsole = true;
    private @Nullable Consumer<Exception> onFinally;
    private @Nullable Runnable onError;
    private @Nullable Exception errorIfExists;

    @Override
    public @NotNull ProgressBuilder onFinally(@Nullable Consumer<Exception> finallyBlock) {
      this.onFinally = finallyBlock;
      return this;
    }

    @Override
    public @NotNull ProgressBuilder onError(@Nullable Runnable errorBlock) {
      this.onError = errorBlock;
      return this;
    }

    @Override
    @SneakyThrows
    public <R> @NotNull ThreadContext<R> execute(@NotNull ThrowingFunction<ProgressBar, R, Exception> command) {
      if (errorIfExists != null && isThreadExists(key, true)) {
        throw errorIfExists;
      }
      ScheduleBuilder<R> builder = builder(key);
      return builder.execute(arg -> {
        ProgressBar progressBar = (progress, message, error) -> {
          if (logToConsole) {
            log.info("Progress: {}", message);
          }
          context().ui().progress().update(key, progress, message, cancellable);
        };
        progressBar.progress(0, key);
        Exception exception = null;
        try {
          return command.apply(progressBar);
        } catch (Exception ex) {
          exception = ex;
          if (onError != null) {
            onError.run();
          }
        } finally {
          progressBar.done();
          if (onFinally != null) {
            onFinally.accept(exception);
          }
        }
        return null;
      });
    }
  }

  @Getter
  public class ThreadContextImpl<T> implements ThreadContext<T> {

    private final JSONObject metadata = new JSONObject();
    private final Date creationTime = new Date();
    // in case if start = false
    public Function<Runnable, ScheduledFuture<?>> postponeScheduleHandler;
    public Runnable finallyListener;
    private boolean throwOnError;
    private Authentication authentication;
    private Path logFile;
    private Duration delay;
    private String name;
    private ThrowingFunction<ThreadContext<T>, T, Exception> command;
    private ScheduleType scheduleType;
    private Duration period;
    private boolean showOnUI = true;
    private boolean hideOnUIAfterCancel = true;
    private T retValue;
    private String error;
    private ScheduledFuture<T> scheduledFuture;
    @Setter
    private String state;
    @Setter
    private String description;
    private boolean stopped;
    @Setter
    private int runCount;
    @Setter
    private boolean cancelOnError = true;
    private Map<String, ThrowingBiFunction<T, T, Boolean, Exception>> valueListeners;
    private Map<String, ThrowingRunnable<Exception>> simpleWorkUnitListeners;
    private Consumer<Exception> errorListener;

    private LastBytesBuffer info;
    private StreamGobbler streamGobbler;

    @Override
    public String getTimeToNextSchedule() {
      if (scheduledFuture == null || (period == null && delay == null)) {
        return null;
      }
      return Duration.ofMillis(scheduledFuture.getDelay(TimeUnit.MILLISECONDS)).truncatedTo(ChronoUnit.SECONDS).toString().substring(2);
    }

    @Override
    public Object getMetadata(String key) {
      return metadata.get(key);
    }

    @Override
    public void setMetadata(@NotNull String key, @NotNull Object value) {
      metadata.put(key, value);
    }

    @Override
    public void cancel(boolean mayInterruptIfRunning) {
      cancelProcessInternal(mayInterruptIfRunning);
    }

    @Override
    public void reset() {
      createSchedule(this, this.postponeScheduleHandler);
    }

    @Override
    @SneakyThrows
    public T await(@NotNull Duration timeout) {
      scheduledFuture.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
      return getRetValue();
    }

    @Override
    public boolean addValueListener(@NotNull String name, @NotNull ThrowingBiFunction<T, T, Boolean, Exception> valueListener) {
      if (valueListeners == null) {
        valueListeners = new ConcurrentHashMap<>();
      }
      if (valueListeners.containsKey(name)) {
        log.warn("Unable to add run/schedule listener with name <{}> Listener already exists", name);
        return false;
      }
      valueListeners.put(name, valueListener);
      return true;
    }

    @Override
    public boolean removeValueListener(@NotNull String name) {
      if (valueListeners != null) {
        return valueListeners.remove(name) != null;
      }
      return false;
    }

    public void setRetValue(T value) {
      T oldValue = this.retValue;
      this.retValue = value;

      // fire listeners if require
      if (this.valueListeners != null) {
        for (Iterator<ThrowingBiFunction<T, T, Boolean, Exception>> iterator = this.valueListeners.values().iterator(); iterator.hasNext(); ) {
          ThrowingBiFunction<T, T, Boolean, Exception> fn = iterator.next();
          try {
            if (Boolean.TRUE.equals(fn.apply(value, oldValue))) {
              iterator.remove();
            }
          } catch (Exception ex) {
            log.error("Error while fire listener for run/schedule <" + this.name + ">", ex);
            iterator.remove();
          }
        }
        // clean if not require anymore
        if (this.valueListeners.isEmpty()) {
          this.valueListeners = null;
        }
      }
    }

    // not support batch processes now
    public void rename(@NotNull String newName) {
      this.name = newName;
      if (schedulers.containsKey(newName)) {
        throw new IllegalArgumentException("Process with name: '" + newName + "' already exists");
      }
      schedulers.put(newName, this);
      schedulers.remove(name);
    }

    @Override
    public void writeStreamInfo(byte[] content) {
      getInfoBuffer().append(content);
    }

    @Override
    public void attachInputStream(@NotNull InputStream inputStream, @NotNull InputStream errorStream) {
      this.streamGobbler = new StreamGobbler(getName(),
        s -> getInfoBuffer().append(s.getBytes()),
        s -> getInfoBuffer().append(s.getBytes()));
    }

    public synchronized LastBytesBuffer getInfoBuffer() {
      if (info == null) {
        info = new LastBytesBuffer(10_000);
      }
      return info;
    }

    private void cancelProcessInternal(boolean mayInterruptIfRunning) {
      if (scheduledFuture != null) {
        if (scheduledFuture.isCancelled() || scheduledFuture.isDone() || scheduledFuture.cancel(mayInterruptIfRunning)) {
          processFinished();
        }
      }
    }

    private void processFinished() {
      stopped = true;
      if (streamGobbler != null) {
        streamGobbler.stopStream(100, 100);
      }
      if (!showOnUI || hideOnUIAfterCancel) {
        ContextBGPImpl.this.schedulers.remove(name);
      }
      if (finallyListener != null) {
        try {
          finallyListener.run();
        } catch (Exception uex) {
          log.error("Unexpected error in thread finally listener <{}>", CommonUtils.getErrorMessage(uex));
        }
      }
    }
  }
}
