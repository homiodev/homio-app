package org.touchhome.app.workspace;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import org.touchhome.bundle.api.scratch.Scratch3ExtensionBlocks;

import javax.validation.constraints.NotNull;

@Getter
public final class Scratch3ExtensionImpl implements Comparable<Scratch3ExtensionImpl> {

    private String extensionId;
    private boolean featured = true;
    private Scratch3ExtensionBlocks getInfo;
    @JsonIgnore
    private int order;

    Scratch3ExtensionImpl(String extensionId, Scratch3ExtensionBlocks scratch3BaseBlock, int order) {
        this.extensionId = extensionId;
        this.getInfo = scratch3BaseBlock;
        this.order = order;
    }

    @Override
    public int compareTo(@NotNull Scratch3ExtensionImpl o) {
        return Integer.compare(order, o.order);
    }
}
