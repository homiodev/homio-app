package org.homio.app.chromecast;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.homio.api.Context;
import org.homio.api.service.discovery.ItemDiscoverySupport;
import org.homio.api.util.Lang;
import org.homio.hquery.ProgressBar;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import javax.jmdns.ServiceInfo;
import java.net.Inet4Address;
import java.util.List;
import java.util.Objects;

@Log4j2
@Component
@RequiredArgsConstructor
public class ChromecastDiscovery implements ItemDiscoverySupport {
  private final Context context;

  @Override
  public @NotNull String getName() {
    return "scan-chromecast";
  }

  @Override
  public DeviceScannerResult scan(Context context, @NotNull ProgressBar progressBar) {
    List<ServiceInfo> services = context.network().scanMDNS("_googlecast._tcp.local.");
    DeviceScannerResult result = new DeviceScannerResult();
    for (ServiceInfo service : services) {
      if (isNewDevice(service)) {
        result.getNewCount().incrementAndGet();
        handleNewDevice(service);
      } else {
        result.getExistedCount().incrementAndGet();
      }
    }

    return result;
  }

  private void handleNewDevice(ServiceInfo service) {
    String model = service.getPropertyString("md");
    String host = service.getInet4Addresses()[0].getHostAddress();
    String application = service.getApplication();
    int port = service.getPort();
    handleDevice(service.getName(), model, context, messages -> {
      messages.add(Lang.getServerMessage("CHROMECAST.NEW_DEVICE_QUESTION"));
      messages.add(Lang.getServerMessage("TITLE.ADDRESS", host + ":" + port));
      messages.add(Lang.getServerMessage("TITLE.MODEL", model));
      messages.add(Lang.getServerMessage("CHROMECAST.APPLICATION", application));
    }, () -> {
      ChromecastEntity entity = new ChromecastEntity();
      entity.setChromecastType(ChromecastEntity.ChromecastType.findModel(model));
      entity.setName(Objects.toString(service.getPropertyString("fn"), model));
      entity.setIeeeAddress(service.getServer());
      entity.setPort(port);
      entity.setFirmware(service.getPropertyString("bs"));
      entity.setApplication(application);
      entity.setHost(host);
      context.db().save(entity);
    });
  }

  private boolean isNewDevice(ServiceInfo service) {
    Inet4Address[] addresses = service.getInet4Addresses();
    if (addresses.length == 0) {
      return false;
    }
    return context.db().findAll(ChromecastEntity.class)
             .stream()
             .filter(e -> Objects.equals(e.getIeeeAddress(), service.getServer()))
             .findAny().orElse(null) == null;
  }
}
