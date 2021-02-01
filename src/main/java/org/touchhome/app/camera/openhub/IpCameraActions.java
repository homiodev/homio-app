package org.touchhome.app.camera.openhub;

/**
 * The {@link IpCameraActions} is responsible for Actions.
 *
 * @author Matthew Skinner - Initial contribution
 */

//@ThingActionsScope(name = "ipcamera")
public class IpCameraActions /*implements ThingActions */{
   /* public final Logger logger = LoggerFactory.getLogger(getClass());
    private IpCameraHandler handler;

    @Override
    public void setThingHandler(ThingHandler handler) {
        this.handler = (IpCameraHandler) handler;
    }

    @Override
    public  ThingHandler getThingHandler() {
        return handler;
    }

    @RuleAction(label = "record a MP4", description = "Record MP4 to a set filename if given, or if filename is null to ipcamera.mp4")
    public void recordMP4(
            @ActionInput(name = "filename", label = "Filename", description = "Name that the recording will have once created, don't include the .mp4.") String filename,
            @ActionInput(name = "secondsToRecord", label = "Seconds to Record", description = "Enter a number of how many seconds to record.") int secondsToRecord) {
        log.debug("Recording {}.mp4 for {} seconds.", filename, secondsToRecord);
        IpCameraHandler localHandler = handler;
        if (localHandler != null) {
            localHandler.recordMp4(filename != null ? filename : "ipcamera", secondsToRecord);
        }
    }

    public static void recordMP4(ThingActions actions, String filename, int secondsToRecord) {
        ((IpCameraActions) actions).recordMP4(filename, secondsToRecord);
    }

    @RuleAction(label = "record a GIF", description = "Record GIF to a set filename if given, or if filename is null to ipcamera.gif")
    public void recordGIF(
            @ActionInput(name = "filename", label = "Filename", description = "Name that the recording will have once created, don't include the .mp4.") String filename,
            @ActionInput(name = "secondsToRecord", label = "Seconds to Record", description = "Enter a number of how many seconds to record.") int secondsToRecord) {
        log.debug("Recording {}.gif for {} seconds.", filename, secondsToRecord);
        IpCameraHandler localHandler = handler;
        if (localHandler != null) {
            localHandler.recordGif(filename != null ? filename : "ipcamera", secondsToRecord);
        }
    }

    public static void recordGIF(ThingActions actions, String filename, int secondsToRecord) {
        ((IpCameraActions) actions).recordGIF(filename, secondsToRecord);
    }*/
}
