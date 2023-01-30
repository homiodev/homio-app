package org.touchhome.app.manager.bgp;

import com.pivovarit.function.ThrowingRunnable;
import java.time.Duration;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.touchhome.app.config.TouchHomeProperties;
import org.touchhome.app.utils.InternalUtil;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.EntityContextBGP.ScheduleBuilder;
import org.touchhome.bundle.api.EntityContextBGP.ThreadContext;
import org.touchhome.bundle.api.model.Status;

@Log4j2
@Service
public class InternetAvailabilityBgpService implements BgpService {

    private static ThreadContext<Boolean> internetThreadContext;

    public InternetAvailabilityBgpService(EntityContext entityContext, TouchHomeProperties touchHomeProperties) {
        ScheduleBuilder<Boolean> builder = entityContext.bgp().builder("internet-test");
        Duration interval = touchHomeProperties.getInternetTestInterval();
        ScheduleBuilder<Boolean> internetAccessBuilder = builder.interval(interval).delay(interval).interval(interval)
                                                                .tap(context -> internetThreadContext = context);
        internetThreadContext.addValueListener("internet-hardware-event", (isInternetUp, isInternetWasUp) -> {
            if (isInternetUp != isInternetWasUp) {
                entityContext.event().fireEventIfNotSame("internet-status", isInternetUp ? Status.ONLINE : Status.OFFLINE);
                if (isInternetUp) {
                    entityContext.ui().addBellInfoNotification("internet-connection", "Internet Connection", "Internet up");
                } else {
                    entityContext.ui().addBellErrorNotification("internet-connection", "Internet Connection", "Internet down");
                }
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
