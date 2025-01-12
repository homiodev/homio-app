package org.homio.app.service.scan;

import lombok.extern.log4j.Log4j2;
import org.homio.api.Context;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.HasEntityIdentifier;
import org.homio.api.service.discovery.ItemDiscoverySupport.DeviceScannerResult;
import org.homio.api.ui.UIActionHandler;
import org.homio.api.util.CommonUtils;
import org.homio.api.util.FlowMap;
import org.homio.hquery.ProgressBar;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Base class for scan devices, controllers, camera, etc...
 */
@Log4j2
public abstract class BaseItemsDiscovery implements UIActionHandler {

  @Override
  public ActionResponseModel handleAction(Context context, JSONObject ignore) {
    List<DevicesScanner> scanners = getScanners(context);
    if (scanners.isEmpty()) {
      return ActionResponseModel.showWarn("SCAN.NO_PROCESSES");
    }

    log.info("Start batch scanning for <{}>", getBatchName());

    context.bgp().runInBatch(getBatchName(), Duration.ofSeconds(getMaxTimeToWaitInSeconds()), scanners,
      scanner -> {
        log.info("Start scan in thread <{}>", scanner.name);
        AtomicInteger status =
          CommonUtils.getStatusMap().computeIfAbsent("scan-" + scanner.name, s -> new AtomicInteger(0));
        if (status.compareAndSet(0, 1)) {
          return () -> context.ui().progress().runAndGet(scanner.name, true,
            progressBar -> {
              try {
                return scanner.handler.handle(context, progressBar);
              } catch (Exception ex) {
                log.error("Error while execute task: " + scanner.name, ex);
                return new DeviceScannerResult();
              }
            },
            ex -> {
              log.info("Done scan for <{}>", scanner.name);
              status.set(0);
              if (ex != null) {
                context.ui().toastr().error("SCAN.ERROR", FlowMap.of("MSG", CommonUtils.getErrorMessage(ex)), ex);
              }
            });
        } else {
          log.warn("Scan for <{}> already in progress", scanner.name);
        }
        return null;
      }, completedTasks -> {
      }, (Consumer<List<DeviceScannerResult>>) result -> {
        int foundNewCount = 0;
        int foundOldCount = 0;
        for (DeviceScannerResult deviceScannerResult : result) {
          if (deviceScannerResult != null) {
            foundNewCount += deviceScannerResult.getNewCount().get();
            foundOldCount += deviceScannerResult.getExistedCount().get();
          }
        }
        if (foundNewCount > 0 || foundOldCount > 0) {
          context.ui().toastr().info("SCAN.RESULT", FlowMap.of("OLD", foundOldCount, "NEW", foundNewCount));
          log.info("Done batch scanning for <{}>", getBatchName());
        }
      });
    return ActionResponseModel.showSuccess("SCAN.STARTED");
  }

  protected abstract List<DevicesScanner> getScanners(Context context);

  protected abstract String getBatchName();

  /**
   * @return Max time in seconds for wait each DevicesScanner to be done.
   */
  protected int getMaxTimeToWaitInSeconds() {
    return 10 * 60;
  }

  public interface DeviceScannerHandler {

    /**
     * Fires to start search for new items
     *
     * @param context                     -
     * @param progressBar                 -
     * @return found items count
     */
    DeviceScannerResult handle(Context context, ProgressBar progressBar);
  }

  public static class DevicesScanner implements HasEntityIdentifier {

    private final String name;
    private final DeviceScannerHandler handler;

    public DevicesScanner(String name, DeviceScannerHandler handler) {
      this.name = name;
      this.handler = handler;
    }

    @Override
    public @NotNull String getEntityID() {
      return name;
    }
  }
}
