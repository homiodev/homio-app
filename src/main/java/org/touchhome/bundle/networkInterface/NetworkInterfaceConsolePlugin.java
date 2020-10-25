package org.touchhome.bundle.networkInterface;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;
import org.touchhome.bundle.api.console.ConsolePlugin;
import org.touchhome.bundle.api.model.HasEntityIdentifier;
import org.touchhome.bundle.api.ui.field.UIField;

import javax.validation.constraints.NotNull;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class NetworkInterfaceConsolePlugin implements ConsolePlugin {

    @Override
    public String getParentTab() {
        return "hardware";
    }

    @Override
    @SneakyThrows
    public List<? extends HasEntityIdentifier> drawEntity() {
        List<NetworkInterfaceEntity> list = new ArrayList<>();
        for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            list.add(new NetworkInterfaceEntity(
                    networkInterface.getName(),
                    networkInterface.getDisplayName(),
                    networkInterface.getMTU(),
                    networkInterface.getInterfaceAddresses().stream()
                            .map(ia -> ia.getAddress().toString())
                            .collect(Collectors.joining(" ~~~|~~~ "))));
        }
        Collections.sort(list);
        return list;
    }

    @Override
    public int order() {
        return 1500;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    private static class NetworkInterfaceEntity implements HasEntityIdentifier, Comparable<NetworkInterfaceEntity> {
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
        public Integer getId() {
            return null;
        }

        @Override
        public int compareTo(@NotNull NetworkInterfaceEntity o) {
            return this.name.compareTo(o.name);
        }
    }
}
