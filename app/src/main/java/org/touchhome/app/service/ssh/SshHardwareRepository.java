package org.touchhome.app.service.ssh;

import io.swagger.annotations.ApiParam;
import org.touchhome.bundle.api.hardware.api.HardwareQuery;
import org.touchhome.bundle.api.hardware.api.HardwareRepositoryAnnotation;

@HardwareRepositoryAnnotation
public interface SshHardwareRepository {

    @HardwareQuery(value = "wget https://bintray.com/touchhome/touchhome/download_file?file_path=tmate-2.4.0-static-linux-:armv.tar.xz -O :dir/tmate.tar.xz", printOutput = true)
    @HardwareQuery(value = "tar -C :dir -xvf :dir/tmate.tar.xz && rm -rf :dir/tmate.tar.xz && mkdir -p :dir/ssh", printOutput = true)
    @HardwareQuery(value = "mv :dir/tmate-2.4.0-static-linux-:armv/tmate :dir/ssh/tmate && rm -rf :dir/tmate-2.4.0-static-linux-:armv && chmod +x :dir/ssh/tmate", printOutput = true)
    void installTmate(@ApiParam("armv") String armv);

    @HardwareQuery(value = ":dir/ssh/tmate -V", ignoreOnError = true)
    String getTmateVersion();

    @HardwareQuery(value = ":dir/ssh/tmate -F")
    Object openTmateSsh();
}
