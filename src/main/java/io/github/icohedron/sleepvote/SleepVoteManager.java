package io.github.icohedron.sleepvote;

import ninja.leaping.configurate.ConfigurationNode;
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
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class SleepVoteManager {

    private SleepVote sleepVote;
    private Logger logger;
    private Messenger messenger;

    private Map<World, Set<UUID>> sleeping;
    private Map<UUID, Task> playerSleepRequests;
    private Set<UUID> mute;
    private Set<UUID> ignore;

    private boolean enablePrefix;
    private boolean messageLogging;
    private boolean ignoreAdmins;

    private float requiredPercentSleeping;
    private int requiredNumberSleeping;

    private String wakeupMessage;
    private String enterBedMessage;
    private String exitBedMessage;

    private boolean[] ignoredGameModes;

    private AFKManager afkManager;

    SleepVoteManager(SleepVote sleepVote, ConfigurationNode configNode) {
        this.sleepVote = sleepVote;
        logger = sleepVote.getLogger();
        messenger = new Messenger(this, configNode.getNode("sound").getBoolean());

        // Configure according to the configuration file values

        requiredPercentSleeping = configNode.getNode("required_percent_sleeping").getFloat();
        requiredNumberSleeping = configNode.getNode("required_number_sleeping").getInt();

        if (requiredPercentSleeping < 0.0f || requiredPercentSleeping > 1.0f) {
            requiredPercentSleeping = 0.5f;
            logger.info("\"required_percent_sleeping\": The value of '" + requiredPercentSleeping + "' is invalid, it must be in the inclusive range of [0.0, 1.0]. Using default of 0.5");
        }

        enablePrefix = configNode.getNode("sleepvote_prefix").getBoolean();
        messageLogging = configNode.getNode("enable_logging").getBoolean();
        ignoreAdmins = configNode.getNode("ignore_admins").getBoolean();

        wakeupMessage = configNode.getNode("messages", "wakeup").getString("Wakey wakey, rise and shine!");
        enterBedMessage = configNode.getNode("messages", "enter_bed").getString("<player> wants to sleep! <sleeping>/<active> (<percent>%)");
        exitBedMessage = configNode.getNode("messages", "exit_bed").getString("<player> has left their bed. <sleeping>/<active> (<percent>%)");

        ignoredGameModes = new boolean[4];
        ignoredGameModes[0] = configNode.getNode("ignored_gamemodes", "survival").getBoolean(false);
        ignoredGameModes[1] = configNode.getNode("ignored_gamemodes", "creative").getBoolean(false);
        ignoredGameModes[2] = configNode.getNode("ignored_gamemodes", "adventure").getBoolean(false);
        ignoredGameModes[3] = configNode.getNode("ignored_gamemodes", "spectator").getBoolean(false);

        // Set up Nucleus Integration if functionality is requested

        if (configNode.getNode("ignore_afk_players").getBoolean()) {
            Optional<PluginContainer> nucleus = Sponge.getPluginManager().getPlugin("nucleus");
            if (nucleus.isPresent()) {
                afkManager = new AFKManager(this);
                Sponge.getEventManager().registerListeners(sleepVote, afkManager);
            } else {
                logger.warn("Nucleus not detected. Some requested functionality may be missing");
            }
        }

        sleeping = new HashMap<>();
        playerSleepRequests = new HashMap<>();
        mute = new HashSet<>();
        ignore = new HashSet<>();
    }

    @Listener
    public void onPreSleepingEvent(SleepingEvent.Pre event, @First Player player) {
        player.setSleepingIgnored(true); // Turn off vanilla sleeping to prevent a bug where the time advances (or doesn't, if /gamerule doDaylightCycle false, in which case it just kicks players out of bed without doing anything) but the plugin doesn't display the wakeup message.

        UUID uuid = player.getUniqueId();
        if (playerSleepRequests.containsKey(uuid)) {
            playerSleepRequests.get(uuid).cancel();
        }

        if (isIgnored(player)) {
            return;
        }

        playerSleepRequests.put(uuid, Task.builder().execute(() -> {
            if (isInBed(player)) {

                World world = player.getWorld();
                sleeping.computeIfAbsent(world, w -> new HashSet<>());

                Set<UUID> sleepingPlayers = sleeping.get(world);
                sleepingPlayers.add(uuid);

                Text text = messenger.parseMessage(enterBedMessage,
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
        Set<UUID> sleepingPlayers = sleeping.get(world);

        int numSleeping = sleepingPlayers.size();
        int required = getRequiredPlayerCount(world);

        WorldProperties worldProperties = world.getProperties();

        if (numSleeping >= required) {
            worldProperties.setWorldTime(((int) Math.ceil(worldProperties.getWorldTime() / 24000.0f)) * 24000); // Set time to the next multiple 24000 ticks (equivalent to '/time set 0')
            worldProperties.setRaining(false);
            worldProperties.setThundering(false);

            Text text = messenger.parseMessage(wakeupMessage,
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
        UUID uuid = player.getUniqueId();

        World world = player.getWorld();
        sleeping.computeIfAbsent(world, w -> new HashSet<>());

        Set<UUID> sleepingPlayers = sleeping.get(world);
        if (sleepingPlayers.remove(uuid)) {

            Text text = messenger.parseMessage(exitBedMessage,
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

    private int getRequiredPlayerCount(World world) {
        // TODO: Automatically add exclusions for vanished players -- requires Nucleus to provide some sort of API for that
        Set<Player> players = new HashSet<>(world.getPlayers());
        if (afkManager != null) {
            players.removeAll(afkManager.getAfkPlayerSet());
        }
        players.removeIf(this::isIgnored);

        int requiredFromPercent = (int) (players.size() * requiredPercentSleeping);

        int required;
        if (requiredNumberSleeping <= 0) {
            required = requiredFromPercent;
        } else {
            required = Math.min(requiredFromPercent, requiredNumberSleeping);
        }

        return required < 1 ? 1 : required;
    }

    void unregisterListeners() {
        if (afkManager != null) {
            Sponge.getEventManager().unregisterListeners(afkManager);
        }
    }

    boolean isInBed(Player player) {
        // Doesn't work due to bug: https://forums.spongepowered.org/t/warnings-on-startup-skipping-keys/18338
//        return player.get(Keys.IS_SLEEPING).filter(k -> k.booleanValue()).isPresent();

        // Workaround: takes advantage of the fact that the player's hitbox shrinks to a (almost) perfect 0.2*0.2*0.2 cube while in a bed.
        // But we only need to check the floored y-values! The player's hitbox is normally greater than 1, so if the floored y-value is 0, then the player must be in a bed! If it's equal to 1 or more, then the player is not in bed.
        return player.getBoundingBox().filter(b -> b.getSize().getFloorY() == 0).isPresent();
    }

    boolean isInIgnoredGameMode(Player player) {
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

    void ignorePlayer(Player player) {
        ignore.add(player.getUniqueId());
    }

    boolean isIgnored(Player player) {
        UUID uuid = player.getUniqueId();
        return (ignoreAdmins && player.hasPermission("sleepvote.hidden")) || isInIgnoredGameMode(player) || ignore.contains(uuid);
    }

    void unignorePlayer(Player player) {
        ignore.remove(player.getUniqueId());
    }

    boolean isInIgnoredSet(Player player) {
        return ignore.contains(player.getUniqueId());
    }

    void mutePlayer(Player player) {
        mute.add(player.getUniqueId());
    }

    void unmutePlayer(Player player) {
        mute.remove(player.getUniqueId());
    }

    boolean isMute(Player player) {
        return mute.contains(player.getUniqueId());
    }

    boolean areAdminsIgnored() {
        return ignoreAdmins;
    }

    Messenger getMessenger() {
        return messenger;
    }
}
