package io.github.icohedron.sleepvote;

import io.github.nucleuspowered.nucleus.api.events.NucleusAFKEvent;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.filter.cause.First;
import org.spongepowered.api.event.network.ClientConnectionEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class AFKManager {

    private SleepVoteManager sleepVoteManager;
    private Set<UUID> afkPlayers;

    AFKManager(SleepVoteManager sleepVoteManager) {
        afkPlayers = new HashSet<>();
        this.sleepVoteManager = sleepVoteManager;
    }

    @Listener
    public void onPlayerGoingAFKEvent(NucleusAFKEvent.GoingAFK event, @First Player player) {
        if (!sleepVoteManager.isInBed(player)) { // For all intents and purposes, a sleeping AFK player is technically not AFK
            afkPlayers.add(player.getUniqueId());
            // A player leaving a bed will trigger NucleusAFKEvent.ReturningFromAFK
        }
    }

    @Listener
    public void onPlayerReturingFromAFKEvent(NucleusAFKEvent.ReturningFromAFK event, @First Player player) {
        afkPlayers.remove(player.getUniqueId());
    }

    @Listener
    public void onPlayerDisconnect(ClientConnectionEvent event, @First Player player) {
        afkPlayers.remove(player.getUniqueId());
    }

    boolean isAFK(UUID playerUuid) {
        return afkPlayers.contains(playerUuid);
    }
}
