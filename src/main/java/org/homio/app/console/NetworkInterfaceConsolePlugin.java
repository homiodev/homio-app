package org.homio.app.console;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.SystemUtils;
import org.homio.api.Context;
import org.homio.api.console.ConsolePluginTable;
import org.homio.api.model.HasEntityIdentifier;
import org.homio.api.ui.field.UIField;
import org.homio.app.model.entity.LocalBoardEntity;
import org.homio.app.service.device.LocalBoardService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.homio.api.entity.HasJsonData.LIST_DELIMITER;
import static org.homio.api.util.Constants.PRIMARY_DEVICE;

@Component
@RequiredArgsConstructor
public class NetworkInterfaceConsolePlugin implements ConsolePluginTable<NetworkInterfaceConsolePlugin.NetworkInterfaceEntity> {

  @Getter
  private final @Accessors(fluent = true) Context context;
  private LocalBoardService service;

  @Override
  public String getParentTab() {
    return "hardware";
  }

  @Override
  @SneakyThrows
  public Collection<NetworkInterfaceEntity> getValue() {
    List<NetworkInterfaceEntity> list = new ArrayList<>();
    LocalBoardService service = getService();
    for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
      String name = networkInterface.getName();
      list.add(new NetworkInterfaceEntity(
        name,
        networkInterface.getDisplayName(),
        networkInterface.getMTU(),
        getTX(name, service),
        getRX(name, service),
        networkInterface.getInterfaceAddresses().stream()
          .map(ia -> ia.getAddress().toString())
          .collect(Collectors.joining(" %s|%s ".formatted(LIST_DELIMITER, LIST_DELIMITER)))));
    }
    Collections.sort(list);
    return list;
  }

  private long getTX(String name, LocalBoardService service) {
    return service.getNetworkDiff(name, LocalBoardService.NetworkStat::tx) / 1024;
  }

  private long getRX(String name, LocalBoardService service) {
    return service.getNetworkDiff(name, LocalBoardService.NetworkStat::rx) / 1024;
  }

  @Override
  public @Nullable Collection<TableCell> getUpdatableValues() {
    Set<TableCell> cells = new HashSet<>();
    if (SystemUtils.IS_OS_LINUX) {
      LocalBoardService service = getService();
      for (String iface : service.getNewNetStat().keySet()) {
        cells.add(new TableCell(iface, "tx", getTX(iface, service)));
        cells.add(new TableCell(iface, "rx", getRX(iface, service)));
      }
    }
    return cells;
  }

  @Override
  public int order() {
    return 1500;
  }

  @Override
  public @NotNull String getName() {
    return "networkInterface";
  }

  @Override
  public Class<NetworkInterfaceEntity> getEntityClass() {
    return NetworkInterfaceEntity.class;
  }

  private LocalBoardService getService() {
    if (service == null) {
      LocalBoardEntity localBoardEntity = this.context.db().getRequire(LocalBoardEntity.class, PRIMARY_DEVICE);
      service = localBoardEntity.getService();
    }
    return service;
  }

  @Getter
  @NoArgsConstructor
  @AllArgsConstructor
  public static class NetworkInterfaceEntity implements HasEntityIdentifier, Comparable<NetworkInterfaceEntity> {

    @UIField(order = 1)
    private String name;

    @UIField(order = 2, label = "Label")
    private String displayName;

    @UIField(order = 3)
    private int mtu;

    @UIField(order = 4, valueSuffix = "KB/s")
    private long tx;

    @UIField(order = 5, valueSuffix = "KB/s")
    private long rx;

    @UIField(order = 6)
    private String addresses;

    @Override
    public String getEntityID() {
      return name;
    }

    @Override
    public int compareTo(@NotNull NetworkInterfaceEntity o) {
      return this.name.compareTo(o.name);
    }
  }
}
