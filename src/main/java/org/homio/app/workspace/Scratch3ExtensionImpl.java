package org.homio.app.workspace;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import org.homio.api.workspace.scratch.Scratch3ExtensionBlocks;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@Getter
public final class Scratch3ExtensionImpl implements Comparable<Scratch3ExtensionImpl> {

    private final String parent;
    private final String extensionId;
    private final boolean featured = true;
    private final Scratch3ExtensionBlocks getInfo;
    @JsonIgnore
    private final int order;

    Scratch3ExtensionImpl(Scratch3ExtensionBlocks scratch3BaseBlock, int order) {
        this.extensionId = scratch3BaseBlock.getId();
        this.parent = scratch3BaseBlock.getParent();
        this.getInfo = scratch3BaseBlock;
        this.order = order;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Scratch3ExtensionImpl that = (Scratch3ExtensionImpl) o;
        return Objects.equals(extensionId, that.extensionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(extensionId);
    }

    @Override
    public int compareTo(@NotNull Scratch3ExtensionImpl o) {
        return Integer.compare(order, o.order);
    }
}
