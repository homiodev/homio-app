package org.touchhome.app.service.ssh;

import org.touchhome.bundle.hquery.api.HQueryParam;
import org.touchhome.bundle.hquery.api.HardwareQuery;
import org.touchhome.bundle.hquery.api.HardwareRepositoryAnnotation;

@HardwareRepositoryAnnotation
public interface SshHardwareRepository {

    @HardwareQuery(
        name = "Download tmate",
        printOutput = true,
        value =
            "wget https://github.com/tmate-io/tmate/releases/download/2.4.0/tmate-2.4.0-static-linux-:armv.tar.xz -O ${rootDir}/tmate.tar.xz")
    @HardwareQuery(
            name = "Unzip tmate",
            printOutput = true,
            value =
                    "tar -C ${rootDir} -xvf ${rootDir}/tmate.tar.xz && rm -rf ${rootDir}/tmate.tar.xz && mkdir -p ${rootDir}/ssh")
    @HardwareQuery(
            name = "Install tmate",
            printOutput = true,
            value =
                    "mv ${rootDir}/tmate-2.4.0-static-linux-:armv/tmate ${rootDir}/ssh/tmate && rm -rf ${rootDir}/tmate-2.4"
                            + ".0-static-linux-:armv && chmod +x ${rootDir}/ssh/tmate")
    void installTmate(@HQueryParam("armv") String armv);

    @HardwareQuery(
            name = "Get tmate version",
            value = "${rootDir}/ssh/tmate -V",
            ignoreOnError = true,
            cacheValid = 3600)
    String getTmateVersion();
}
