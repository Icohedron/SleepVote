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

    private SleepVoteManager sleepVoteManager;
    private boolean unhideWarning;

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
                    logger.error("Failed to create configuration file! Aborting operation and falling back to default configuration");
                    e.printStackTrace();
                }
            } else { // Otherwise, there was likely a parsing error.
                logger.error("Failed to read configuration file! (Improper syntax?) Falling back to default configuration");
            }

            // In either case, just load the default configuration
            settingsConfigurationLoader = HoconConfigurationLoader.builder().setURL(Sponge.getAssetManager().getAsset(this, "default_" + SETTINGS_CONFIG_NAME).get().getUrl()).build();
            try {
                rootNode = Optional.of(settingsConfigurationLoader.load());
            } catch (IOException e) {
                logger.error("Failed to load default configuration! Shutting down plugin");
                System.exit(0);
            }

        }

        ConfigurationNode root = rootNode.get();
        unhideWarning = root.getNode("unhide_warning").getBoolean();
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
                        .description(Text.of("A user with this permission will be hidden from SleepVote if enabled in the config"))
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
                builder.id("sleepvote.command.status")
                        .description(Text.of("Allows the user know about their current mute and hidden status"))
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
                    src.sendMessage(sleepVoteManager.getMessenger().addPrefix(Text.of("Finished reloading configuration")));
                    return CommandResult.success();
                })
                .build();

        CommandSpec hideCommand = CommandSpec.builder()
                .description(Text.of("Hide yourself from SleepVote"))
                .permission("sleepvote.command.hide")
                .executor((src, args) -> {
                    if (!(src instanceof Player)) {
                        src.sendMessage(sleepVoteManager.getMessenger().addPrefix(Text.of("Only a player may execute this command")));
                        return CommandResult.empty();
                    }

                    Player player = (Player) src;
                    if (sleepVoteManager.isInIgnoredSet(player)) {
                        sleepVoteManager.unignorePlayer(player);
                        player.sendMessage(sleepVoteManager.getMessenger().addPrefix(Text.of("You are no longer hidden from SleepVote")));

                        if (unhideWarning) {
                            if (sleepVoteManager.isInIgnoredGameMode(player)) {
                                player.sendMessage(sleepVoteManager.getMessenger().addPrefix(Text.of("Note: Although you have successfully been removed from the list of hidden players, you are still in an ignored gamemode. Players with certain gamemodes are ignored by SleepVote. Talk to your server operator for more details")));
                            }

                            if (sleepVoteManager.areAdminsIgnored() && player.hasPermission("sleepvote.hidden")) {
                                player.sendMessage(sleepVoteManager.getMessenger().addPrefix(Text.of("Note: Although you have successfully been removed from the list of hidden players, you still have the permission 'sleepvote.hidden'. It has been detected that SleepVote is configured to always ignore players with this permission. Talk to your server operator for more details")));
                            }
                        }

                    } else {
                        sleepVoteManager.ignorePlayer(player);
                        player.sendMessage(sleepVoteManager.getMessenger().addPrefix(Text.of("You are now hidden from SleepVote")));
                    }

                    return CommandResult.success();

                })
                .build();

        CommandSpec muteCommand = CommandSpec.builder()
                .description(Text.of("Stop SleepVote from playing sounds to you"))
                .permission("sleepvote.command.mute")
                .executor((src, args) -> {
                    if (!(src instanceof Player)) {
                        src.sendMessage(sleepVoteManager.getMessenger().addPrefix(Text.of("Only a player may execute this command")));
                        return CommandResult.empty();
                    }

                    Player player = (Player) src;
                    if (sleepVoteManager.isMute(player)) {
                        sleepVoteManager.unmutePlayer(player);
                        player.sendMessage(sleepVoteManager.getMessenger().addPrefix(Text.of("SleepVote will now be able to play sounds to you")));
                    } else {
                        sleepVoteManager.mutePlayer(player);
                        player.sendMessage(sleepVoteManager.getMessenger().addPrefix(Text.of("SleepVote will no longer play sounds to you")));
                    }

                    return CommandResult.success();

                })
                .build();

        CommandSpec statusCommand = CommandSpec.builder()
                .description(Text.of("Tells you about your current mute and hidden status"))
                .permission("sleepvote.command.status")
                .executor((src, args) -> {
                    if (!(src instanceof Player)) {
                        src.sendMessage(sleepVoteManager.getMessenger().addPrefix(Text.of("Only a player may execute this command")));
                        return CommandResult.empty();
                    }

                    Player player = (Player) src;
                    boolean ignored = sleepVoteManager.isIgnored(player);
                    boolean mute = sleepVoteManager.isMute(player);

                    player.sendMessage(sleepVoteManager.getMessenger().addPrefix(Text.of("Ignored/Hidden: " + ignored + ", Mute Sounds: " + mute)));

                    return CommandResult.success();

                })
                .build();

        CommandSpec sleepvoteCommand = CommandSpec.builder()
                .description(Text.of("The one command for all of SleepVote"))
                .permission("sleepvote.command")
                .child(reloadCommand, "reload", "r")
                .child(hideCommand, "hide", "h")
                .child(muteCommand, "mute", "m")
                .child(statusCommand, "status", "s")
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
}
