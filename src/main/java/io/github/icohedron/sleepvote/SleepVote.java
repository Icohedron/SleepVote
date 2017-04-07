package io.github.icohedron.sleepvote;

import com.google.inject.Inject;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.action.SleepingEvent;
import org.spongepowered.api.event.filter.cause.First;
import org.spongepowered.api.event.game.GameReloadEvent;
import org.spongepowered.api.event.game.state.GameInitializationEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.world.World;
import org.spongepowered.api.world.storage.WorldProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Plugin(id = "sleepvote", name = "SleepVote")
public class SleepVote {

    @Inject
    @ConfigDir(sharedRoot = false)
    private Path configurationDirectory;

    @Inject
    private Logger logger;

    private Map<World, Set<Player>> sleeping;
    private Map<Player, Task> playerSleepRequests;

    private boolean enablePrefix;
    private boolean messageLogging;

    private float requiredPercentSleeping;
    private String wakeupMessage;
    private String enterBedMessage;
    private String exitBedMessage;

    @Listener
    public void onInitializationEvent(GameInitializationEvent event) {
        sleeping = new HashMap<>();
        playerSleepRequests = new HashMap<>();
        loadConfiguration();
        initializeCommands();
    }

    public void loadConfiguration() {
        Path configurationFilePath = configurationDirectory.resolve("sleepvote.conf");
        ConfigurationLoader configurationLoader = HoconConfigurationLoader.builder().setPath(configurationFilePath).build();
        Optional<ConfigurationNode> rootNode = getRootNode(configurationLoader);

        if (!rootNode.filter(node -> !node.getChildrenMap().isEmpty()).isPresent()) {

            if (Files.notExists(configurationFilePath)) { // Probably the case if the root node is virtual.
                logger.info("No configuration file detected. Creating configuration file...");
                try {
                    Files.createDirectories(configurationDirectory);
                    Sponge.getAssetManager().getAsset(this, "default_sleepvote.conf").get().copyToFile(configurationFilePath);
                    logger.info("Finished! Configuration file saved to \"" + configurationFilePath.toString() + "\"");
                } catch (IOException e) {
                    logger.error("Failed to create configuration file! Aborting operation and falling back to default configuration.");
                    e.printStackTrace();
                }
            } else { // Otherwise, there was likely a parsing error.
                logger.error("Failed to read configuration file! (Improper syntax?) Falling back to default configuration.");
            }

            // In both cases, just load the default configuration.
            configurationLoader = HoconConfigurationLoader.builder().setURL(Sponge.getAssetManager().getAsset(this, "default_sleepvote.conf").get().getUrl()).build();
            rootNode = getRootNode(configurationLoader);
        }

        ConfigurationNode root = rootNode.get();
        enablePrefix = root.getNode("sleepvote_prefix").getBoolean();
        messageLogging = root.getNode("enable_logging").getBoolean();
        requiredPercentSleeping = root.getNode("required_percent_sleeping").getFloat();
        wakeupMessage = root.getNode("messages", "wakeup").getString();
        enterBedMessage = root.getNode("messages", "enter_bed").getString();
        exitBedMessage = root.getNode("messages", "exit_bed").getString();

        // Hard-coded defaults in case these values are invalid or missing

        if (requiredPercentSleeping <= 0.0f || requiredPercentSleeping > 1.0f) {
            logger.info("Invalid or missing required_percent_sleeping value. Using default of 0.5");
            requiredPercentSleeping = 0.5f;
        }

        if (wakeupMessage == null) {
            logger.info("Missing messages.wakeup string. Using default of \"Wakey wakey, rise and shine!\"");
            wakeupMessage = "Wakey wakey, rise and shine!";
        }

        if (enterBedMessage == null) {
            logger.info("Missing messages.enter_bed string. Using default of \"<player> wants to sleep! <sleeping>/<active> (<percent>%)\"");
            enterBedMessage = "<player> wants to sleep! <sleeping>/<active> (<percent>%)";
        }

        if (exitBedMessage == null) {
            logger.info("Missing messages.exit_bed string. Using default of \"<player> has left their bed. <sleeping>/<active> (<percent>%)\"");
            exitBedMessage = "<player> has left their bed. <sleeping>/<active> (<percent>%)";
        }
    }

    private Optional<ConfigurationNode> getRootNode(ConfigurationLoader configurationLoader) {
        try {
            return Optional.ofNullable(configurationLoader.load());
        } catch (IOException e) {
            logger.error("An error occurred while attempting to read the configuration file!");
            e.printStackTrace();
            return Optional.empty();
        }
    }

    private void initializeCommands() {
        CommandSpec reloadCommand = CommandSpec.builder()
                .description(Text.of("Reload configuration"))
                .permission("sleepvote.command.reload")
                .executor((src, args) -> {
                    loadConfiguration();
                    src.sendMessage(addPrefix(Text.of("Finished reloading configuration")));
                    return CommandResult.success();
                })
                .build();

        // TODO: Commands (e.g. '/sleepvote ignore <player>', '/sleepvote unignore <player>')

        CommandSpec sleepvoteCommand = CommandSpec.builder()
                .description(Text.of("The one command for all of SleepVote"))
                .permission("sleepvote.command")
                .child(reloadCommand, "reload", "r")
                .build();

        Sponge.getCommandManager().register(this, sleepvoteCommand, "sleepvote", "sv");
    }

    //// Sleeping Mechanics ////

    @Listener
    public void onPreSleepingEvent(SleepingEvent.Pre event, @First Player player) {
        if (playerSleepRequests.containsKey(player)) {
            playerSleepRequests.get(player).cancel();
        }

        player.setSleepingIgnored(true); // Turn off vanilla sleeping to prevent a bug where the time advances (or doesn't, if /gamerule doDaylightCycle false, in which case it just kicks players out of bed without doing anything) but the plugin doesn't display the wakeup message.

        playerSleepRequests.put(player, Task.builder().execute(() -> {
            if (isInBed(player)) {
                World world = player.getWorld();
                sleeping.computeIfAbsent(world, w -> new HashSet<>());
                sleeping.get(world).add(player);
                sendWorldMessage(world, parseMessage(enterBedMessage, world, Optional.of(player)));
            }
        }).async().delay(4, TimeUnit.SECONDS).submit(this));
    }

    @Listener
    public void onSleepTickEvent(SleepingEvent.Tick event) {
        for (World world : sleeping.keySet()) {
            Set<Player> sleepingPlayers = sleeping.get(world);
            if (!sleepingPlayers.isEmpty()) {
                int numSleeping = sleepingPlayers.size();
                int required = getRequiredPlayerCount(world);
                WorldProperties worldProperties = world.getProperties();

                if (numSleeping >= required) {
                    worldProperties.setWorldTime(((int) Math.ceil(worldProperties.getWorldTime() / 24000.0f)) * 24000); // Set time to the next multiple 24000 ticks (equivalent to '/time set 0')
                    sendWorldMessage(world, parseMessage(wakeupMessage, world, Optional.empty()));
                    sleepingPlayers.clear();
                }
            }
        }
    }

    @Listener
    public void onPostSleepingEvent(SleepingEvent.Post event, @First Player player) {
        World world = player.getWorld();
        sleeping.computeIfAbsent(world, w -> new HashSet<>());
        player.setSleepingIgnored(false);
        if (sleeping.get(world).remove(player)) {
            sendWorldMessage(world, parseMessage(exitBedMessage, world, Optional.of(player)));
        }
    }

    private Text addPrefix(Text text) {
        Text prefix = Text.of(TextColors.GREEN, "[", TextColors.RED, "SleepVote", TextColors.GREEN, "] ");
        return Text.of(prefix, text);
    }

    private Text parseMessage(String message, World world, Optional<Player> player) {
        String playerName = player.isPresent() ? player.get().getName() : "Undefined";
        int sleepingPlayers = sleeping.get(world).size();
        int required = getRequiredPlayerCount(world);
        int percent = (int) (sleepingPlayers * 100.0f / required);

        Text text = Text.of(TextColors.YELLOW, message.replace("<player>", playerName)
                               .replace("<sleeping>", Integer.toString(sleepingPlayers))
                               .replace("<required>", Integer.toString(required))
                               .replace("<percent>", Integer.toString(percent)));

        Text result = enablePrefix ? addPrefix(text) : text;
        if (messageLogging) {
            logger.info("[" + world.getName() + "] " + result.toPlain());
        }
        return result;
    }

    private boolean isInBed(Player player) {
        // Doesn't work due to bug: https://forums.spongepowered.org/t/warnings-on-startup-skipping-keys/18338
//        return player.get(Keys.IS_SLEEPING).filter(k -> k.booleanValue()).isPresent();

        // Workaround: takes advantage of the fact that the player's hitbox shrinks to a (almost) perfect 0.2*0.2*0.2 cube while in a bed.
        // But we only need to check the floored y-values! The player's hitbox is normally greater than 1, so if the floored y-value is 0, then the player must be in a bed! If it's equal to 1 or more, then the player is not in bed.
        return player.getBoundingBox().filter(b -> b.getSize().getFloorY() == 0).isPresent();
    }

    private void sendWorldMessage(World world, Text message) {
        for (Player p : world.getPlayers()) {
            p.sendMessage(message);
        }
    }

    private int getRequiredPlayerCount(World world) {
        // TODO: Automatically add exclusions for specific players (such as vanished, afk, etc.)
        return (int) Math.ceil(world.getPlayers().size() * requiredPercentSleeping);
    }

    @Listener
    public void onReloadEvent(GameReloadEvent event) {
        loadConfiguration();
    }
}
