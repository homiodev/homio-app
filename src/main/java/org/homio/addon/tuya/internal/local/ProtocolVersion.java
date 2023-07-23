package org.homio.addon.tuya.internal.local;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.homio.api.model.OptionModel.KeyValueEnum;

/**
 * The {@link ProtocolVersion} maps the protocol version String to
 */
public enum ProtocolVersion implements KeyValueEnum {
    V3_1("3.1"),
    V3_3("3.3"),
    V3_4("3.4");

    private final String versionString;

    ProtocolVersion(String versionString) {
        this.versionString = versionString;
    }

    public byte[] getBytes() {
        return versionString.getBytes(StandardCharsets.UTF_8);
    }

    public String getString() {
        return versionString;
    }

    public static ProtocolVersion fromString(String version) {
        return Arrays.stream(values()).filter(t -> t.versionString.equals(version)).findAny()
                .orElseThrow(() -> new IllegalArgumentException("Unknown version " + version));
    }

    @Override
    public String getKey() {
        return versionString;
    }

    @Override
    public String getValue() {
        return name();
    }
}
