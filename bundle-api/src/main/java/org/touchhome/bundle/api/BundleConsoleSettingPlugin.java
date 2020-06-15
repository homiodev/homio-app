package org.touchhome.bundle.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Interface for Console settings that enable to filter settings depend on current active tab
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public interface BundleConsoleSettingPlugin<T> extends BundleSettingPlugin<T> {
    /**
     * @return list of console page names where this setting enabled
     */
    String[] pages();
}
