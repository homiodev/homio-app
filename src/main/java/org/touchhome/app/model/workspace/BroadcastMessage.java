package org.touchhome.app.model.workspace;

import lombok.Getter;
import org.touchhome.bundle.api.inmemory.InMemoryDBEntity;

@Getter
public class BroadcastMessage extends InMemoryDBEntity {
    private final String name;

    public BroadcastMessage(String name, Object value) {
        super(value);
        this.name = name;
    }
}
