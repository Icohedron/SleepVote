package io.github.icohedron.sleepvote;

import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.action.SleepingEvent;
import org.spongepowered.api.event.filter.cause.First;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.world.World;
import org.spongepowered.api.world.storage.WorldProperties;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class SleepVoteManager {

    private SleepVote sleepVote;
    private Logger logger;
    private Messenger messenger;

    private Map<World, Set<Player>> sleeping;
    private Map<Player, Task> playerSleepRequests;

    private boolean enablePrefix;
    private boolean messageLogging;

    private float requiredPercentSleeping;
    private String wakeupMessage;
    private String enterBedMessage;
    private String exitBedMessage;

    private AFKManager afkManager;

    private Set<UUID> hiddenPlayers;

    public SleepVoteManager(SleepVote sleepVote, boolean enablePrefix, boolean messageLogging, boolean ignoreAfkPlayers, float requiredPercentSleeping,
                            String wakeupMessage, String enterBedMessage, String exitBedMessage) {

        this.sleepVote = sleepVote;
        this.enablePrefix = enablePrefix;
        this.messageLogging = messageLogging;
        this.requiredPercentSleeping = requiredPercentSleeping;
        this.wakeupMessage = wakeupMessage;
        this.enterBedMessage = enterBedMessage;
        this.exitBedMessage = exitBedMessage;

        logger = sleepVote.getLogger();
        messenger = sleepVote.getMessenger();
        sleeping = new HashMap<>();
        playerSleepRequests = new HashMap<>();
        hiddenPlayers = new HashSet<>();

        if (ignoreAfkPlayers) {
            Optional<PluginContainer> nucleus = Sponge.getPluginManager().getPlugin("nucleus");
            if (nucleus.isPresent()) {
                afkManager = new AFKManager(this);
                Sponge.getEventManager().registerListeners(sleepVote, afkManager);
            } else {
                logger.warn("Nucleus not detected. Some requested functionality may be missing.");
            }
        }
    }

    @Listener
    public void onPreSleepingEvent(SleepingEvent.Pre event, @First Player player) {
        player.setSleepingIgnored(true); // Turn off vanilla sleeping to prevent a bug where the time advances (or doesn't, if /gamerule doDaylightCycle false, in which case it just kicks players out of bed without doing anything) but the plugin doesn't display the wakeup message.

        if (playerSleepRequests.containsKey(player)) {
            playerSleepRequests.get(player).cancel();
        }

        if (isPlayerHidden(player)) {
            return; // This player is ignored.
        }

        playerSleepRequests.put(player, Task.builder().execute(() -> {
            if (isInBed(player)) {

                World world = player.getWorld();
                sleeping.computeIfAbsent(world, w -> new HashSet<>());

                Set<Player> sleepingPlayers = sleeping.get(world);
                sleepingPlayers.add(player);

                Text text = messenger.parseMessage(enterBedMessage,
                        Optional.of(sleepingPlayers.size()),
                        Optional.of(getRequiredPlayerCount(world)),
                        Optional.of(player.getName()),
                        enablePrefix);
                messenger.sendWorldMessage(world, text);

                if (messageLogging) {
                    logger.info("[" + world.getName() + "] " + text.toPlain());
                }
            }
        }).async().delay(4, TimeUnit.SECONDS).submit(sleepVote));
    }

    @Listener
    public void onSleepTickEvent(SleepingEvent.Tick event) {
        for (World world : sleeping.keySet()) {
            Set<Player> sleepingPlayers = sleeping.get(world);
            int numSleeping = sleepingPlayers.size();
            int required = getRequiredPlayerCount(world);
            WorldProperties worldProperties = world.getProperties();

            if (numSleeping == 0) {
                continue;
            }

            if (numSleeping >= required) {
                worldProperties.setWorldTime(((int) Math.ceil(worldProperties.getWorldTime() / 24000.0f)) * 24000); // Set time to the next multiple 24000 ticks (equivalent to '/time set 0')
                Text text = messenger.parseMessage(wakeupMessage,
                        Optional.empty(), Optional.empty(), Optional.empty(), enablePrefix);
                messenger.sendWorldMessage(world, text);
                if (messageLogging) {
                    logger.info("[" + world.getName() + "] " + text.toPlain());
                }
                sleepingPlayers.clear();
            }
        }
    }

    @Listener
    public void onPostSleepingEvent(SleepingEvent.Post event, @First Player player) {
        World world = player.getWorld();
        sleeping.computeIfAbsent(world, w -> new HashSet<>());

        player.setSleepingIgnored(false);

        Set<Player> sleepingPlayers = sleeping.get(world);
        if (sleepingPlayers.remove(player)) {
            Text text = messenger.parseMessage(exitBedMessage,
                    Optional.of(sleepingPlayers.size()),
                    Optional.of(getRequiredPlayerCount(world)),
                    Optional.of(player.getName()),
                    enablePrefix);
            messenger.sendWorldMessage(world, text);

            if (messageLogging) {
                logger.info("[" + world.getName() + "] " + text.toPlain());
            }
        }
    }


    public boolean isInBed(Player player) {
        // Doesn't work due to bug: https://forums.spongepowered.org/t/warnings-on-startup-skipping-keys/18338
//        return player.get(Keys.IS_SLEEPING).filter(k -> k.booleanValue()).isPresent();

        // Workaround: takes advantage of the fact that the player's hitbox shrinks to a (almost) perfect 0.2*0.2*0.2 cube while in a bed.
        // But we only need to check the floored y-values! The player's hitbox is normally greater than 1, so if the floored y-value is 0, then the player must be in a bed! If it's equal to 1 or more, then the player is not in bed.
        return player.getBoundingBox().filter(b -> b.getSize().getFloorY() == 0).isPresent();
    }


    private int getRequiredPlayerCount(World world) {
        // TODO: Automatically add exclusions for vanished players
        Set<Player> players = new HashSet<>(world.getPlayers());
        if (afkManager != null) {
            players.removeAll(afkManager.getImmutableAfkPlayerSet());
        }
        players.removeIf(p -> hiddenPlayers.contains(p.getUniqueId()));
        return (int) Math.ceil(players.size() * requiredPercentSleeping);
    }

    public void unregisterListeners() {
        if (afkManager != null) {
            Sponge.getEventManager().unregisterListeners(afkManager);
        }
    }

    public void addUuidsToHiddenPlayersSet(Collection<UUID> uuids) {
        hiddenPlayers.addAll(uuids);
    }

    public void hidePlayer(Player player) {
        hiddenPlayers.add(player.getUniqueId());
    }

    public void unhidePlayer(Player player) {
        hiddenPlayers.remove(player.getUniqueId());
    }

    public boolean isPlayerHidden(Player player) {
        return hiddenPlayers.contains(player.getUniqueId());
    }

    public HashSet<UUID> getImmutableHiddenPlayers() {
        return new HashSet<>(hiddenPlayers);
    }

}
