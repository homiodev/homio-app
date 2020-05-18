package org.touchhome.bundle.zigbee.internal;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.PrettyPrintWriter;
import com.thoughtworks.xstream.io.xml.StaxDriver;
import com.zsmartsystems.zigbee.IeeeAddress;
import com.zsmartsystems.zigbee.database.*;
import com.zsmartsystems.zigbee.zdo.field.BindingTable;
import com.zsmartsystems.zigbee.zdo.field.NodeDescriptor.FrequencyBandType;
import com.zsmartsystems.zigbee.zdo.field.NodeDescriptor.MacCapabilitiesType;
import com.zsmartsystems.zigbee.zdo.field.NodeDescriptor.ServerCapabilitiesType;
import com.zsmartsystems.zigbee.zdo.field.PowerDescriptor.PowerSourceType;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.zigbee.model.ZigBeeDeviceEntity;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

import static org.touchhome.bundle.api.util.TouchHomeUtils.resolvePath;

@Log4j2
public class ZigBeeDataStore implements ZigBeeNetworkDataStore {

    private final Path networkStateFilePath;
    private final EntityContext entityContext;

    public ZigBeeDataStore(String networkId, EntityContext entityContext) {
        networkStateFilePath = resolvePath("zigbee", networkId);
        this.entityContext = entityContext;
    }

    private XStream openStream() {
        XStream stream = new XStream(new StaxDriver());
        stream.setClassLoader(this.getClass().getClassLoader());

        stream.alias("ZigBeeNode", ZigBeeNodeDao.class);
        stream.alias("ZigBeeEndpoint", ZigBeeEndpointDao.class);
        stream.alias("ZclCluster", ZclClusterDao.class);
        stream.alias("ZclAttribute", ZclAttributeDao.class);
        stream.alias("MacCapabilitiesType", MacCapabilitiesType.class);
        stream.alias("ServerCapabilitiesType", ServerCapabilitiesType.class);
        stream.alias("PowerSourceType", PowerSourceType.class);
        stream.alias("FrequencyBandType", FrequencyBandType.class);
        stream.alias("BindingTable", BindingTable.class);
        stream.alias("IeeeAddress", BindingTable.class);
        stream.registerConverter(new IeeeAddressConverter());
        return stream;
    }

    private Path getIeeeAddressPath(IeeeAddress address) {
        return networkStateFilePath.resolve(address + ".xml");
    }

    @Override
    public Set<IeeeAddress> readNetworkNodes() {
        Set<IeeeAddress> nodes = new HashSet<>();
        File[] files = networkStateFilePath.toFile().listFiles();

        if (files == null) {
            return nodes;
        }

        for (File file : files) {
            if (!file.getName().toLowerCase().endsWith(".xml")) {
                continue;
            }

            try {
                IeeeAddress address = new IeeeAddress(file.getName().substring(0, 16));
                nodes.add(address);
            } catch (IllegalArgumentException e) {
                log.error("Error parsing database filename: {}", file.getName());
            }
        }

        return nodes;
    }

    @Override
    public ZigBeeNodeDao readNode(IeeeAddress address) {
        XStream stream = openStream();

        ZigBeeNodeDao node = null;
        try {
            node = readZigBeeNodeDao(getIeeeAddressPath(address), stream);
        } catch (Exception ex) {
            log.error("{}: Error reading network state: . Try reading from backup file...", address, ex);
            try {
                node = readZigBeeNodeDao(networkStateFilePath.resolve(address + "_backup.xml"), stream);
            } catch (IOException e) {
                log.error("{}: Error reading network state from backup file", address);
                // try restore minimal node from db
                ZigBeeDeviceEntity zigBeeDeviceEntity = entityContext.getEntity(ZigBeeDeviceEntity.PREFIX + address.toString());
                if (zigBeeDeviceEntity != null && zigBeeDeviceEntity.getNetworkAddress() != null) {
                    log.warn("{}: Restore minimal information", address);
                    node = new ZigBeeNodeDao();
                    node.setIeeeAddress(address);
                    node.setNetworkAddress(zigBeeDeviceEntity.getNetworkAddress());
                }
            }
        }

        return node;
    }

    @Override
    public void writeNode(ZigBeeNodeDao node) {
        XStream stream = openStream();
        writeZigBeeNode(node, stream, networkStateFilePath.resolve(node.getIeeeAddress() + "_backup.xml"), false);
        writeZigBeeNode(node, stream, getIeeeAddressPath(node.getIeeeAddress()), true);
    }

    private void writeZigBeeNode(ZigBeeNodeDao node, XStream stream, Path path, boolean isLog) {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(path), StandardCharsets.UTF_8))) {
            stream.marshal(node, new PrettyPrintWriter(writer));
            if (isLog) {
                log.debug("{}: ZigBee saving network state complete.", node.getIeeeAddress());
            }
        } catch (Exception e) {
            log.error("{}: Error writing network state: ", node.getIeeeAddress(), e);
        }
    }

    private ZigBeeNodeDao readZigBeeNodeDao(Path path, XStream stream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8))) {
            return (ZigBeeNodeDao) stream.fromXML(reader);
        }
    }

    @Override
    @SneakyThrows
    public void removeNode(IeeeAddress address) {
        if (!Files.deleteIfExists(getIeeeAddressPath(address))) {
            log.error("{}: Error removing network state", address);
        }
        Files.deleteIfExists(networkStateFilePath.resolve(address + "_backup.xml"));
    }

    /**
     * Deletes the network state file
     */
    public synchronized void delete() {
        try {
            log.debug("Deleting ZigBee network state");
            Files.walk(networkStateFilePath).sorted(Comparator.reverseOrder()).map(Path::toFile)
                    .forEach(File::delete);
        } catch (IOException e) {
            log.error("Error deleting ZigBee network state {} ", networkStateFilePath, e);
        }
    }
}
