# Sleep Vote
When a set percentage of players are sleeping in a given world, the night will be skipped and the time will be set to morning.
This works independently for each world. (i.e. a sleep vote in one world will not change/affect the time in another).
Comes with configurable messages, toggleable sounds, and an ability to hide yourself from the plugin to not disturb the sleep of others.

***Quick note here: 
Current versions are considered to be "beta". These releases aim to provide the latest features as they are being developed and tested.

Eventually the versions will switch over to being "pre-releases" in which they will be deemed stable but may still contain a few bugs. At this point, it is *very important* for users to report any and all bugs they can find to the [Issue Tracker](https://ore.spongepowered.org/Icohedron/Sleep-Vote/issues)! This will help to ensure that the "release" version is the most stable as it can be!

Only once thorough testing is complete and all features have been fleshed out is when the all-awaited "release" version will come out.

Any additional updates after 1.0.0 will follow the familiar "snapshot -> pre-release -> release" track, which is similar to how current Minecraft versions are being developed.

That's all! Thanks for reading!***

## Commands
```
# The one command for the entire plugin
# Corresponding Permission: sleepvote.command
/sleepvote

# Allows the user to reload the plugin.
# Corresponding Permission: sleepvote.command.reload
/sleepvote reload

# Allows the user to hide themself from SleepVote.
# Corresponding Permission: sleepvote.command.hide
/sleepvote hide

# Allows the user to mute the sounds played to them by SleepVote
# Corresponding Permission: sleepvote.command.mute
/sleepvote mute
```

## Permissions
```
# Gives permissions to everything in the plugin
sleepvote

# Gives permission to execute all commands
sleepvote.command

# Gives permission to use the 'hide' command
sleepvote.command.hide

# Gives permission to use the 'mute' command
sleepvote.command.mute

# Users with this permission are ignored by the plugin when counting and calculating sleeping players
sleepvote.hidden

# Users with this permission will not have sounds played to them by the plugin
sleepvote.mute
```

## Screenshots
![alt text](http://i.imgur.com/sGm5ttn.png)
![alt text](http://i.imgur.com/rmTOGUc.png)
![alt text](http://i.imgur.com/ymdcy4p.png)

## Configuration file
```
# Percentage of players (in a world) required to be sleeping in order to advance through the night.
"required_percent_sleeping" = 0.5

#### Messages ####

# <player> is the name of the player who just went in/out of bed
# <sleeping> is the number of sleeping players in the world
# <required> is the number of players in the world required to be sleeping in order to advance through the night
# <percent> is simply (sleeping / required) * 100

"messages" {
    "wakeup" = "Wakey wakey, rise and shine!"
    "enter_bed" = "<player> wants to sleep! <sleeping>/<required> (<percent>%)"
    "exit_bed" = "<player> has left their bed. <sleeping>/<required> (<percent>%)"
}

# Enable the "[SleepVote]" chat prefix on the wakeup, enter_bed, and exit_bed messages (e.g. "[SleepVote] Wakey wakey, rise and shine!" when true, and "Wakey wakey, rise and shine!" when false)
"sleepvote_prefix" = true

# Toggle on/off the logging of the wakeup, enter_bed, and exit_bed messages messages in the server console
"enable_logging" = true

# Toggle on/of the sounds that play on the wakeup, enter_bed, and exit_bed messages
# Note that there is a command that allows players to mute the sounds for themselves: '/sleepvote mute' with the corresponding permission of 'sleepvote.command.mute'
"sound" = true

#### Counted Game Modes ####
# Determine which game modes are ignored when counting up players
# A value of 'true' means that players with that game mode will be ignored
"ignored_gamemodes" {
    "survival" = false
    "creative" = false
    "adventure" = false
    "spectator" = true
}

#### Nucleus Integration ####
# The following options require Nucleus in order to work

# Recommended: remove the permission 'nucleus.afk.base' (access to the '/afk' command) from players so that this feature is not abused
"ignore_afk_players" = false
```

## Build Instructions
Just run the following in a terminal:
```
gradlew build
```
The plugin jar file will then appear in /build/libs

## FAQ/Troubleshooting
Q: I installed the plugin, but when I sleep, nothing happens!

A: Players with the 'sleepvote.hidden' permission are ignored by the plugin. Try sleeping without op / unset the permission on your group/player using your permissions plugin. The command '/sleepvote hide' should also work, but give it a few seconds to kick in.

## Links
[W.I.P. Plugins Sponge Forum Thread](https://forums.spongepowered.org/t/sleep-vote-v0-4-0/18289)

[Sponge Ore Repository](https://ore.spongepowered.org/Icohedron/Sleep-Vote)
