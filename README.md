# Sleep Vote
When a set percentage of players are sleeping in a given world, the night will be skipped and the time will be set to morning.

This should work independently for each world. (i.e. a sleep vote in one world will not change/affect the time in another)

## Commands
```
# Reloads the configuration file
# Permission: sleepvote.command.reload
/sleepvote reload
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

# Toggle on/off the logging of the above messages (wakeup, enter_bed, exit_bed) in the server console
"enable_logging" = true
```

## Build Instructions
Just run the following in a terminal:
```
gradlew build
```
The plugin jar file will then appear in /build/libs

## Links
[W.I.P. Plugins Sponge Forum Thread](https://forums.spongepowered.org/t/sleep-vote-v0-4-0/18289)

[Sponge Ore Repository](https://ore.spongepowered.org/Icohedron/Sleep-Vote)
