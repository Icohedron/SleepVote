# Sleep Vote
This plugin allows players to make a vote to skip the night. This is done simply by sleeping in a bed.

### Features
- World-independent votes. A vote to change the time in one world will not affect another
- Configurable wakeup, enter bed, and exit bed messages
- Sounds that play on each of the above messages, which can be muted per-user via the command '/sleepvote mute', or for all users by modifying the config value "sounds"
- Got administrators? They can be hidden from sleep votes using the command '/sleepvote hide' which persists until the server restarts. Got dedicated administrators? Perhaps giving them the permission 'sleepvote.hidden' and enabling "hide_admins" in the config would be for you, since that will keep admins hidden after server restarts as well
- Players with certain gamemodes may not want to be counted in votes to skip the night. That can be done in the config. By default, only spectator is ignored

### Version Notice
Current versions are considered to be "beta". These releases aim to provide the latest features as they are being developed and tested.

Eventually the versions will switch over to being "pre-releases" in which they will be deemed stable but may still contain a few bugs. The first prerelease will be aptly named "1.0-PRERELEASE-1"  
At this point, it is *very important* for users to report any and all bugs they can find to the [Issue Tracker](https://ore.spongepowered.org/Icohedron/Sleep-Vote/issues)! This will help to ensure that the "release" version is the most stable as it can be!

Only once thorough testing is complete and all features have been fleshed out is when the all-awaited "release" version will come out (version "1.0").

Any additional updates afterwards will follow the familiar "snapshot -> pre-release -> release" track, which is similar to how current Minecraft versions are being developed.

## Commands
```
# The one command for the entire plugin
# Corresponding Permission: sleepvote.command
/sleepvote
/sv

# Allows the user to reload the plugin
# Corresponding Permission: sleepvote.command.reload
/sleepvote reload
/sv r

# Allows the user to hide themself from SleepVote
# Corresponding Permission: sleepvote.command.hide
/sleepvote hide
/sv h

# Allows the user to mute the sounds played to them by SleepVote
# Corresponding Permission: sleepvote.command.mute
/sleepvote mute
/sv m

# Tells the user about their current status regarding: whether or not they are hidden/ignored, and whether or not the plugin sounds are muted for them
# Corresponding Permission: sleepvote.command.status
/sleepvote status
/sv s
```

## Permissions
```
# Gives permissions to everything in the plugin
sleepvote

# Gives permission to execute the commands. Recommended for all users.
sleepvote.command

# Gives permission to execute the 'hide' command. Recommended for admins only.
sleepvote.command.hide

# Gives permission to execute the 'mute' command. Recommended for all users.
sleepvote.command.mute

# Gives permission to execute the 'status' command. Recommended for admins, but users are okay too.
sleepvote.command.status

# Users with this permission are ignored by the plugin when counting and calculating sleeping players. Recommended only for admins.
# Must be enabled in the config
sleepvote.hidden

# A typical permission setup for default users:
# [+] sleepvote.command
# [-] sleepvote.command.hide
# [-] sleepvote.command.reload
# [-] sleepvote.command.status
```

## Screenshots
![alt text](http://i.imgur.com/sGm5ttn.png)
![alt text](http://i.imgur.com/rmTOGUc.png)
![alt text](http://i.imgur.com/ymdcy4p.png)

## Configuration file
```
#### Counting Required Players ####
# You are given two options: percent and number
# If both are specified, the plugin will choose the lower of the two requirements

# Below are some presets. '%' is the value for "required_percent_sleeping" and '#' is the value for "required_number_sleeping"

# Preset: Percent Only (Default)
# '%' = <any percent>
# '#' = 0

# Preset: Number Only
# '%' = 1.0
# '#' = <any number>

# Preset: Dynamic
# The plugin will choose the smaller of the two requirements. (e.g. Say the population is 50 and '%' = 0.5 and '#' = 10. The plugin will choose 10 as a requirement rather than 25 (which is 50 * 0.5))
# '%' = <any percent>
# '#' = <any number>

# Now here's the fun part. You decide how you want it!

# Percentage of players (in a world) required to be sleeping in order to advance through the night
# Must be a value in the inclusive range of [0.0,1.0]. If set to zero, it will only require one player
"required_percent_sleeping" = 0.5

# Number of players (in a world) required to be sleeping in order to advance through the night
# If there are less players than what is specified, then it will use the percentage instead
# Set to a value less than or equal to (<=) 0 to disable. It is disabled by default
"required_number_sleeping" = 0

#### Messages ####

# <player> is the name of the player who just went in/out of bed
# <sleeping> is the number of sleeping players in the world
# <required> is the number of players in the world required to be sleeping in order to advance through the night
# <percent> is simply the percentage of players sleeping out of the number of players required to sleep. Calculated internally as '(sleeping / required) * 100'

"messages" {
    "wakeup" = "Wakey wakey, rise and shine!"
    "enter_bed" = "<player> wants to sleep! <sleeping>/<required> (<percent>%)"
    "exit_bed" = "<player> has left their bed. <sleeping>/<required> (<percent>%)"
}

# Enable or disable the "[SleepVote]" chat prefix on the wakeup, enter_bed, and exit_bed messages (e.g. "[SleepVote] Wakey wakey, rise and shine!" when true, and "Wakey wakey, rise and shine!" when false)
"sleepvote_prefix" = true

# Toggle on/off the logging of the wakeup, enter_bed, and exit_bed messages messages in the server console, prefixed by the world name
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

#### Administration ####
# If set to true, players with the permission 'sleepvote.hidden' will be ignored, regardless of their status when using the '/sleepvote hide' command. This includes ops
# Set to false by default since some people have been confused, claiming that the plugin "didn't work" when they slept simply because they were trying it out while in op
# The advantage of this is that it persists across server restarts whereas '/sleepvote hide' does not.
"ignore_admins" = false

#### Miscellaneous ####
# Lets players know if they are still hidden after trying to unhide themselves using '/sleepvote hide'
# It tells them if "ignore_admins" is true and they have the permission 'sleepvote.hidden'
# And/or it tells them that they are in an ignored gamemode
"unhide_warning" = true

#### Nucleus Integration ####
# The following options require Nucleus in order to work

# Recommended: remove the permission 'nucleus.afk.base' (access to the '/afk' command) from players so that this feature is not abused
"ignore_afk_players" = false
```

## Build Instructions
Just run the following in a terminal:
```
./gradlew build
```
The plugin jar file will then appear in './build/libs'

## Links
[Sponge Ore Repository](https://ore.spongepowered.org/Icohedron/Sleep-Vote)
