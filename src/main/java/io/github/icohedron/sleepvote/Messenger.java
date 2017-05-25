package io.github.icohedron.sleepvote;

import org.spongepowered.api.effect.sound.SoundType;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.world.World;

class Messenger {

    private boolean playSound;

    Messenger(boolean playSound) {
        this.playSound = playSound;
    }

    Text parseMessage(String message, int numSleeping, int requiredSleeping, String playerName, boolean prefix) {
        int percent = 0;
        if (requiredSleeping > 0) {
            percent = (int) (numSleeping * 100.0f / requiredSleeping);
        }

        String msg = message;
        msg = msg.replace("<sleeping>", Integer.toString(numSleeping));
        msg = msg.replace("<required>", Integer.toString(requiredSleeping));
        msg = msg.replace("<percent>", Integer.toString(percent));
        msg = msg.replace("<player>", playerName);
        Text text = Text.of(TextColors.YELLOW, msg);

        return prefix ? addPrefix(text) : text;
    }

    void sendWorldMessage(World world, Text message) {
        for (Player p : world.getPlayers()) {
            p.sendMessage(message);
        }
    }

    void playWorldSound(World world, SoundType sound) {
        if (!playSound) {
            return;
        }
        for (Player p : world.getPlayers()) {
            if (!p.hasPermission("sleepvote.mute")) {
                p.playSound(sound, p.getLocation().getPosition(), 1);
            }
        }
    }

    Text addPrefix(Text text) {
        Text prefix = Text.of(TextColors.GREEN, "[", TextColors.RED, "SleepVote", TextColors.GREEN, "] ");
        return Text.of(prefix, text);
    }

}
