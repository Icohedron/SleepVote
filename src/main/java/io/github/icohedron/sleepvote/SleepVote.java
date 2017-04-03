package io.github.icohedron.sleepvote;

import com.google.inject.Inject;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import org.slf4j.Logger;
import org.spongepowered.api.Game;
import org.spongepowered.api.config.DefaultConfig;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.action.SleepingEvent;
import org.spongepowered.api.event.game.state.GameInitializationEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.world.World;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Plugin(id = "sleepvote", name = "Sleep Vote", version = "0.0.2")
public class SleepVote {

    private static final float  DEFAULT_PERCENT = 0.5f;
    private static final String DEFAULT_MESSAGE = "Wakey wakey, rise and shine!";

    @Inject
    @DefaultConfig(sharedRoot = true)
    private Path defaultConfigPath;

    private ConfigurationLoader<CommentedConfigurationNode> configLoader;
    private ConfigurationNode rootNode;

    @Inject
    private Logger logger;

    @Inject
    private Game game;

    private Map<World, Set<Player>> sleeping;
    private float percent;
    private String successMessage;

    @Listener
    public void onInitiation(GameInitializationEvent event) {
        sleeping = new HashMap<>();
        configLoader = HoconConfigurationLoader.builder().setPath(defaultConfigPath).build();
        try {
            rootNode = configLoader.load();
        } catch(IOException e) {
            logger.error("Failed to read configuration file (improper syntax?)! Falling back to default configuration...");
            percent = DEFAULT_PERCENT;
            successMessage = DEFAULT_MESSAGE;
            return;
        }

        if (rootNode.getChildrenMap().isEmpty()) {
            logger.info("No configuration file detected. Creating default configuration...");
            createDefaultConfig();
        } else {
            percent = rootNode.getNode("percent").getFloat();
            successMessage = rootNode.getNode("success_message").getString();

            logger.info("Got percent value of " + percent + " and success_message value of \"" + successMessage + "\"");
        }
    }

    @Listener
    public void onPlayerSleepPre(SleepingEvent.Pre event) {
        Optional<Player> player = event.getCause().first(Player.class);
        if (!player.isPresent()) {
            return;
        }

        World world = player.get().getWorld();
        if (!sleeping.containsKey(world)) {
            sleeping.put(world, new HashSet<>());
        }

        if (!(world.getProperties().getWorldTime() >= 12541 && world.getProperties().getWorldTime() <= 23458)) {
            return;
        }

        if (sleeping.get(world).add(player.get())) {
            String message = player.get().getName() + " wants to sleep! " + sleeping.get(world).size() + "/" + requiredPlayerCount(world);
            worldMessage(world, message);
        }
    }

    @Listener
    public void onPlayerSleepPost(SleepingEvent.Post event) {
        Optional<Player> player = event.getCause().first(Player.class);
        if (!player.isPresent()) {
            return;
        }

        World world = player.get().getWorld();
        if (sleeping.get(world).remove(player.get())) {
            String message = player.get().getName() + " left their bed! " + sleeping.get(world).size() + "/" + requiredPlayerCount(world);
            worldMessage(world, message);
        }
    }

    @Listener
    public void onPlayerSleepTick(SleepingEvent.Tick event) {
        for (World world : sleeping.keySet()) {
            if (sleeping.get(world).isEmpty()) {
                continue;
            }
            if (sleeping.get(world).size() >= requiredPlayerCount(world)) {
                world.getProperties().setWorldTime(0);
                worldMessage(world, "Wakey wakey, rise and shine!");
                sleeping.get(world).removeAll(sleeping.get(world));
            }
        }
    }

    private void worldMessage(World world, String message) {
        for (Player p : world.getPlayers()) {
            p.sendMessage(Text.builder(message).color(TextColors.YELLOW).build());
        }
    }

    private void createDefaultConfig() {
        percent = DEFAULT_PERCENT;
        successMessage = DEFAULT_MESSAGE;

        rootNode.getNode("percent").setValue(percent);
        rootNode.getNode("success_message").setValue(successMessage);

        try {
            configLoader.save(rootNode);
        } catch (IOException e) {
            logger.error("Failed to create default configuration file! Aborting...");
        }
    }

    private int requiredPlayerCount(World world) {
        return (int) Math.ceil(world.getPlayers().size() * percent);
    }
}
