package org.touchhome.app.camera.openhub;

/**
 * responsible for auto finding cameras that have Onvif
 */
//@Component
public class IpCameraDiscoveryService /*extends AbstractDiscoveryService*/ {

    public IpCameraDiscoveryService() {
        // super(SUPPORTED_THING_TYPES, 0, false);
    }

    // @Override
    protected void startBackgroundDiscovery() {
    }

  /*  @Override
    protected void deactivate() {
        super.deactivate();
    }*/

    public void newCameraFound(String brand, String hostname, int onvifPort) {
        /*ThingTypeUID thingtypeuid = new ThingTypeUID("ipcamera", brand);
        ThingUID thingUID = new ThingUID(thingtypeuid, hostname.replace(".", ""));
        DiscoveryResult discoveryResult = DiscoveryResultBuilder.create(thingUID)
                .withProperty(CONFIG_IPADDRESS, hostname).withProperty(CONFIG_ONVIF_PORT, onvifPort)
                .withLabel(brand + " camera @" + hostname).build();
        thingDiscovered(discoveryResult);*/
    }

    /*@Override
    protected void startScan() {
        removeOlderResults(getTimestampOfLastScan());
        OnvifDiscovery onvifDiscovery = new OnvifDiscovery(this);
        try {
            onvifDiscovery.discoverCameras();
        } catch (UnknownHostException | InterruptedException e) {
            log.warn(
                    "IpCamera Discovery has an issue discovering the network settings to find cameras with. Try setting up the camera manually.");
        }
        stopScan();
    }*/
}
