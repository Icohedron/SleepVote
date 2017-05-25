package io.github.icohedron.sleepvote;

import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.effect.sound.SoundTypes;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.living.player.gamemode.GameMode;
import org.spongepowered.api.entity.living.player.gamemode.GameModes;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.action.SleepingEvent;
import org.spongepowered.api.event.filter.cause.First;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.world.World;
import org.spongepowered.api.world.storage.WorldProperties;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private HashMap<String, String> strings; // Available strings: "wakeup_message", "enter_bed_message", "exit_bed_message"

    private boolean[] ignoredGameModes;

    private AFKManager afkManager;

    SleepVoteManager(SleepVote sleepVote, float requiredPercentSleeping, HashMap<String, String> strings, boolean[] ignoredGameModes) {
        this.sleepVote = sleepVote;
        this.requiredPercentSleeping = requiredPercentSleeping;
        this.strings = strings;
        this.ignoredGameModes = ignoredGameModes;

        logger = sleepVote.getLogger();
        messenger = sleepVote.getMessenger();
        sleeping = new HashMap<>();
        playerSleepRequests = new HashMap<>();
    }

    @Listener
    public void onPreSleepingEvent(SleepingEvent.Pre event, @First Player player) {
        player.setSleepingIgnored(true); // Turn off vanilla sleeping to prevent a bug where the time advances (or doesn't, if /gamerule doDaylightCycle false, in which case it just kicks players out of bed without doing anything) but the plugin doesn't display the wakeup message.

        if (playerSleepRequests.containsKey(player)) {
            playerSleepRequests.get(player).cancel();
        }

        if (isIgnored(player)) {
            return;
        }

        playerSleepRequests.put(player, Task.builder().execute(() -> {
            if (isInBed(player)) {

                World world = player.getWorld();
                sleeping.computeIfAbsent(world, w -> new HashSet<>());

                Set<Player> sleepingPlayers = sleeping.get(world);
                sleepingPlayers.add(player);

                Text text = messenger.parseMessage(strings.get("enter_bed_message"),
                        sleepingPlayers.size(),
                        getRequiredPlayerCount(world),
                        player.getName(),
                        enablePrefix);
                messenger.sendWorldMessage(world, text);
                messenger.playWorldSound(world, SoundTypes.BLOCK_NOTE_HAT);

                if (messageLogging) {
                    logger.info("[" + world.getName() + "] " + text.toPlain());
                }
            }
        }).async().delay(4, TimeUnit.SECONDS).submit(sleepVote));
    }

    @Listener
    public void onSleepTickEvent(SleepingEvent.Tick event, @First Player player) {
        World world = player.getWorld();
        sleeping.computeIfAbsent(world, w -> new HashSet<>());
        Set<Player> sleepingPlayers = sleeping.get(world);

        int numSleeping = sleepingPlayers.size();
        int required = getRequiredPlayerCount(world);

        WorldProperties worldProperties = world.getProperties();

        if (numSleeping >= required) {
            worldProperties.setWorldTime(((int) Math.ceil(worldProperties.getWorldTime() / 24000.0f)) * 24000); // Set time to the next multiple 24000 ticks (equivalent to '/time set 0')
            worldProperties.setRaining(false);
            worldProperties.setThundering(false);

            Text text = messenger.parseMessage(strings.get("wakeup_message"),
                    0, 0, "", enablePrefix);
            messenger.sendWorldMessage(world, text);
            messenger.playWorldSound(world, SoundTypes.ENTITY_PLAYER_LEVELUP);

            if (messageLogging) {
                logger.info("[" + world.getName() + "] " + text.toPlain());
            }
            sleepingPlayers.clear();
        }
    }

    @Listener
    public void onPostSleepingEvent(SleepingEvent.Post event, @First Player player) {
        player.setSleepingIgnored(false);

        World world = player.getWorld();
        sleeping.computeIfAbsent(world, w -> new HashSet<>());

        Set<Player> sleepingPlayers = sleeping.get(world);
        if (sleepingPlayers.remove(player)) {

            Text text = messenger.parseMessage(strings.get("exit_bed_message"),
                    sleepingPlayers.size(),
                    getRequiredPlayerCount(world),
                    player.getName(),
                    enablePrefix);
            messenger.sendWorldMessage(world, text);
            messenger.playWorldSound(world, SoundTypes.BLOCK_NOTE_HAT);

            if (messageLogging) {
                logger.info("[" + world.getName() + "] " + text.toPlain());
            }
        }
    }

    private boolean isInIgnoredGameMode(Player player) {

        Optional<GameMode> optionalGameMode = player.getGameModeData().get(Keys.GAME_MODE);
        if (optionalGameMode.isPresent()) {
            GameMode gameMode = optionalGameMode.get();
            return (gameMode.equals(GameModes.SURVIVAL) && ignoredGameModes[0]) ||
                    (gameMode.equals(GameModes.CREATIVE) && ignoredGameModes[1]) ||
                    (gameMode.equals(GameModes.ADVENTURE) && ignoredGameModes[2]) ||
                    (gameMode.equals(GameModes.SPECTATOR) && ignoredGameModes[3]);
        }

        return true;
    }

    private boolean isIgnored(Player player) {
        return player.hasPermission("sleepvote.hidden") || isInIgnoredGameMode(player);
    }

    boolean isInBed(Player player) {
        // Doesn't work due to bug: https://forums.spongepowered.org/t/warnings-on-startup-skipping-keys/18338
//        return player.get(Keys.IS_SLEEPING).filter(k -> k.booleanValue()).isPresent();

        // Workaround: takes advantage of the fact that the player's hitbox shrinks to a (almost) perfect 0.2*0.2*0.2 cube while in a bed.
        // But we only need to check the floored y-values! The player's hitbox is normally greater than 1, so if the floored y-value is 0, then the player must be in a bed! If it's equal to 1 or more, then the player is not in bed.
        return player.getBoundingBox().filter(b -> b.getSize().getFloorY() == 0).isPresent();
    }


    private int getRequiredPlayerCount(World world) {
        // TODO: Automatically add exclusions for vanished players -- requires Nucleus to provide some sort of API for that
        Set<Player> players = new HashSet<>(world.getPlayers());
        if (afkManager != null) {
            players.removeAll(afkManager.getAfkPlayerSet());
        }
        players.removeIf(this::isIgnored);
        int required = (int) (players.size() * requiredPercentSleeping);
        return required < 1 ? 1 : required;
    }

    void unregisterListeners() {
        if (afkManager != null) {
            Sponge.getEventManager().unregisterListeners(afkManager);
        }
    }

    void enableMessagePrefix() {
        enablePrefix = true;
    }

    void enableMessageLogging() {
        messageLogging = true;
    }

    void ignoreAfkPlayers() {
        Optional<PluginContainer> nucleus = Sponge.getPluginManager().getPlugin("nucleus");
        if (nucleus.isPresent()) {
            afkManager = new AFKManager(this);
            Sponge.getEventManager().registerListeners(sleepVote, afkManager);
        } else {
            logger.warn("Nucleus not detected. Some requested functionality may be missing");
        }
    }
}
