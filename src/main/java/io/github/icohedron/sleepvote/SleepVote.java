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
import org.spongepowered.api.text.format.TextColors;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Plugin(id = PluginInfo.ID, name = PluginInfo.NAME, version = PluginInfo.VERSION,
        description = PluginInfo.DESCRIPTION,
        dependencies = @Dependency(id = "nucleus", optional = true))
public class SleepVote {

    private static final String SETTINGS_CONFIG_NAME = "configuration.properties";

    @Inject @ConfigDir(sharedRoot = false)
    private Path configurationDirectory;

    @Inject
    private Logger logger;

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
        sleepVoteManager = new SleepVoteManager(this, root); // Manages the core functionality of the plugin

        Sponge.getEventManager().registerListeners(this, sleepVoteManager);
    }

    private void initializeCommands() {

        // Permission Descriptions

        Sponge.getServiceManager().provide(PermissionService.class).ifPresent(permissionService -> {
        	PermissionDescription.Builder builder;

            builder = permissionService.newDescriptionBuilder(this);
            builder.id("sleepvote.hidden")
                    .description(Text.of("A user with this permission will be hidden from SleepVote if enabled in the config"))
                    .assign(PermissionDescription.ROLE_ADMIN, true)
                    .register();

            builder = permissionService.newDescriptionBuilder(this);
            builder.id("sleepvote.command.reload")
                    .description(Text.of("Allows the user to reload the plugin"))
                    .assign(PermissionDescription.ROLE_ADMIN, true)
                    .register();

            builder = permissionService.newDescriptionBuilder(this);
            builder.id("sleepvote.command.hide")
                    .description(Text.of("Allows the user to hide themself from SleepVote"))
                    .assign(PermissionDescription.ROLE_ADMIN, true)
                    .register();

            builder = permissionService.newDescriptionBuilder(this);
            builder.id("sleepvote.command.mute")
                    .description(Text.of("Allows the user to mute the sounds played to them by SleepVote"))
                    .assign(PermissionDescription.ROLE_USER, true)
                    .register();

            builder = permissionService.newDescriptionBuilder(this);
            builder.id("sleepvote.command.status")
                    .description(Text.of("Allows the user know about their current mute and hidden status"))
                    .assign(PermissionDescription.ROLE_USER, true)
                    .register();
        
            builder = permissionService.newDescriptionBuilder(this);
            builder.id("sleepvote.command")
                    .description(Text.of("Allows the user to execute all SleepVote commands"))
                    .assign(PermissionDescription.ROLE_USER, true)
                    .register();
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
                .description(Text.of("Hide or unhide yourself from SleepVote"))
                .permission("sleepvote.command.hide")
                .executor((src, args) -> {
                    if (!(src instanceof Player)) {
                        src.sendMessage(sleepVoteManager.getMessenger().addPrefix(Text.of("Only a player may execute this command")));
                        return CommandResult.empty();
                    }

                    Player player = (Player) src;
                    if (sleepVoteManager.isHidden(player)) {
                        sleepVoteManager.unignorePlayer(player);
                        player.sendMessage(sleepVoteManager.getMessenger().addPrefix(Text.of("You are no longer being hidden from SleepVote")));

                        if (sleepVoteManager.isInIgnoredGameMode(player)) {
                            player.sendMessage(sleepVoteManager.getMessenger().addPrefix(Text.of("Note: you are still hidden because of your gamemode. This server has made certain gamemodes hidden from SleepVote")));
                        }

                        if (sleepVoteManager.areAdminsIgnored() && player.hasPermission("sleepvote.hidden")) {
                            player.sendMessage(sleepVoteManager.getMessenger().addPrefix(Text.of("Note: you are still hidden because of your permissions. This server has made players with the permission 'sleepvote.hidden' hidden from SleepVote")));
                        }

                    } else {
                        sleepVoteManager.ignorePlayer(player);
                        player.sendMessage(sleepVoteManager.getMessenger().addPrefix(Text.of("You are now hidden from SleepVote")));
                    }

                    return CommandResult.success();

                })
                .build();

        CommandSpec muteCommand = CommandSpec.builder()
                .description(Text.of("Toggle on/off the sounds played by SleepVote"))
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
                        if (!sleepVoteManager.getMessenger().soundsEnabled()) {
                            player.sendMessage(sleepVoteManager.getMessenger().addPrefix(Text.of("Note: this server has sounds globally disabled for all users regardless of this setting")));
                        }
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

                    if (!sleepVoteManager.getMessenger().soundsEnabled()) {
                        mute = true;
                    }

                    Text visibilityText = ignored ? Text.of(TextColors.RED, "hidden") : Text.of(TextColors.GREEN, "visible");
                    Text soundText = mute ? Text.of(TextColors.RED, "off") : Text.of(TextColors.GREEN, "on");

                    player.sendMessage(sleepVoteManager.getMessenger().addPrefix(Text.of("Visiblity: ", visibilityText, " | Sounds: ", soundText)));

                    return CommandResult.success();

                })
                .build();

        CommandSpec sleepvoteCommand = CommandSpec.builder()
                .description(Text.of("The one command for all of SleepVote"))
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
        sleepVoteManager.dispose();
        Sponge.getEventManager().unregisterListeners(sleepVoteManager);
        loadConfiguration();
    }

    Logger getLogger() {
        return logger;
    }
}
