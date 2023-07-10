package org.homio.app.manager.bgp;

import com.pivovarit.function.ThrowingRunnable;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.homio.api.EntityContext;
import org.homio.api.EntityContextBGP.ScheduleBuilder;
import org.homio.api.EntityContextBGP.ThreadContext;
import org.homio.api.model.Status;
import org.homio.app.manager.common.impl.EntityContextBGPImpl;
import org.homio.app.utils.InternalUtil;

@Log4j2
public class InternetAvailabilityBgpService {

    private final AtomicBoolean internetUp = new AtomicBoolean(false);
    private final EntityContext entityContext;
    private ThreadContext<Boolean> internetThreadContext;

    public InternetAvailabilityBgpService(EntityContext entityContext, EntityContextBGPImpl entityContextBGP) {
        this.entityContext = entityContext;
        ScheduleBuilder<Boolean> builder = entityContextBGP.builder("internet-test");
        Duration interval = entityContext.setting().getEnvRequire("interval-internet-test", Duration.class, Duration.ofSeconds(10), true);
        ScheduleBuilder<Boolean> internetAccessBuilder = builder
            .interval(interval).delay(interval).interval(interval)
            .valueListener("internet-hardware-event", (isInternetUp, isInternetWasUp) -> {
                if (isInternetUp != isInternetWasUp) {
                    internetUp.set(isInternetUp);
                    entityContext.event().fireEventIfNotSame("internet-status",
                        isInternetUp ? Status.ONLINE : Status.OFFLINE);
                }
                return null;
            })
            .tap(context -> internetThreadContext = context);

        internetAccessBuilder.execute(context -> InternalUtil.checkUrlAccessible() != null);
    }

    @SneakyThrows
    public void addRunOnceOnInternetUpListener(String name, ThrowingRunnable<Exception> command) {
        if (internetUp.get()) {
            executeInternetUpCommand(name, command);
            return; // skip adding listener if internet already up
        }
        internetThreadContext.addValueListener(name, (isInternetUp, ignore) -> {
            if (isInternetUp) {
                executeInternetUpCommand(name, command);
                return true;
            }
            return false;
        });
    }

    private void executeInternetUpCommand(String name, ThrowingRunnable<Exception> command) {
        entityContext.bgp().builder(name).execute(() -> {
            log.info("Internet up. Run <" + name + "> listener.");
            try {
                command.run();
            } catch (Exception ex) {
                log.error("Error occurs while run command: " + name, ex);
            }
        });
    }
}
