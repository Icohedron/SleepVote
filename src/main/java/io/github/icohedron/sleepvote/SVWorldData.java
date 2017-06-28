package io.github.icohedron.sleepvote;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class SVWorldData {

    private Set<UUID> sleepingPlayers;
    private boolean skipping;

    SVWorldData() {
        sleepingPlayers = new HashSet<>();
        skipping = false;
    }

    public Set<UUID> getSleepingPlayers() {
        return sleepingPlayers;
    }

    public boolean isSkipping() {
        return skipping;
    }

    public void setSkipping(boolean skipping) {
        this.skipping = skipping;
    }
}
