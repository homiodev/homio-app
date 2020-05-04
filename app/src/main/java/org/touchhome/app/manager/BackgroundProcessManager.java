package org.touchhome.app.manager;

import lombok.extern.log4j.Log4j2;
import org.reflections.Reflections;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.touchhome.app.json.bgp.BackgroundProcessServiceJSON;
import org.touchhome.bundle.api.thread.BackgroundProcessService;
import org.touchhome.bundle.api.thread.BackgroundProcessStatus;
import org.touchhome.bundle.api.util.SmartUtils;

import javax.annotation.PreDestroy;
import java.lang.reflect.Constructor;
import java.util.*;
import java.util.concurrent.*;

import static org.touchhome.bundle.api.thread.BackgroundProcessStatus.*;

@Log4j2
@Component
public class BackgroundProcessManager {

    @Value("${backgroundProcessThreadsMaxCount:1000}")
    private int backgroundProcessThreadsMaxCount;

    private static Set<Class<? extends BackgroundProcessService>> backgroundProcessServices = new Reflections("org.touchhome").getSubTypesOf(BackgroundProcessService.class);

    private ScheduledThreadPoolExecutor scheduledThreadPoolExecutor;

    private Map<String, BackgroundProcessDescriptor> threads = new ConcurrentHashMap<>();

    public void postConstruct() {
        for (Class<? extends BackgroundProcessService> aClass : backgroundProcessServices) {
            try {
                Constructor<? extends BackgroundProcessService> constructor = aClass.getDeclaredConstructor();
                fireIfNeedRestart(constructor.newInstance());
            } catch (Exception ignore) {

            }
        }
    }

    private ScheduledThreadPoolExecutor getScheduledThreadPoolExecutor() {
        if (scheduledThreadPoolExecutor == null) {
            scheduledThreadPoolExecutor = (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(backgroundProcessThreadsMaxCount);
            scheduledThreadPoolExecutor.setRemoveOnCancelPolicy(true);
        }
        return scheduledThreadPoolExecutor;
    }

    public boolean isRunning(String backgroundProcessDescriptorID) {
        return threads.containsKey(backgroundProcessDescriptorID) && !threads.get(backgroundProcessDescriptorID).scheduledFuture.isDone();
    }

    public boolean isRunning(BackgroundProcessService backgroundProcessService) {
        return backgroundProcessService != null && isRunning(backgroundProcessService.getId());
    }

    public boolean cancelTask(BackgroundProcessService backgroundProcessService, BackgroundProcessStatus backgroundProcessStatus, String errorMessage) {
        return cancelTask(backgroundProcessService.getId(), backgroundProcessStatus, errorMessage);
    }

    public boolean cancelTask(String backgroundProcessDescriptorID, BackgroundProcessStatus backgroundProcessStatus, String errorMessage) {
        boolean cancelled = false;
        if (threads.containsKey(backgroundProcessDescriptorID)) {
            BackgroundProcessDescriptor backgroundProcessDescriptor = threads.get(backgroundProcessDescriptorID);
            BackgroundProcessService service = backgroundProcessDescriptor.backgroundProcessService;

            log.info("Cancel task <{}> ", service);
            ScheduledFuture scheduledFuture = threads.get(backgroundProcessDescriptorID).scheduledFuture;
            if (scheduledFuture.isCancelled() || scheduledFuture.isDone() || scheduledFuture.cancel(true)) {
                if (backgroundProcessStatus != null) {
                    service.setStatus(backgroundProcessStatus, errorMessage);
                }
                threads.remove(backgroundProcessDescriptorID);
                service.cancelService();
                cancelled = true;
            }
        }
        return cancelled;
    }

    public int getTimeInPercentageToNextSchedule(BackgroundProcessService backgroundProcessService) {
        if (backgroundProcessService.getPeriod() == 0) {
            return 0;
        }
        long delay = threads.get(backgroundProcessService.getId()).scheduledFuture.getDelay(TimeUnit.MILLISECONDS);
        return 100 - (int) (100 * delay / backgroundProcessService.getPeriod());
    }

    public void fireStart(BackgroundProcessService backgroundProcessService) {
        scheduleAtFixedRate(backgroundProcessService);
    }

    public void fireIfNeedRestart(BackgroundProcessService backgroundProcessService) {
        if (isRunning(backgroundProcessService.getId())) {
            if (!backgroundProcessService.canWorkSafe() || !backgroundProcessService.shouldStartNow()) {
                cancelTask(backgroundProcessService, BackgroundProcessStatus.STOP, "Inconsistency. BGP should be stopped");
            } else {
                threads.keySet().stream().filter(bgpID -> bgpID.equals(backgroundProcessService.getId()) &&
                        threads.get(bgpID).backgroundProcessService.getPeriod() != backgroundProcessService.getPeriod()).forEach(bgp -> restartTask(backgroundProcessService));
            }
        } else {
            if (backgroundProcessService.shouldStartNow() && backgroundProcessService.canWorkSafe()) {
                scheduleAtFixedRate(backgroundProcessService);
            } else {
                // if after restarting we don't need run script and his status FAILED - we may change this status
                if (backgroundProcessService.getStatus() == FAILED) {
                    backgroundProcessService.setStatus(BackgroundProcessStatus.STOP, null);
                }
            }
        }
    }

    public BackgroundProcessService getBackgroundProcessByDescriptorID(String backgroundProcessDescriptorID) {
        return threads.containsKey(backgroundProcessDescriptorID) ? threads.get(backgroundProcessDescriptorID).backgroundProcessService : null;
    }

    public <T extends BackgroundProcessService> T getBackgroundProcessByClass(Class<T> aClass) {
        return (T) getBackgroundProcessByDescriptorID(aClass.getSimpleName());
    }


    private void scheduleAtFixedRate(BackgroundProcessService backgroundProcessService) {
        if (!threads.containsKey(backgroundProcessService.getId())) {
            if (backgroundProcessService.getStatus() == RESTARTING) {
                log.info("Restarting task <{}>", backgroundProcessService);
            } else {
                log.info("Starting task <{}>", backgroundProcessService);
            }
            backgroundProcessService.beforeStartService();
            Runnable command = () -> {
                try {
                    backgroundProcessService.run();
                } catch (Exception ex) {
                    cancelTask(backgroundProcessService, FAILED, SmartUtils.getErrorMessage(ex));
                }
            };

            ScheduledFuture<?> scheduledFuture = null;
            if (backgroundProcessService.getPeriod() == 0) { // run only once
                scheduledFuture = getScheduledThreadPoolExecutor().schedule(command, 0, TimeUnit.MILLISECONDS);
            } else if (backgroundProcessService.getPeriod() > 0) { // run periodically only if period more than 0

                if (backgroundProcessService.getScheduleType().equals(BackgroundProcessService.ScheduleType.Delay)) {
                    scheduledFuture = getScheduledThreadPoolExecutor().scheduleWithFixedDelay(command, 0, backgroundProcessService.getPeriod(), TimeUnit.MILLISECONDS);
                } else {
                    scheduledFuture = getScheduledThreadPoolExecutor().scheduleAtFixedRate(command, 0, backgroundProcessService.getPeriod(), TimeUnit.MILLISECONDS);
                }
            }

            backgroundProcessService.fireStartEvent();

            if (scheduledFuture != null) {
                threads.put(backgroundProcessService.getId(), new BackgroundProcessDescriptor(backgroundProcessService, scheduledFuture));
            }
        }
    }

    private void restartTask(BackgroundProcessService backgroundProcessService) {
        cancelTask(backgroundProcessService, RESTARTING, null);
        scheduleAtFixedRate(backgroundProcessService);
    }

    @PreDestroy
    public void preDestroy() {
        // TODO: somehow calls "delete from DeviceBaseEntity_DeviceBaseEntity where W1DeviceEntity_id=?"
        /*for (AbstractJSBackgroundProcessService jsBackgroundProcessService : threads.keySet()) {
            cancelTask(jsBackgroundProcessService, BackgroundProcessStatusJSON.STOP, "ServerShutdown");
        }*/
        getScheduledThreadPoolExecutor().shutdown();
        try {
            if (!getScheduledThreadPoolExecutor().awaitTermination(10, TimeUnit.SECONDS)) {
                log.warn("Can't stop all background processes in 10 seconds. Force stopping...");
                log.warn("Stopped bgp size: " + getScheduledThreadPoolExecutor().shutdownNow().size());
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public List<BackgroundProcessServiceJSON> getAllBackgroundProcesses() {
        List<BackgroundProcessServiceJSON> backgroundServiceEntities = new ArrayList<>();
        for (BackgroundProcessDescriptor backgroundProcessDescriptor : threads.values()) {
            BackgroundProcessService backgroundProcessService = backgroundProcessDescriptor.backgroundProcessService;
            BackgroundProcessServiceJSON backgroundProcessServiceJSON = new BackgroundProcessServiceJSON();
            backgroundProcessServiceJSON.setEntityID(backgroundProcessService.getId());
            backgroundProcessServiceJSON.setProcessName(backgroundProcessService.getName());
            backgroundProcessServiceJSON.setDescription(backgroundProcessService.getDescription());
            backgroundProcessServiceJSON.setState(backgroundProcessService.getState());

            if (backgroundProcessService.getPeriod() != 0 &&
                    (BackgroundProcessService.ScheduleType.Rate.equals(backgroundProcessService.getScheduleType())
                            || EXECUTED.equals(backgroundProcessService.getStatus()))) {
                long delay = backgroundProcessDescriptor.scheduledFuture.getDelay(TimeUnit.MILLISECONDS);
                backgroundProcessServiceJSON.setNextTimeToExecute(new Date(System.currentTimeMillis() + delay));
            }
            backgroundProcessServiceJSON.setScheduleType(backgroundProcessService.getScheduleType());
            backgroundProcessServiceJSON.setStatus(backgroundProcessService.getStatus());
            backgroundProcessServiceJSON.setErrorMessage(backgroundProcessService.getErrorMessage());
            backgroundProcessServiceJSON.setPeriod(backgroundProcessService.getPeriod());

            backgroundProcessServiceJSON.setCreationTime(backgroundProcessService.getCreationTime());

            backgroundServiceEntities.add(backgroundProcessServiceJSON);
        }
        return backgroundServiceEntities;
    }

    public void cancelTasksByType(Class<? extends BackgroundProcessService> type, BackgroundProcessStatus status) {
        for (Map.Entry<String, BackgroundProcessDescriptor> item : threads.entrySet()) {
            if (item.getValue().backgroundProcessService.getClass().isAssignableFrom(type)) {
                this.cancelTask(item.getValue().backgroundProcessService, status, null);
            }
        }
    }

    private static class BackgroundProcessDescriptor {
        private BackgroundProcessService backgroundProcessService;
        private ScheduledFuture scheduledFuture;

        BackgroundProcessDescriptor(BackgroundProcessService backgroundProcessService, ScheduledFuture scheduledFuture) {
            this.backgroundProcessService = backgroundProcessService;
            this.scheduledFuture = scheduledFuture;
        }
    }
}
