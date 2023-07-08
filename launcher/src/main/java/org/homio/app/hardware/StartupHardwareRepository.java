package org.homio.app.hardware;

import org.homio.hquery.api.HardwareQuery;
import org.homio.hquery.api.HardwareRepository;

@HardwareRepository
public interface StartupHardwareRepository {

    @HardwareQuery(name = "Check psql is running", value = {"service", "postgresql", "status"}, printOutput = true)
    boolean isPostgreSQLRunning();
}
