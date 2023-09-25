package org.homio.app.console;

import static org.homio.api.entity.HasJsonData.LIST_DELIMITER;

import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.homio.api.EntityContext;
import org.homio.api.console.ConsolePluginTable;
import org.homio.api.model.HasEntityIdentifier;
import org.homio.api.ui.field.UIField;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NetworkInterfaceConsolePlugin implements ConsolePluginTable<NetworkInterfaceConsolePlugin.NetworkInterfaceEntity> {

    @Getter
    private final EntityContext entityContext;

    @Override
    public String getParentTab() {
        return "hardware";
    }

    @Override
    @SneakyThrows
    public Collection<NetworkInterfaceEntity> getValue() {
        List<NetworkInterfaceEntity> list = new ArrayList<>();
        for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            list.add(new NetworkInterfaceEntity(
                    networkInterface.getName(),
                    networkInterface.getDisplayName(),
                    networkInterface.getMTU(),
                    networkInterface.getInterfaceAddresses().stream()
                            .map(ia -> ia.getAddress().toString())
                                    .collect(Collectors.joining(" %s|%s ".formatted(LIST_DELIMITER, LIST_DELIMITER)))));
        }
        Collections.sort(list);
        return list;
    }

    @Override
    public int order() {
        return 1500;
    }

    @Override
    public String getName() {
        return "networkInterface";
    }

    @Override
    public Class<NetworkInterfaceEntity> getEntityClass() {
        return NetworkInterfaceEntity.class;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NetworkInterfaceEntity implements HasEntityIdentifier, Comparable<NetworkInterfaceEntity> {

        @UIField(order = 1)
        private String name;

        @UIField(order = 2, label = "Label")
        private String displayName;

        @UIField(order = 3, label = "MTU")
        private int mtu;

        @UIField(order = 4, label = "Addresses")
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
