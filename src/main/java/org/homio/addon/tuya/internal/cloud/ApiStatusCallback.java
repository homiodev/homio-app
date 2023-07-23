package org.homio.addon.tuya.internal.cloud;

/**
 * The {@link ApiStatusCallback} is an interface for reporting API call results
 */
public interface ApiStatusCallback {

    /**
     * report the status of the connection if it changes
     *
     * @param status true -> established, false -> disconnected/failed
     */
    void tuyaOpenApiStatus(boolean status);
}
