package org.homio.addon.zigbee;

import java.util.function.BiPredicate;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.homio.api.state.State;

final class MagicCubeHandler {

    @RequiredArgsConstructor
    public enum MagicCubeEvent {
        /*MOVE((clusterID, state) -> clusterID == ZclMultistateInputBasicCluster.CLUSTER_ID && MoveSide.isMoveSide(state.intValue())),
        ROTATE_RIGHT((clusterID, state) -> clusterID == ZclAnalogInputBasicCluster.CLUSTER_ID && state.floatValue() > 0),
        ROTATE_LEFT((clusterID, state) -> clusterID == ZclAnalogInputBasicCluster.CLUSTER_ID && state.floatValue() < 0),
        FLIP90((clusterID, state) -> clusterID == ZclMultistateInputBasicCluster.CLUSTER_ID && state.intValue() > 60 && state.intValue() < 110),
        FLIP180((clusterID, state) -> clusterID == ZclMultistateInputBasicCluster.CLUSTER_ID && state.intValue() > 120),
        TAP_TWICE((clusterID, state) -> clusterID == ZclMultistateInputBasicCluster.CLUSTER_ID && TapSide.isTapSide(state.intValue())),
        SHAKE_AIR((clusterID, state) -> clusterID == ZclMultistateInputBasicCluster.CLUSTER_ID && state.intValue() == 0),
        FREE_FALL((clusterID, state) -> clusterID == ZclMultistateInputBasicCluster.CLUSTER_ID && state.intValue() == 3),*/
        ANY_EVENT((clusterID, state) -> false);

        private final BiPredicate<Integer, State> matcher;

    /*static MagicCubeEvent getValue(ScratchDeviceState state) {
      int clusterID = state.getUuid().getClusterId();
      for (MagicCubeEvent magicCubeEvent : MagicCubeEvent.values()) {
        if (magicCubeEvent.matcher.test(clusterID, state.getState())) {
          return magicCubeEvent;
        }
      }
      return null;
    }*/

    /*public static MagicCubeEvent getEvent(String name) {
      for (MagicCubeEvent magicCubeEvent : MagicCubeEvent.values()) {
        if (magicCubeEvent.name().equals(name)) {
          return magicCubeEvent;
        }
      }
      if (name.startsWith("MOVE_SIDE_")) {
        return MagicCubeEvent.MOVE;
      } else if (name.startsWith("TAP_SIDE_")) {
        return MagicCubeEvent.TAP_TWICE;
      }
      throw new IllegalStateException("Unable to find MagicCubeEvent by name: " + name);
    }*/
    }

    @Getter
    @RequiredArgsConstructor
    public enum MoveSide {
        MOVE_SIDE_ANY(-256),
        MOVE_SIDE_1(256),
        MOVE_SIDE_2(257),
        MOVE_SIDE_3(258),
        MOVE_SIDE_4(259),
        MOVE_SIDE_5(260),
        MOVE_SIDE_6(261);

        private final int side;

        public static MoveSide getEntity(int side) {
            for (MoveSide moveSide : MoveSide.values()) {
                if (moveSide.side == side) {
                    return moveSide;
                }
            }
            return null;
        }

        private static boolean isMoveSide(int value) {
            return value > 255 && value < 262;
        }
    }

    @Getter
    @RequiredArgsConstructor
    public enum TapSide {
        TAP_SIDE_ANY(-512),
        TAP_SIDE_1(512),
        TAP_SIDE_2(513),
        TAP_SIDE_3(514),
        TAP_SIDE_4(515),
        TAP_SIDE_5(516),
        TAP_SIDE_6(517);

        private final int side;

        public static TapSide getEntity(int side) {
            for (TapSide tapSide : TapSide.values()) {
                if (tapSide.side == side) {
                    return tapSide;
                }
            }
            return null;
        }

        private static boolean isTapSide(int value) {
            return value > 511 && value < 518;
        }
    }

  /*public static class CubeValueDescriptor {

    private final MagicCubeEvent magicCubeEvent;
    private final TapSide tapSide;
    private final MoveSide moveSide;
    @Getter
    private final ScratchDeviceState scratchDeviceState;

    CubeValueDescriptor(ScratchDeviceState sds) {
      this.magicCubeEvent = MagicCubeEvent.getValue(sds);
      this.scratchDeviceState = sds;
      this.tapSide = magicCubeEvent == MagicCubeEvent.TAP_TWICE ? TapSide.getEntity(sds.getState().intValue()) : null;
      this.moveSide = magicCubeEvent == MagicCubeEvent.MOVE ? MoveSide.getEntity(sds.getState().intValue()) : null;
    }

    boolean match(MagicCubeEvent expectedMenuValue, TapSide expectedTapSide, MoveSide expectedMoveSide) {
      if (expectedMenuValue == magicCubeEvent) {
        if (magicCubeEvent == MagicCubeEvent.TAP_TWICE) {
          return expectedTapSide == TapSide.TAP_SIDE_ANY || expectedTapSide == tapSide;
        } else if (magicCubeEvent == MagicCubeEvent.MOVE) {
          return expectedMoveSide == MoveSide.MOVE_SIDE_ANY || expectedMoveSide == moveSide;
        }
        return true;
      }
      return expectedMenuValue == MagicCubeEvent.ANY_EVENT;
    }

    @Override
    public String toString() {
      if (tapSide != null) {
        return tapSide.name();
      }
      if (moveSide != null) {
        return moveSide.name();
      }
      if (magicCubeEvent == null) {
        return "Event: Unknown event. " + scratchDeviceState.getUuid() + " / " + scratchDeviceState.getState().toString();
      }
      return magicCubeEvent.name();
    }
  }*/
}
