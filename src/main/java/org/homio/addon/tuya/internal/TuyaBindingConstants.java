package org.homio.addon.tuya.internal;

import static org.homio.addon.tuya.internal.cloud.TuyaOpenAPI.gson;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Objects;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.tuya.internal.util.SchemaDp;

/**
 * The {@link TuyaBindingConstants} class defines common constants, which are used across the whole binding.
 */
@Log4j2
public class TuyaBindingConstants {

    private static final String BINDING_ID = "tuya";

    // List of all Thing Type UIDs
    //public static final ThingTypeUID THING_TYPE_PROJECT = new ThingTypeUID(BINDING_ID, "project");
    // public static final ThingTypeUID THING_TYPE_TUYA_DEVICE = new ThingTypeUID(BINDING_ID, "tuyaDevice");

    public static final String PROPERTY_CATEGORY = "category";
    public static final String PROPERTY_MAC = "mac";

    public static final String CONFIG_LOCAL_KEY = "localKey";
    public static final String CONFIG_DEVICE_ID = "deviceId";
    public static final String CONFIG_PRODUCT_ID = "productId";

   /* public static final ChannelTypeUID CHANNEL_TYPE_UID_COLOR = new ChannelTypeUID(BINDING_ID, "color");
    public static final ChannelTypeUID CHANNEL_TYPE_UID_DIMMER = new ChannelTypeUID(BINDING_ID, "dimmer");
    public static final ChannelTypeUID CHANNEL_TYPE_UID_NUMBER = new ChannelTypeUID(BINDING_ID, "number");
    public static final ChannelTypeUID CHANNEL_TYPE_UID_STRING = new ChannelTypeUID(BINDING_ID, "string");
    public static final ChannelTypeUID CHANNEL_TYPE_UID_SWITCH = new ChannelTypeUID(BINDING_ID, "switch");*/

    public static final int TCP_CONNECTION_HEARTBEAT_INTERVAL = 10; // in s
    public static final int TCP_CONNECTION_TIMEOUT = 60; // in s;
    public static final int TCP_CONNECTION_MAXIMUM_MISSED_HEARTBEATS = 3;

    public static final Map<String, Map<String, SchemaDp>> SCHEMAS = getSchemas();

    private static Map<String, Map<String, SchemaDp>> getSchemas() {
        InputStream resource = Thread.currentThread().getContextClassLoader().getResourceAsStream("schema.json");
        if (resource == null) {
            log.warn("Could not read resource file 'schema.json', discovery might fail");
            return Map.of();
        }

        try (InputStreamReader reader = new InputStreamReader(resource)) {
            Type schemaListType = TypeToken.getParameterized(Map.class, String.class, SchemaDp.class).getType();
            Type schemaType = TypeToken.getParameterized(Map.class, String.class, schemaListType).getType();
            return Objects.requireNonNull(gson.fromJson(reader, schemaType));
        } catch (IOException e) {
            log.warn("Failed to read 'schema.json', discovery might fail");
            return Map.of();
        }
    }
}