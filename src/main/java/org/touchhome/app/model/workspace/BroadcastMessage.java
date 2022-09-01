package org.touchhome.app.model.workspace;

import lombok.Getter;
import org.touchhome.bundle.api.inmemory.InMemoryDBEntity;

@Getter
public class BroadcastMessage extends InMemoryDBEntity {
    private final String entityID;

    public BroadcastMessage(String entityID, Object value) {
        super(value);
        this.entityID = entityID;
    }
}
