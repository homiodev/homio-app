package org.touchhome.bundle.rf433.dto;

import java.util.ArrayList;
import java.util.List;

public class Rf433JSON {

    private String entityID = null;
    private Boolean omitDuplicateTime = false;
    private Boolean trimTime = false;
    private Boolean force = false;
    private Boolean joinSameImpulses = false;
    private Integer showDuplicates = 0;
    private Integer ignoreNoise = 0;
    private Integer signalAccuracy = 2;
    private Integer maxDuration = 3;
    private Integer repeats = 10;
    private List<ZoomItem> zoom = new ArrayList<>();

    public Integer getMaxDuration() {
        return maxDuration;
    }

    public void setMaxDuration(Integer maxDuration) {
        this.maxDuration = maxDuration;
    }

    public Integer getSignalAccuracy() {
        return signalAccuracy;
    }

    public void setSignalAccuracy(Integer signalAccuracy) {
        this.signalAccuracy = signalAccuracy;
    }

    public String getEntityID() {
        return entityID;
    }

    public void setEntityID(String entityID) {
        this.entityID = entityID;
    }

    public Boolean getOmitDuplicateTime() {
        return omitDuplicateTime;
    }

    public void setOmitDuplicateTime(Boolean omitDuplicateTime) {
        this.omitDuplicateTime = omitDuplicateTime;
    }

    public Boolean getTrimTime() {
        return trimTime;
    }

    public void setTrimTime(Boolean trimTime) {
        this.trimTime = trimTime;
    }

    public Boolean getForce() {
        return force;
    }

    public void setForce(Boolean force) {
        this.force = force;
    }

    public Boolean getJoinSameImpulses() {
        return joinSameImpulses;
    }

    public void setJoinSameImpulses(Boolean joinSameImpulses) {
        this.joinSameImpulses = joinSameImpulses;
    }

    public Integer getIgnoreNoise() {
        return ignoreNoise;
    }

    public void setIgnoreNoise(Integer ignoreNoise) {
        this.ignoreNoise = ignoreNoise;
    }

    public List<ZoomItem> getZoom() {
        return zoom;
    }

    public void setZoom(List<ZoomItem> zoom) {
        this.zoom = zoom;
    }

    public Integer getRepeats() {
        return repeats;
    }

    public void setRepeats(Integer repeats) {
        this.repeats = repeats;
    }

    public Integer getShowDuplicates() {
        return showDuplicates;
    }

    public void setShowDuplicates(Integer showDuplicates) {
        this.showDuplicates = showDuplicates;
    }

    public static class ZoomItem {
        private Float from;
        private Float to;

        public Float getFrom() {
            return from;
        }

        public void setFrom(Float from) {
            this.from = from;
        }

        public Float getTo() {
            return to;
        }

        public void setTo(Float to) {
            this.to = to;
        }
    }
}
