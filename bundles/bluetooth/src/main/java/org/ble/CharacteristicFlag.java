package org.ble;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A flag indicate the operation allowed on a single characteristic.
 */
public enum CharacteristicFlag {
    read,
    write,
    notify;

    public static final List<CharacteristicFlag> C_READ = Collections.singletonList(CharacteristicFlag.read);
    public static final List<CharacteristicFlag> C_WRITE = Collections.singletonList(CharacteristicFlag.write);
    public static final List<CharacteristicFlag> C_READ_WRITE = Arrays.asList(CharacteristicFlag.read, CharacteristicFlag.write);
    public static final List<CharacteristicFlag> C_WRITE_NOTIFY = Arrays.asList(CharacteristicFlag.write, CharacteristicFlag.notify);
    public static final List<CharacteristicFlag> C_READ_NOTIFY = Arrays.asList(CharacteristicFlag.read, CharacteristicFlag.notify);
    public static final List<CharacteristicFlag> C_READ_WRITE_NOTIFY = Arrays.asList(CharacteristicFlag.read, CharacteristicFlag.write, CharacteristicFlag.notify);
}
