package io.github.icohedron.sleepvote;

import io.github.nucleuspowered.nucleus.api.events.NucleusAFKEvent;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.filter.cause.First;
import org.spongepowered.api.event.network.ClientConnectionEvent;

import java.util.HashSet;
import java.util.Set;

class AFKManager {

    private SleepVoteManager sleepVoteManager;
    private Set<Player> afkPlayers;

    AFKManager(SleepVoteManager sleepVoteManager) {
        afkPlayers = new HashSet<>();
        this.sleepVoteManager = sleepVoteManager;
    }

    @Listener
    public void onPlayerGoingAFKEvent(NucleusAFKEvent.GoingAFK event, @First Player player) {
        if (!sleepVoteManager.isInBed(player)) { // For all intents and purposes, a sleeping AFK player is technically not AFK
            afkPlayers.add(player);
            // A player leaving a bed will trigger NucleusAFKEvent.ReturningFromAFK
        }
    }

    @Listener
    public void onPlayerReturingFromAFKEvent(NucleusAFKEvent.ReturningFromAFK event, @First Player player) {
        afkPlayers.remove(player);
    }

    @Listener
    public void onPlayerDisconnect(ClientConnectionEvent event, @First Player player) {
        afkPlayers.remove(player);
    }

    Set<Player> getAfkPlayerSet() {
        return afkPlayers;
    }
}
