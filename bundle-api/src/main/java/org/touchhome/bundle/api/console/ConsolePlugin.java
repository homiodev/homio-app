package org.touchhome.bundle.api.console;

import org.touchhome.bundle.api.model.HasEntityIdentifier;

import javax.validation.constraints.NotNull;
import java.util.List;

public interface ConsolePlugin extends Comparable<ConsolePlugin> {

    default List<String> drawPlainString() {
        return null;
    }

    default List<? extends HasEntityIdentifier> drawEntity() {
        return null;
    }

    default int order() {
        return 0;
    }

    @Override
    default int compareTo(@NotNull ConsolePlugin consolePlugin) {
        return Integer.compare(order(), consolePlugin.order());
    }
}
