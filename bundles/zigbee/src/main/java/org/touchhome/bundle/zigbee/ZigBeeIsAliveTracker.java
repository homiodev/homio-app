package org.touchhome.bundle.zigbee;

import lombok.extern.log4j.Log4j2;

import java.util.Map;
import java.util.concurrent.*;

@Log4j2
class ZigBeeIsAliveTracker {

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private Map<ZigBeeDevice, Integer> handlerIntervalMapping = new ConcurrentHashMap<>();
    private Map<ZigBeeDevice, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    void addHandler(ZigBeeDevice zigBeeDevice, int expectedUpdateInterval) {
        log.debug("{}: Add IsAlive Tracker", zigBeeDevice.getNodeIeeeAddress());
        handlerIntervalMapping.put(zigBeeDevice, expectedUpdateInterval);
        resetTimer(zigBeeDevice);
    }

    void removeHandler(ZigBeeDevice zigBeeDevice) {
        log.debug("{}: Remove IsAlive Tracker", zigBeeDevice.getNodeIeeeAddress());
        cancelTask(zigBeeDevice);
        handlerIntervalMapping.remove(zigBeeDevice);
    }

    synchronized void resetTimer(ZigBeeDevice zigBeeDevice) {
        log.debug("{}: Reset timeout for handler with zigBeeDevice", zigBeeDevice.getNodeIeeeAddress());
        cancelTask(zigBeeDevice);
        scheduleTask(zigBeeDevice);
    }

    private void scheduleTask(ZigBeeDevice zigBeeDevice) {
        ScheduledFuture<?> existingTask = scheduledTasks.get(zigBeeDevice);
        if (existingTask == null && handlerIntervalMapping.containsKey(zigBeeDevice)) {
            int interval = handlerIntervalMapping.get(zigBeeDevice);
            log.debug("{}: Scheduling timeout task for zigBeeDevice in {} seconds", zigBeeDevice.getNodeIeeeAddress(), interval);
            ScheduledFuture<?> task = scheduler.schedule(() -> {
                log.debug("{}: Timeout has been reached for zigBeeDevice", zigBeeDevice.getNodeIeeeAddress());
                zigBeeDevice.aliveTimeoutReached();
                scheduledTasks.remove(zigBeeDevice);
            }, interval, TimeUnit.SECONDS);

            scheduledTasks.put(zigBeeDevice, task);
        }
    }

    private void cancelTask(ZigBeeDevice zigBeeDevice) {
        ScheduledFuture<?> task = scheduledTasks.get(zigBeeDevice);
        if (task != null) {
            log.debug("{}: Canceling timeout task for zigBeeDevice", zigBeeDevice.getNodeIeeeAddress());
            task.cancel(true);
            scheduledTasks.remove(zigBeeDevice);
        }
    }
}
