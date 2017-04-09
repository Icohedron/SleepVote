package io.github.icohedron.sleepvote;

import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.world.World;

import java.util.Optional;

public class Messenger {

    public Text parseMessage(String message, Optional<Integer> numSleeping, Optional<Integer> requiredSleeping, Optional<String> playerName, boolean prefix) {
        int percent = -1;
        if (numSleeping.isPresent() && requiredSleeping.isPresent()) {
            percent = (int) (numSleeping.get() * 100.0f / requiredSleeping.get());
        }

        String msg = new String(message);

        if (numSleeping.isPresent()) {
            msg = msg.replace("<sleeping>", Integer.toString(numSleeping.get()));
        }

        if (requiredSleeping.isPresent()) {
            msg = msg.replace("<required>", Integer.toString(requiredSleeping.get()));
        }

        if (percent != -1) {
            msg = msg.replace("<percent>", Integer.toString(percent));
        }

        if (playerName.isPresent()) {
            msg = msg.replace("<player>", playerName.get());
        }

        Text text = Text.of(TextColors.YELLOW, msg);

        Text result = prefix ? addPrefix(text) : text;
        return result;
    }

    public void sendWorldMessage(World world, Text message) {
        for (Player p : world.getPlayers()) {
            p.sendMessage(message);
        }
    }

    public Text addPrefix(Text text) {
        Text prefix = Text.of(TextColors.GREEN, "[", TextColors.RED, "SleepVote", TextColors.GREEN, "] ");
        return Text.of(prefix, text);
    }

}
