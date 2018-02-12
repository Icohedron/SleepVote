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

public class SleepVoteManager {

    private final SleepVote sleepVote;
    private final Logger logger;
    private final Messenger messenger;

    private final Map<UUID, SVWorldData> uuidsvWorldDataMap;
    private final Map<UUID, SVPlayerData> uuidsvPlayerDataMap;

    private final boolean enablePrefix;
    private final boolean messageLogging;
    private final boolean ignoreAdmins;

    private final float requiredPercentSleeping;
    private final int requiredNumberSleeping;

    private final String wakeupMessage;
    private final String enterBedMessage;
    private final String exitBedMessage;

    private final boolean[] ignoredGameModes;

    private AFKManager afkManager;

    private Task votingUpdateLoop;

    SleepVoteManager(SleepVote sleepVote, ConfigurationNode configNode) {
        this.sleepVote = sleepVote;
        logger = sleepVote.getLogger();
        messenger = new Messenger(this, configNode.getNode("sound").getBoolean());

        // Configure according to the configuration file values

        float reqPercent = configNode.getNode("required_percent_sleeping").getFloat();
        requiredNumberSleeping = configNode.getNode("required_number_sleeping").getInt();

        if (reqPercent < 0.0f || reqPercent > 1.0f) {
            requiredPercentSleeping = 0.5f;
            logger.info("\"required_percent_sleeping\": The value of '" + requiredPercentSleeping + "' is invalid, it must be in the inclusive range of [0.0, 1.0]. Using default of 0.5");
        } else {
            requiredPercentSleeping = reqPercent;
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

        uuidsvWorldDataMap = new HashMap<>();
        uuidsvPlayerDataMap = new HashMap<>();

        startVotingUpdateLoop();
    }

    private void startVotingUpdateLoop() {
        votingUpdateLoop = Task.builder().execute(() -> {
            for (World world : Sponge.getServer().getWorlds()) {
                if (uuidsvWorldDataMap.containsKey(world.getUniqueId())) {
                    SVWorldData svWorldData = uuidsvWorldDataMap.get(world.getUniqueId());

                    if (svWorldData.isSkipping() || svWorldData.getSleepingPlayers().isEmpty()) {
                        continue;
                    }

                    for (UUID uuid : svWorldData.getSleepingPlayers()) { // In the off chance that players had their sleep disrupted without triggering postSleepEvent (i.e. Breaking a bed while someone is in it, teleporting to another world, etc...)
                        Optional<Player> optUPlayer = Sponge.getServer().getPlayer(uuid);
                        if (optUPlayer.isPresent()) {
                            Player player = optUPlayer.get();
                            if (!isInBed(player) || !player.getWorld().getUniqueId().equals(world.getUniqueId()) || isIgnored(player)) {
                                svWorldData.getSleepingPlayers().remove(uuid);
                                Text text = messenger.parseMessage(exitBedMessage,
                                        svWorldData.getSleepingPlayers().size(),
                                        getRequiredPlayerCount(world),
                                        optUPlayer.get().getName(),
                                        enablePrefix);
                                messenger.sendWorldMessage(world, text);
                                messenger.playWorldSound(world, SoundTypes.BLOCK_NOTE_HAT);

                                if (messageLogging) {
                                    logger.info("[" + world.getName() + "] " + text.toPlain());
                                }
                            }
                        } else {
                            svWorldData.getSleepingPlayers().remove(uuid);
                        }
                    }

                    int numSleeping = svWorldData.getSleepingPlayers().size();
                    int required = getRequiredPlayerCount(world);

                    WorldProperties worldProperties = world.getProperties();
                    if (numSleeping >= required) {
                        svWorldData.setSkipping(true);
                        Task.builder().execute(() -> { // Add delay so that the night isn't instantly skipped when the last person sleeps
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
                            svWorldData.getSleepingPlayers().clear();
                            svWorldData.setSkipping(false);
                        }).async().delayTicks(10).submit(sleepVote);
                    }
                }
            }
        }).async().intervalTicks(1).submit(sleepVote);
    }

    @Listener
    public void onPreSleepingEvent(SleepingEvent.Pre event, @First Player player) {
        registerPlayer(player);
    }

    private void registerPlayer(Player player) {
        player.setSleepingIgnored(true); // Turn off vanilla sleeping to prevent a bug where the time advances (or doesn't, if /gamerule doDaylightCycle false, in which case it just kicks players out of bed without doing anything) but the plugin doesn't display the wakeup message.
        SVPlayerData svPlayerData = getSVPlayerData(player);

        svPlayerData.getSleepTask().ifPresent(Task::cancel);
        svPlayerData.setSleepTask(Task.builder().execute(() -> {
            if (isInBed(player) && !isIgnored(player)) {
                World world = player.getWorld();
                SVWorldData svWorldData = getSVWorldData(world);
                svWorldData.getSleepingPlayers().add(player.getUniqueId());

                Text text = messenger.parseMessage(enterBedMessage,
                        svWorldData.getSleepingPlayers().size(),
                        getRequiredPlayerCount(world),
                        player.getName(),
                        enablePrefix);
                messenger.sendWorldMessage(world, text);
                messenger.playWorldSound(world, SoundTypes.BLOCK_NOTE_HAT);

                if (messageLogging) {
                    logger.info("[" + world.getName() + "] " + text.toPlain());
                }
            }
        }).async().delayTicks(80).submit(sleepVote));
    }

    private int getRequiredPlayerCount(World world) {
        Set<Player> players = new HashSet<>(world.getPlayers());
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

    boolean isInBed(Player player) {
        // IS_SLEEPING key still doesn't work.
        //return player.get(Keys.IS_SLEEPING).get();

        // Workaround: takes advantage of the fact that the player's hitbox shrinks to a (almost) perfect 0.2*0.2*0.2 cube while in a bed.
        // But we only need to check the floored y-values! The player's hitbox is normally greater than 1, so if the floored y-value is 0, then the player must be in a bed! If it's equal to 1 or more, then the player is not in bed.
        return player.getBoundingBox().filter(b -> b.getSize().getFloorY() == 0).isPresent();
    }

    private SVPlayerData getSVPlayerData(Player player) {
        UUID uuid = player.getUniqueId();
        uuidsvPlayerDataMap.putIfAbsent(uuid, new SVPlayerData());
        return uuidsvPlayerDataMap.get(uuid);
    }

    private SVWorldData getSVWorldData(World world) {
        UUID uuid = world.getUniqueId();
        uuidsvWorldDataMap.putIfAbsent(uuid, new SVWorldData());
        return uuidsvWorldDataMap.get(uuid);
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
        getSVPlayerData(player).setHidden(true);
    }

    boolean isIgnored(Player player) {
        boolean isAFK = false;
        if (afkManager != null) {
            isAFK = afkManager.isAFK(player.getUniqueId());
        }

        SVPlayerData svPlayerData = getSVPlayerData(player);
        return (ignoreAdmins && player.hasPermission("sleepvote.hidden"))
                || isInIgnoredGameMode(player)
                || svPlayerData.isHidden()
                || isAFK
                || player.get(Keys.VANISH).get();
    }

    boolean isHidden(Player player) {
        return getSVPlayerData(player).isHidden();
    }

    void unignorePlayer(Player player) {
        getSVPlayerData(player).setHidden(false);
    }

    void mutePlayer(Player player) {
        getSVPlayerData(player).setMute(true);
    }

    void unmutePlayer(Player player) {
        getSVPlayerData(player).setMute(false);
    }

    boolean isMute(Player player) {
        return getSVPlayerData(player).isMute();
    }

    boolean areAdminsIgnored() {
        return ignoreAdmins;
    }

    Messenger getMessenger() {
        return messenger;
    }

    void dispose() {
        if (afkManager != null) {
            Sponge.getEventManager().unregisterListeners(afkManager);
        }
        votingUpdateLoop.cancel();
    }
}
