package io.github.icohedron.sleepvote;

import org.spongepowered.api.scheduler.Task;

import java.util.Optional;

public class SVPlayerData {

    private boolean mute;
    private boolean hidden;
    private Task sleepTask;

    SVPlayerData() {
        this.mute = false;
        this.hidden = false;
    }

    public boolean isMute() {
        return mute;
    }

    public void setMute(boolean mute) {
        this.mute = mute;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public Optional<Task> getSleepTask() {
        return Optional.ofNullable(sleepTask);
    }

    public void setSleepTask(Task sleepTask) {
        this.sleepTask = sleepTask;
    }
}
