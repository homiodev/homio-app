package org.homio.app.manager.bgp;

import com.pivovarit.function.ThrowingRunnable;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.homio.api.Context;
import org.homio.api.ContextBGP.ScheduleBuilder;
import org.homio.api.ContextBGP.ThreadContext;
import org.homio.api.ContextNetwork;
import org.homio.api.state.OnOffType;
import org.homio.app.manager.common.impl.ContextBGPImpl;

import java.net.ConnectException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

@Log4j2
public class InternetAvailabilityBgpService {

    @Getter
    private final AtomicBoolean internetUp = new AtomicBoolean(false);
    private final Context context;
    private ThreadContext<Boolean> internetThreadContext;

    public InternetAvailabilityBgpService(Context context, ContextBGPImpl ContextBGP) {
        this.context = context;
        ScheduleBuilder<Boolean> builder = ContextBGP.builder("internet-test");
        Duration interval = context.setting().getEnvRequire("interval-internet-test", Duration.class, Duration.ofSeconds(15), true);
        ScheduleBuilder<Boolean> internetAccessBuilder = builder
                .intervalWithDelay(interval)
                .valueListener("internet-hardware-event", (isInternetUp, isInternetWasUp) -> {
                    if (isInternetUp != isInternetWasUp) {
                        internetUp.set(isInternetUp);
                        context.event().fireEventIfNotSame("internet-status", OnOffType.of(isInternetUp));
                    }
                    return null;
                })
                .tap(tc -> internetThreadContext = tc);

        internetAccessBuilder.execute(tc -> pingTest());
    }

    private static boolean pingTest() throws ConnectException {
        try {
            return ContextNetwork.ping("google.com", 80) != null;
        } catch (Exception ignore) {
            return false;
        }
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
        context.bgp().builder(name).execute(() -> {
            log.info("Internet up. Run <" + name + "> listener.");
            try {
                command.run();
            } catch (Exception ex) {
                log.error("Error occurs while run command: " + name, ex);
            }
        });
    }
}
