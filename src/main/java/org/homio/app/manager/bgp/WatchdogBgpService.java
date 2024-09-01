package org.homio.app.manager.bgp;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.LogManager;
import org.homio.api.model.Status;
import org.homio.api.service.EntityService;
import org.homio.api.service.EntityService.WatchdogService;
import org.homio.app.manager.common.impl.ContextBGPImpl;

@Log4j2
public class WatchdogBgpService {

    private final Map<String, WatchdogService> watchdogServiceMap = new ConcurrentHashMap<>();

    public WatchdogBgpService(ContextBGPImpl ContextBGP) {
        ContextBGP.builder("watchdog")
                .intervalWithDelay(Duration.ofSeconds(60))
                .execute(this::runWatchDogService);
    }

    public void addWatchDogService(String key, WatchdogService watchdogService) {
        watchdogServiceMap.put(key, watchdogService);
    }

    public void removeWatchDogService(String entityID) {
        watchdogServiceMap.remove(entityID);
    }

    @SneakyThrows
    private void runWatchDogService() {
        List<CompletableFuture<Void>> restartingServices = new ArrayList<>();
        for (Entry<String, WatchdogService> entry : watchdogServiceMap.entrySet()) {
            if (entry.getValue() != null) {
                String reason = isRequireRestartService(entry);
                if (reason != null) {
                    LogManager.getLogger(entry.getValue())
                            .warn("Restarting service: {}. Reason: {}", entry.getKey(), reason);
                    restartingServices.add(CompletableFuture.runAsync(entry.getValue()::restartService));
                }
            }
        }
        if (!restartingServices.isEmpty()) {
            // wait all watchdog services to finish value
            CompletableFuture.allOf(restartingServices.toArray(new CompletableFuture[0])).get();
            log.info("Finished restart services");
        }
    }

    private static String isRequireRestartService(Entry<String, WatchdogService> entry) {
        try {
            if (entry.getValue() instanceof EntityService.ServiceInstance<?> si) {
                // avoid restart if first initialization not passed yet
                if (si.getEntityHashCode() == 0) {
                    return null;
                }
                if (!si.getEntity().isStart()) {
                    return null;
                }
                Set<String> errors = si.getEntity().getConfigurationErrors();
                if (errors != null && !errors.isEmpty()) {
                    return null;
                }
            }
            return entry.getValue().isRequireRestartService();
        } catch (Exception ex) {
            return null;
        }
    }

    /*private void assembleWatchdogServices() {
        for (Entry<String, Object> servicePair : EntityService.entityToService.entrySet()) {
            if (!watchdogServiceMap.containsKey(servicePair.getKey())) {
                ServiceInstance service = (ServiceInstance) servicePair.getValue();
                watchdogServiceMap.put(servicePair.getKey(), service.getWatchdog());
            }
        }
    }*/
}
