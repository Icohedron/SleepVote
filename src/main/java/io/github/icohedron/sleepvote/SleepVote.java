package io.github.icohedron.sleepvote;

import com.google.inject.Inject;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.GameReloadEvent;
import org.spongepowered.api.event.game.state.GameInitializationEvent;
import org.spongepowered.api.plugin.Dependency;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.service.permission.PermissionDescription;
import org.spongepowered.api.service.permission.PermissionService;
import org.spongepowered.api.text.Text;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Plugin(id = "sleepvote", name = "SleepVote", version = "0.6-BETA-1",
        dependencies = @Dependency(id = "nucleus", optional = true))
public class SleepVote {

    private static final String SETTINGS_CONFIG_NAME = "settings.conf";

    @Inject @ConfigDir(sharedRoot = false) private Path configurationDirectory;
    @Inject private Logger logger;

    private Messenger messenger;
    private SleepVoteManager sleepVoteManager;

    @Listener
    public void onInitializationEvent(GameInitializationEvent event) {
        loadConfiguration();
        initializeCommands();
        logger.info("Finished initialization");
        Sponge.getServiceManager().provide(PermissionService.class);
    }

    private void loadConfiguration() {
        Path configurationFilePath = configurationDirectory.resolve(SETTINGS_CONFIG_NAME);
        ConfigurationLoader<CommentedConfigurationNode> settingsConfigurationLoader = HoconConfigurationLoader.builder().setPath(configurationFilePath).build();
        Optional<ConfigurationNode> rootNode;

        try {
            rootNode = Optional.of(settingsConfigurationLoader.load());
        } catch (IOException e) {
            logger.error("An error occurred while attempting to read the configuration file!");
            logger.error(e.getMessage());
            rootNode = Optional.empty();
        }

        if (!rootNode.filter(node -> !node.getChildrenMap().isEmpty()).isPresent()) {

            if (Files.notExists(configurationFilePath)) { // Probably the case if the root node is virtual.
                logger.info("No configuration file detected. Creating configuration file...");
                try {
                    Files.createDirectories(configurationDirectory);
                    Sponge.getAssetManager().getAsset(this, "default_" + SETTINGS_CONFIG_NAME).get().copyToFile(configurationFilePath);
                    logger.info("Finished! Configuration file saved to \"" + configurationFilePath.toString() + "\"");
                } catch (IOException e) {
                    logger.error("Failed to create configuration file! Aborting operation and falling back to default configuration.");
                    e.printStackTrace();
                }
            } else { // Otherwise, there was likely a parsing error.
                logger.error("Failed to read configuration file! (Improper syntax?) Falling back to default configuration.");
            }

            // In either case, just load the default configuration
            settingsConfigurationLoader = HoconConfigurationLoader.builder().setURL(Sponge.getAssetManager().getAsset(this, "default_" + SETTINGS_CONFIG_NAME).get().getUrl()).build();
            try {
                rootNode = Optional.of(settingsConfigurationLoader.load());
            } catch (IOException e) {
                logger.error("Failed to fall back to default configuration! Shutting down plugin");
                System.exit(0);
            }

        }

        ConfigurationNode root = rootNode.get();
        messenger = new Messenger(root.getNode("sound").getBoolean());
        sleepVoteManager = new SleepVoteManager(this, root); // Manages the core functionality of the plugin

        Sponge.getEventManager().registerListeners(this, sleepVoteManager);
    }

    private void initializeCommands() {

        // Permission Descriptions

        Sponge.getServiceManager().provide(PermissionService.class).ifPresent(permissionService -> {
            Optional<PermissionDescription.Builder> optBuilder;

            optBuilder = permissionService.newDescriptionBuilder(this);
            if (optBuilder.isPresent()) {
                PermissionDescription.Builder builder = optBuilder.get();
                builder.id("sleepvote.hidden")
                        .description(Text.of("A user with this permission will be hidden from SleepVote"))
                        .assign(PermissionDescription.ROLE_ADMIN, true)
                        .register();
            }

            optBuilder = permissionService.newDescriptionBuilder(this);
            if (optBuilder.isPresent()) {
                PermissionDescription.Builder builder = optBuilder.get();
                builder.id("sleepvote.command.reload")
                        .description(Text.of("Allows the user to reload the plugin"))
                        .assign(PermissionDescription.ROLE_ADMIN, true)
                        .register();
            }

            optBuilder = permissionService.newDescriptionBuilder(this);
            if (optBuilder.isPresent()) {
                PermissionDescription.Builder builder = optBuilder.get();
                builder.id("sleepvote.command.hide")
                        .description(Text.of("Allows the user to hide themself from SleepVote"))
                        .assign(PermissionDescription.ROLE_ADMIN, true)
                        .register();
            }

            optBuilder = permissionService.newDescriptionBuilder(this);
            if (optBuilder.isPresent()) {
                PermissionDescription.Builder builder = optBuilder.get();
                builder.id("sleepvote.command.mute")
                        .description(Text.of("Allows the user to mute the sounds played to them by SleepVote"))
                        .assign(PermissionDescription.ROLE_USER, true)
                        .register();
            }

            optBuilder = permissionService.newDescriptionBuilder(this);
            if (optBuilder.isPresent()) {
                PermissionDescription.Builder builder = optBuilder.get();
                builder.id("sleepvote.command")
                        .description(Text.of("Allows the user to execute the sleepvote command"))
                        .assign(PermissionDescription.ROLE_USER, true)
                        .register();
            }
        });

        // Command implementations

        CommandSpec reloadCommand = CommandSpec.builder()
                .description(Text.of("Reload configuration"))
                .permission("sleepvote.command.reload")
                .executor((src, args) -> {
                    reload();
                    src.sendMessage(messenger.addPrefix(Text.of("Finished reloading configuration")));
                    return CommandResult.success();
                })
                .build();

        CommandSpec hideCommand = CommandSpec.builder()
                .description(Text.of("Hide yourself from SleepVote"))
                .permission("sleepvote.command.hide")
                .executor((src, args) -> {
                    if (!(src instanceof Player)) {
                        src.sendMessage(messenger.addPrefix(Text.of("Only a player may execute this command")));
                        return CommandResult.empty();
                    }

                    Player player = (Player) src;
                    if (sleepVoteManager.isInIgnoredSet(player)) {
                        sleepVoteManager.unignorePlayer(player);
                    } else {
                        sleepVoteManager.ignorePlayer(player);
                    }

                    return CommandResult.success();

                })
                .build();

        CommandSpec muteCommand = CommandSpec.builder()
                .description(Text.of("Stop SleepVote from playing sounds to you"))
                .permission("sleepvote.command.mute")
                .executor((src, args) -> {
                    if (!(src instanceof Player)) {
                        src.sendMessage(messenger.addPrefix(Text.of("Only a player may execute this command")));
                        return CommandResult.empty();
                    }

                    Player player = (Player) src;
                    if (sleepVoteManager.isMute(player)) {
                        sleepVoteManager.unmutePlayer(player);
                    } else {
                        sleepVoteManager.mutePlayer(player);
                    }

                    return CommandResult.success();

                })
                .build();

        CommandSpec sleepvoteCommand = CommandSpec.builder()
                .description(Text.of("The one command for all of SleepVote"))
                .permission("sleepvote.command")
                .child(reloadCommand, "reload", "r")
                .child(hideCommand, "hide", "h")
                .child(muteCommand, "mute", "m")
                .build();

        Sponge.getCommandManager().register(this, sleepvoteCommand, "sleepvote", "sv");
    }

    @Listener
    public void onReloadEvent(GameReloadEvent event) {
        reload();
    }

    private void reload() {
        sleepVoteManager.unregisterListeners();
        Sponge.getEventManager().unregisterListeners(sleepVoteManager);
        loadConfiguration();
    }

    Logger getLogger() {
        return logger;
    }

    Messenger getMessenger() {
        return messenger;
    }
}
