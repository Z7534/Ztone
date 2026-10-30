package de.Z7534.ztone.stonesit.model;

import org.bukkit.entity.Pose;

public enum PoseType {

    LAY(Pose.SLEEPING, "lay"),
    LEGS_UP(Pose.SLEEPING, "legs_up");

    private final Pose playerPose;
    private final String name;

    PoseType(Pose playerPose, String name) {
        this.playerPose = playerPose;
        this.name = name;
    }

    public Pose getPlayerPose() {
        return playerPose;
    }

    public String getName() {
        return name;
    }
}
