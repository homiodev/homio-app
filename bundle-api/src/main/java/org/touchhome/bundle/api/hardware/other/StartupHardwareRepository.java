package org.touchhome.bundle.api.hardware.other;

import org.touchhome.bundle.api.hquery.api.HQueryParam;
import org.touchhome.bundle.api.hquery.api.HardwareQuery;
import org.touchhome.bundle.api.hquery.api.HardwareRepositoryAnnotation;

import java.nio.file.Path;

@HardwareRepositoryAnnotation
public interface StartupHardwareRepository {

    @HardwareQuery(":path/update.sh")
    String updateApp(@HQueryParam("path") Path path);
}
