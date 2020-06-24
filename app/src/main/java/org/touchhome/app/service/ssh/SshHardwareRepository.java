package org.touchhome.app.service.ssh;

import org.touchhome.bundle.api.hquery.api.HQueryParam;
import org.touchhome.bundle.api.hquery.api.HardwareQuery;
import org.touchhome.bundle.api.hquery.api.HardwareRepositoryAnnotation;

@HardwareRepositoryAnnotation
public interface SshHardwareRepository {

    @HardwareQuery(value = "wget https://bintray.com/touchhome/touchhome/download_file?file_path=tmate-2.4.0-static-linux-:armv.tar.xz -O ${rootDir}/tmate.tar.xz", printOutput = true)
    @HardwareQuery(value = "tar -C ${rootDir} -xvf ${rootDir}/tmate.tar.xz && rm -rf ${rootDir}/tmate.tar.xz && mkdir -p ${rootDir}/ssh", printOutput = true)
    @HardwareQuery(value = "mv ${rootDir}/tmate-2.4.0-static-linux-:armv/tmate ${rootDir}/ssh/tmate && rm -rf ${rootDir}/tmate-2.4.0-static-linux-:armv && chmod +x ${rootDir}/ssh/tmate", printOutput = true)
    void installTmate(@HQueryParam("armv") String armv);

    @HardwareQuery(value = "${rootDir}/ssh/tmate -V", ignoreOnError = true, cache = true)
    String getTmateVersion();
}
