package org.touchhome.app.condition;

import org.touchhome.bundle.api.hquery.api.HQueryParam;
import org.touchhome.bundle.api.hquery.api.HardwareQuery;
import org.touchhome.bundle.api.hquery.api.HardwareRepositoryAnnotation;

@HardwareRepositoryAnnotation
public interface ConditionHardwareRepository {

    @HardwareQuery("which :soft")
    boolean isSoftwareInstalled(@HQueryParam("soft") String soft);
}
