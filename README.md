# Sleep Vote
This plugin allows players to make a vote to skip the night. This is done simply by sleeping in a bed.

### Features
- World-independent votes. A vote to change the time in one world will not affect another
- Choose between having a fixed number of players sleeping, or a percentage of players sleeping. Also supports dynamic requirements, in which the plugin will choose the lower of the two values. (Say there are 50 players. You choose a percentage of 0.5 and a fixed value of 10. The plugin will choose 10 since 10 is lower than 25)
- Customizable wakeup, enter bed, and exit bed messages. Supports the use of [Minecraft formatting codes](http://minecraft.gamepedia.com/Formatting_codes). Also has sounds that play when these messages are displayed, which can be muted per-user via the command '/sleepvote mute', or globally for all users by modifying the configuration property "sounds"
- Got administrators? They can be hidden from votes and sleep requirements by using the command '/sleepvote hide' which persists until the server restarts or the plugin is reloaded. Got dedicated administrators? Perhaps giving them the permission 'sleepvote.hidden' and enabling "hide_admins" in the config would be for you, since that will keep admins hidden after server restarts and plugin reloads as well
- Vanished players are excluded from sleep votes regardless of the value of "hide_admins"
- Players with certain gamemodes may not want to be counted in votes to skip the night. This can be set in the configuration file. By default, only spectator is ignored
- Optional [Nucleus](https://ore.spongepowered.org/Nucleus/Nucleus) integration for allowing AFK players to be ignored during votes

## Commands
```
# Allows the user to reload the plugin
# Corresponding permission: sleepvote.command.reload
/sleepvote reload
/sv r

# Allows the user to hide themself from SleepVote
# Corresponding permission: sleepvote.command.hide
/sleepvote hide
/sv h

# Allows the user to mute the sounds played to them by SleepVote
# Corresponding permission: sleepvote.command.mute
/sleepvote mute
/sv m

# Tells the user about their current status regarding visiblity (visible/hidden) and sounds (on/off)
# Corresponding permission: sleepvote.command.status
/sleepvote status
/sv s
```

## Permissions
```
# Gives permissions to everything in the plugin. Recommended for admins only
sleepvote

# Gives permission to execute all commands. Recommended for admins only
sleepvote.command

# Gives permission to execute the 'hide' command. Recommended for admins only
sleepvote.command.hide

# Gives permission to execute the 'mute' command. Recommended for all users
sleepvote.command.mute

# Gives permission to execute the 'status' command. Recommended for all users
sleepvote.command.status

# Users with this permission are ignored by the plugin when counting and calculating sleeping players. Recommended for admins only
# Must be enabled in the configuration file under the property "ignore_admins"
sleepvote.hidden

# A typical permission setup for default users:
# [+] sleepvote.command.mute
# [+] sleepvote.command.status

# A typical permission setup for admins:
# [+] sleepvote.command

# A typical permission setup for dedicated admins:
# [+] sleepvote
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

# Formatting codes may be used in messages using '\u00A7' (e.g. "\u00A7cHello!" is will print "Hello!" in red text)
# A full reference for formatting codes can be found at http://minecraft.gamepedia.com/Formatting_codes/

"messages" {
    "wakeup" = "\u00A7eWakey wakey, rise and shine!"
    "enter_bed" = "\u00A7e<player> wants to sleep! \u00A76<sleeping>/<required> (<percent>%)"
    "exit_bed" = "\u00A7e<player> has left their bed. \u00A76<sleeping>/<required> (<percent>%)"
}

# Enable or disable the "[SleepVote]" chat prefix on the wakeup, enter_bed, and exit_bed messages (e.g. "[SleepVote] Wakey wakey, rise and shine!" when true, and "Wakey wakey, rise and shine!" when false)
"sleepvote_prefix" = true

# Toggle on/off the logging of the wakeup, enter_bed, and exit_bed messages messages in the server console, prefixed by the world name
"enable_logging" = true

# Toggle on/off the sounds that play on the wakeup, enter_bed, and exit_bed messages
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
