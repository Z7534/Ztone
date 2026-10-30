package de.Z7534.ztone.stonesit.model;

public enum StopReason {
    BLOCK_BREAK(true, false),
    DAMAGE(true, false),
    DEATH(false, true),
    DISCONNECT(false, false),
    ENVIRONMENT(true, false),
    GAMEMODE_CHANGE(false, true),
    GET_UP(true, true),
    KICKED(true, false),
    PLUGIN(false, false),
    REGION(true, false),
    SWITCH(true, false),
    TELEPORT(false, true);

    private final boolean cancellable;
    private final boolean usingEntityTask;

    StopReason(boolean cancellable, boolean usingEntityTask) {
        this.cancellable = cancellable;
        this.usingEntityTask = usingEntityTask;
    }

    public boolean isCancellable() {
        return cancellable;
    }

    public boolean isUsingEntityTask() {
        return usingEntityTask;
    }
}
