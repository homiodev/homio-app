package org.touchhome.app.hardware;

import java.nio.file.Path;
import org.touchhome.bundle.api.hquery.api.HQueryParam;
import org.touchhome.bundle.api.hquery.api.HardwareQuery;
import org.touchhome.bundle.api.hquery.api.HardwareRepositoryAnnotation;

@HardwareRepositoryAnnotation
public interface StartupHardwareRepository {

  @HardwareQuery(name = "Update app", value = ":path/update.sh")
  String updateApp(@HQueryParam("path") Path path);
}
