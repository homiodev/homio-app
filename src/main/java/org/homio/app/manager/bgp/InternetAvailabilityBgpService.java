package org.homio.app.manager.bgp;

import com.pivovarit.function.ThrowingRunnable;
import java.time.Duration;
import lombok.extern.log4j.Log4j2;
import org.homio.api.EntityContext;
import org.homio.api.EntityContextBGP.ScheduleBuilder;
import org.homio.api.EntityContextBGP.ThreadContext;
import org.homio.api.model.Status;
import org.homio.app.config.AppProperties;
import org.homio.app.manager.common.impl.EntityContextBGPImpl;
import org.homio.app.utils.InternalUtil;

@Log4j2
public class InternetAvailabilityBgpService {

    private ThreadContext<Boolean> internetThreadContext;

    public InternetAvailabilityBgpService(EntityContext entityContext, AppProperties appProperties, EntityContextBGPImpl entityContextBGP) {
        ScheduleBuilder<Boolean> builder = entityContextBGP.builder("internet-test");
        Duration interval = appProperties.getInternetTestInterval();
        ScheduleBuilder<Boolean> internetAccessBuilder = builder
            .interval(interval).delay(interval).interval(interval)
            .valueListener("internet-hardware-event", (isInternetUp, isInternetWasUp) -> {
                if (isInternetUp != isInternetWasUp) {
                    entityContext.event().fireEventIfNotSame("internet-status",
                        isInternetUp ? Status.ONLINE : Status.OFFLINE);
                }
                return null;
            })
            .tap(context -> internetThreadContext = context);

        internetAccessBuilder.execute(context -> InternalUtil.checkUrlAccessible() != null);
    }

    public void addRunOnceOnInternetUpListener(String name, ThrowingRunnable<Exception> command) {
        internetThreadContext.addValueListener(name, (isInternetUp, ignore) -> {
            if (isInternetUp) {
                log.info("Internet up. Run <" + name + "> listener.");
                try {
                    command.run();
                } catch (Exception ex) {
                    log.error("Error occurs while run command: " + name, ex);
                } finally {
                    // remove listener after fire
                    internetThreadContext.removeValueListener(name);
                }
                return true;
            }
            return false;
        });
    }
}
