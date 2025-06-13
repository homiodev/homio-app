package org.homio.addon.homekit;

import org.homio.api.ContextVar;

public interface HasSourceCharacteristic {
    ContextVar.Variable getSource();
}
