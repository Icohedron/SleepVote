package io.github.icohedron.sleepvote;

import com.google.inject.Inject;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.GameReloadEvent;
import org.spongepowered.api.event.game.state.GameInitializationEvent;
import org.spongepowered.api.plugin.Dependency;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.service.permission.PermissionDescription;
import org.spongepowered.api.service.permission.PermissionService;
import org.spongepowered.api.service.permission.SubjectData;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.util.Tristate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Plugin(id = "sleepvote", name = "SleepVote", version = "0.5.1",
        dependencies = @Dependency(id = "nucleus", optional = true))
public class SleepVote {

    private static final String SETTINGS_CONFIG_NAME = "settings.conf";

    @Inject @ConfigDir(sharedRoot = false) private Path configurationDirectory;
    @Inject private Logger logger;

    private Messenger messenger;
    private SleepVoteManager sleepVoteManager;

    private Set<CommandSource> hideCooldown;
    private Set<CommandSource> muteCooldown;

    @Listener
    public void onInitializationEvent(GameInitializationEvent event) {
        loadConfiguration();
        initializeCommands();
        hideCooldown = new HashSet<>();
        muteCooldown = new HashSet<>();
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

        boolean enablePrefix = root.getNode("sleepvote_prefix").getBoolean();
        boolean messageLogging = root.getNode("enable_logging").getBoolean();
        boolean ignoreAFKPlayers = root.getNode("ignore_afk_players").getBoolean();
        float requiredPercentSleeping = root.getNode("required_percent_sleeping").getFloat();

        HashMap<String, String> strings = new HashMap<>();
        strings.put("wakeup_message", root.getNode("messages", "wakeup").getString());
        strings.put("enter_bed_message", root.getNode("messages", "enter_bed").getString());
        strings.put("exit_bed_message", root.getNode("messages", "exit_bed").getString());

        boolean[] ignoredGameModes = new boolean[4];
        ignoredGameModes[0] = root.getNode("ignored_gamemodes", "survival").getBoolean();
        ignoredGameModes[1] = root.getNode("ignored_gamemodes", "creative").getBoolean();
        ignoredGameModes[2] = root.getNode("ignored_gamemodes", "adventure").getBoolean();
        ignoredGameModes[3] = root.getNode("ignored_gamemodes", "spectator").getBoolean();

        // Hard-coded defaults in case these values are invalid or missing

        if (requiredPercentSleeping < 0.0f || requiredPercentSleeping > 1.0f) {
            requiredPercentSleeping = 0.5f;
            logger.info("Invalid or missing required_percent_sleeping value. Using default of 0.5");
        }

        if (strings.get("wakeup_message") == null) {
            strings.put("wakeup_message", "Wakey wakey, rise and shine!");
            logger.info("Missing messages.wakeup string. Using default of \"" + strings.get("wakeup_message") + "\"");
        }

        if (strings.get("enter_bed_message") == null) {
            strings.put("enter_bed_message", "<player> wants to sleep! <sleeping>/<active> (<percent>%)");
            logger.info("Missing messages.enter_bed string. Using default of \"" + strings.get("enter_bed_message") + "\"");
        }

        if (strings.get("exit_bed_message") == null) {
            strings.put("exit_bed_message", "<player> has left their bed. <sleeping>/<active> (<percent>%)");
            logger.info("Missing messages.exit_bed string. Using default of \"" + strings.get("exit_bed_message") + "\"");
        }

        // Create the class that manages all the main functionality of SleepVote

        sleepVoteManager = new SleepVoteManager(this, requiredPercentSleeping, strings, ignoredGameModes);

        if (enablePrefix) {
            sleepVoteManager.enableMessagePrefix();
        }

        if (messageLogging) {
            sleepVoteManager.enableMessageLogging();
        }

        if (ignoreAFKPlayers) {
            sleepVoteManager.ignoreAfkPlayers();
        }

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
                builder.id("sleepvote.mute")
                        .description(Text.of("A user with this permission will not have sounds played to them by SleepVote"))
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

                    if (hideCooldown.contains(src)) {
                        src.sendMessage(messenger.addPrefix(Text.of("Please wait before trying the command again")));
                        return CommandResult.empty();
                    }

                    Text fail = messenger.addPrefix(Text.of("An error has occured"));

                    SubjectData subject = src.getSubjectData();
                    if (src.hasPermission("sleepvote.hidden")) {
                        if (subject.setPermission(new HashSet<>(), "sleepvote.hidden", Tristate.FALSE)) {
                            src.sendMessage(messenger.addPrefix(Text.of("You are no longer hidden from SleepVote")));
                        } else {
                            src.sendMessage(fail);
                            return CommandResult.empty();
                        }
                    } else {
                        if (subject.setPermission(new HashSet<>(), "sleepvote.hidden", Tristate.TRUE)) {
                            src.sendMessage(messenger.addPrefix(Text.of("You are now hidden from SleepVote")));
                        } else {
                            src.sendMessage(fail);
                            return CommandResult.empty();
                        }
                    }

                    hideCooldown.add(src); // Cooldown required due to results from testing
                    Task.builder().execute(() -> hideCooldown.remove(src)).async().delay(3, TimeUnit.SECONDS).submit(this);

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

                    if (muteCooldown.contains(src)) {
                        src.sendMessage(messenger.addPrefix(Text.of("Please wait before trying the command again")));
                        return CommandResult.empty();
                    }

                    Text fail = messenger.addPrefix(Text.of("An error has occured"));

                    SubjectData subject = src.getSubjectData();
                    if (src.hasPermission("sleepvote.mute")) {
                        if (subject.setPermission(new HashSet<>(), "sleepvote.mute", Tristate.FALSE)) {
                            src.sendMessage(messenger.addPrefix(Text.of("SleepVote will now play sounds to you")));
                        } else {
                            src.sendMessage(fail);
                            return CommandResult.empty();
                        }
                    } else {
                        if (subject.setPermission(new HashSet<>(), "sleepvote.mute", Tristate.TRUE)) {
                            src.sendMessage(messenger.addPrefix(Text.of("SleepVote will no longer play sounds to you")));
                        } else {
                            src.sendMessage(fail);
                            return CommandResult.empty();
                        }
                    }

                    muteCooldown.add(src); // Cooldown required due to results from testing
                    Task.builder().execute(() -> muteCooldown.remove(src)).async().delay(3, TimeUnit.SECONDS).submit(this);

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
