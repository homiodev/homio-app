package org.touchhome.bundle.api.hardware.other;

import io.swagger.annotations.ApiParam;
import org.touchhome.bundle.api.hardware.api.HardwareQueries;
import org.touchhome.bundle.api.hardware.api.HardwareQuery;
import org.touchhome.bundle.api.hardware.api.HardwareRepositoryAnnotation;

@HardwareRepositoryAnnotation
public interface StartupHardwareRepository {

    @HardwareQuery("sed -i 's/\"exit 0\"/\"TouchHome runner\"/' /etc/rc.local")
    @HardwareQuery("grep -xF ':cmd' /etc/rc.local && echo '1' || sudo sed -i '/exit 0/i :cmd' /etc/rc.local")
    String addStartupCommand(@ApiParam("cmd") String cmd);

    @HardwareQuery(":path/update.sh")
    String updateApp(@ApiParam("path") String path);
}
