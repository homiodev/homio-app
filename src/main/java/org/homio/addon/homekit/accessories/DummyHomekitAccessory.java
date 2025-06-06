package org.homio.addon.homekit.accessories;

import io.github.hapjava.accessories.HomekitAccessory;
import io.github.hapjava.characteristics.Characteristic;
import io.github.hapjava.services.Service;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.homio.addon.homekit.HomekitEndpointEntity.calculateId;

/**
 * Implements a fake placeholder accessory for when configuration is missing
 */
public class DummyHomekitAccessory implements HomekitAccessory {
    int id;
    String item;
    List<Service> services = new ArrayList<>();
    public DummyHomekitAccessory(String item, String data) {
        this.id = calculateId(item);
        this.item = item;

        var reader = Json.createReader(new StringReader(data));
        var services = reader.readArray();
        reader.close();

        services.forEach(s ->
                this.services.add(new DummyService((JsonObject) s)));
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public CompletableFuture<String> getName() {
        return CompletableFuture.completedFuture(item);
    }

    @Override
    public void identify() {
    }

    @Override
    public CompletableFuture<String> getSerialNumber() {
        return CompletableFuture.completedFuture(item);
    }

    @Override
    public CompletableFuture<String> getModel() {
        return CompletableFuture.completedFuture("none");
    }

    @Override
    public CompletableFuture<String> getManufacturer() {
        return CompletableFuture.completedFuture("none");
    }

    @Override
    public CompletableFuture<String> getFirmwareRevision() {
        return CompletableFuture.completedFuture("none");
    }

    @Override
    public Collection<Service> getServices() {
        return services;
    }

    private static class DummyCharacteristic implements Characteristic {
        private final JsonObject json;
        private String type;

        public DummyCharacteristic(JsonObject json) {
            this.json = json;
            type = json.getString("type");
            // reconstitute shortened IDs
            if (type.length() < 8) {
                type = "0".repeat(8 - type.length()) + type + "-0000-1000-8000-0026BB765291";
            }
        }

        @Override
        public String getType() {
            return type;
        }

        @Override
        public void supplyValue(JsonObjectBuilder characteristicBuilder) {
            characteristicBuilder.add("value", json.get("value"));
        }

        @Override
        public CompletableFuture<JsonObject> toJson(int iid) {
            var builder = Json.createObjectBuilder();
            json.forEach(builder::add);
            builder.add("iid", iid);
            return CompletableFuture.completedFuture(builder.build());
        }

        @Override
        public void setValue(JsonValue jsonValue) {
        }
    }

    private static class DummyService implements Service {
        private final String type;
        private final List<Characteristic> characteristics = new ArrayList<>();
        private final List<Service> linkedServices = new ArrayList<>();

        public DummyService(JsonObject json) {
            type = json.getString("type");
            json.getJsonArray("c").forEach(c ->
                    characteristics.add(new DummyCharacteristic((JsonObject) c)));
            var ls = json.getJsonArray("ls");
            if (ls != null) {
                ls.forEach(s ->
                        addLinkedService(new DummyService((JsonObject) s)));
            }
        }

        @Override
        public String getType() {
            return type;
        }

        @Override
        public List<Characteristic> getCharacteristics() {
            return characteristics;
        }

        @Override
        public List<Service> getLinkedServices() {
            return linkedServices;
        }

        @Override
        public void addLinkedService(Service service) {
            linkedServices.add(service);
        }
    }
}
