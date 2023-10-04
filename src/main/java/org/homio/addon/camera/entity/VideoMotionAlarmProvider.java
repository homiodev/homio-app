package org.homio.addon.camera.entity;

public interface VideoMotionAlarmProvider {

    void addMotionAlarmListener(BaseCameraEntity<?, ?> entity, String listener);

    void removeMotionAlarmListener(BaseCameraEntity<?, ?> entity, String listener);

    void suspendMotionAlarmListeners(BaseCameraEntity<?, ?> entity);

    void resumeMotionAlarmListeners(BaseCameraEntity<?, ?> entity);
}
