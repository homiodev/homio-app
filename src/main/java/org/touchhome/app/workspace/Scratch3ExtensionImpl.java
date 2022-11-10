package org.touchhome.app.workspace;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Objects;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import org.touchhome.bundle.api.workspace.scratch.Scratch3ExtensionBlocks;

@Getter
public final class Scratch3ExtensionImpl implements Comparable<Scratch3ExtensionImpl> {

  private final String parent;
  private String extensionId;
  private boolean featured = true;
  private Scratch3ExtensionBlocks getInfo;
  @JsonIgnore
  private int order;

  Scratch3ExtensionImpl(Scratch3ExtensionBlocks scratch3BaseBlock, int order) {
    this.extensionId = scratch3BaseBlock.getId();
    this.parent = scratch3BaseBlock.getParent();
    this.getInfo = scratch3BaseBlock;
    this.order = order;
  }

  @Override
  public int compareTo(@NotNull Scratch3ExtensionImpl o) {
    return Integer.compare(order, o.order);
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
}
