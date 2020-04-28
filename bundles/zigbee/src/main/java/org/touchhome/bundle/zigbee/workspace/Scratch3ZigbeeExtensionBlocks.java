package org.touchhome.bundle.zigbee.workspace;

import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.scratch.Scratch3ExtensionBlocks;

public abstract class Scratch3ZigbeeExtensionBlocks extends Scratch3ExtensionBlocks {

    public Scratch3ZigbeeExtensionBlocks(String id, String color, EntityContext entityContext) {
        super(id, color, entityContext);
    }

    public Scratch3ZigbeeExtensionBlocks(String color, EntityContext entityContext) {
        super(color, entityContext);
    }
}
