package org.touchhome.app.camera.openhub;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ChannelUID {

    public static final String CHANNEL_SEGMENT_PATTERN = "[\\w-]*|[\\w-]*#[\\w-]*";
    public static final String CHANNEL_GROUP_SEPARATOR = "#";
    public static final String SEPARATOR = ":";

    private final List<String> segments;

    private String channelUid;

    ChannelUID() {
        segments = Collections.emptyList();
    }

    /**
     * Parses a {@link ChannelUID} for a given string. The UID must be in the format
     * 'bindingId:segment:segment:...'.
     *
     * @param channelUid uid in form a string
     */
    public ChannelUID(String channelUid) {
        this.channelUid = channelUid;
        this.segments = splitToSegments(channelUid);
    }

    private static List<String> splitToSegments(final String id) {
        return Arrays.asList(id.split(SEPARATOR));
    }

    /**
     * @param thingUID the unique identifier of the thing the channel belongs to
     * @param id the channel's id
     */
    /*public ChannelUID(String thingUID, String id) {
        super(toSegments(thingUID, null, id));
    }*/

    /**
     * @param channelGroupUID the unique identifier of the channel group the channel belongs to
     * @param id the channel's id
     */
   /* public ChannelUID(ChannelGroupUID channelGroupUID, String id) {
        super(toSegments(channelGroupUID.getThingUID(), channelGroupUID.getId(), id));
    }
*/
    /**
     * @param thingUID the unique identifier of the thing the channel belongs to
     * @param groupId the channel's group id
     * @param id the channel's id
     */
  /*  public ChannelUID(ThingUID thingUID, String groupId, String id) {
        super(toSegments(thingUID, groupId, id));
    }*/

    private static List<String> toSegments(String thingUID,  String groupId, String id) {
        List<String> ret = new ArrayList<>();
        ret.add(thingUID);
        ret.add(getChannelId(groupId, id));
        return ret;
    }

    private static String getChannelId( String groupId, String id) {
        return groupId != null ? groupId + CHANNEL_GROUP_SEPARATOR + id : id;
    }

    /**
     * Returns the id.
     *
     * @return id
     */
    public String getId() {
        List<String> segments = getAllSegments();
        return segments.get(segments.size() - 1);
    }

    protected List<String> getAllSegments() {
        return segments;
    }

    /**
     * Returns the id without the group id.
     *
     * @return id id without group id
     */
    public String getIdWithoutGroup() {
        if (!isInGroup()) {
            return getId();
        } else {
            return getId().split(CHANNEL_GROUP_SEPARATOR)[1];
        }
    }

    public boolean isInGroup() {
        return getId().contains(CHANNEL_GROUP_SEPARATOR);
    }

    /**
     * Returns the group id.
     *
     * @return group id or null if channel is not in a group
     */
    public  String getGroupId() {
        return isInGroup() ? getId().split(CHANNEL_GROUP_SEPARATOR)[0] : null;
    }

   // @Override
    protected int getMinimalNumberOfSegments() {
        return 4;
    }

   // @Override
    /*protected void validateSegment(String segment, int index, int length) {
        if (index < length - 1) {
            super.validateSegment(segment, index, length);
        } else {
            if (!segment.matches(CHANNEL_SEGMENT_PATTERN)) {
                throw new IllegalArgumentException(String.format(
                        "UID segment '%s' contains invalid characters. The last segment of the channel UID must match the pattern '%s'.",
                        segment, CHANNEL_SEGMENT_PATTERN));
            }
        }
    }*/

    /**
     * Returns the thing UID
     *
     * @return the thing UID
     */
    public String getThingUID() {
        List<String> allSegments = getAllSegments();
        return null; // allSegments.subList(0, allSegments.size() - 1).toArray(new String[allSegments.size() - 1]);
    }
}
