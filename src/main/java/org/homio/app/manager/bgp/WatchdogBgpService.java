package org.homio.app.manager.bgp;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.homio.app.manager.common.impl.EntityContextBGPImpl;
import org.homio.bundle.api.service.EntityService;
import org.homio.bundle.api.service.EntityService.ServiceInstance;
import org.homio.bundle.api.service.EntityService.WatchdogService;

@Log4j2
public class WatchdogBgpService {

    private final Map<String, WatchdogService> watchdogServiceMap = new HashMap<>();

    public WatchdogBgpService(EntityContextBGPImpl entityContextBGP) {
        entityContextBGP.builder("watchdog")
                        .interval(Duration.ofMinutes(1))
                        .delay(Duration.ofMinutes(1))
                        .execute(this::runWatchDogService);
    }

    @SneakyThrows
    private void runWatchDogService() {
        assembleWatchdogServices();
        List<CompletableFuture<Void>> restartingServices = new ArrayList<>();
        for (Entry<String, WatchdogService> entry : watchdogServiceMap.entrySet()) {
            if (entry.getValue() != null && entry.getValue().isRequireRestartService()) {
                log.info("Restarting service: {}", entry.getKey());
                restartingServices.add(CompletableFuture.runAsync(entry.getValue()::restartService));
            }
        }
        if (!restartingServices.isEmpty()) {
            // wait all watchdog services to finish value
            CompletableFuture.allOf(restartingServices.toArray(new CompletableFuture[0])).get();
            log.info("Finished restart services");
        }
    }

    private void assembleWatchdogServices() {
        for (Entry<String, Object> servicePair : EntityService.entityToService.entrySet()) {
            if (!watchdogServiceMap.containsKey(servicePair.getKey())) {
                ServiceInstance service = (ServiceInstance) servicePair.getValue();
                watchdogServiceMap.put(servicePair.getKey(), service.getWatchdog());
            }
        }
    }
}
