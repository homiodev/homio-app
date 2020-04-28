package org.touchhome.bundle.api;

import org.touchhome.bundle.api.json.Option;

import java.util.List;

public interface DynamicOptionLoader<T> {

    List<Option> loadOptions(T parameter, EntityContext entityContext);
}
