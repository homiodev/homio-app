package org.homio.app.manager.bgp;

import com.pivovarit.function.ThrowingRunnable;
import java.time.Duration;
import lombok.extern.log4j.Log4j2;
import org.homio.app.config.AppProperties;
import org.homio.app.utils.InternalUtil;
import org.homio.bundle.api.EntityContext;
import org.homio.bundle.api.EntityContextBGP.ScheduleBuilder;
import org.homio.bundle.api.EntityContextBGP.ThreadContext;
import org.homio.bundle.api.model.Status;
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class InternetAvailabilityBgpService implements BgpService {

    private static ThreadContext<Boolean> internetThreadContext;

    public InternetAvailabilityBgpService(EntityContext entityContext, AppProperties appProperties) {
        ScheduleBuilder<Boolean> builder = entityContext.bgp().builder("internet-test");
        Duration interval = appProperties.getInternetTestInterval();
        ScheduleBuilder<Boolean> internetAccessBuilder = builder.interval(interval).delay(interval).interval(interval)
                                                                .tap(context -> internetThreadContext = context);
        internetThreadContext.addValueListener("internet-hardware-event", (isInternetUp, isInternetWasUp) -> {
            if (isInternetUp != isInternetWasUp) {
                entityContext.event().fireEventIfNotSame("internet-status", isInternetUp ? Status.ONLINE : Status.OFFLINE);
            }
            return null;
        });

        internetAccessBuilder.execute(context -> InternalUtil.checkUrlAccessible() != null);
    }

    @Override
    public void startUp() {
    }

    public static void addRunOnceOnInternetUpListener(String name, ThrowingRunnable<Exception> command) {
        internetThreadContext.addValueListener(name, (isInternetUp, ignore) -> {
            if (isInternetUp) {
                log.info("Internet up. Run <" + name + "> listener.");
                try {
                    command.run();
                } catch (Exception ex) {
                    log.error("Error occurs while run command: " + name, ex);
                }
                return true;
            }
            return false;
        });
    }
}
