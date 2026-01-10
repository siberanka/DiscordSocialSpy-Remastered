package net.siberanka.discordsocialspy.command;

import net.siberanka.discordsocialspy.DiscordSocialSpyPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class DiscordSocialSpyCommand implements CommandExecutor {

    private final DiscordSocialSpyPlugin plugin;

    public DiscordSocialSpyCommand(DiscordSocialSpyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String lbl, String[] args) {
        if (args.length == 0) return false;
        if (!args[0].equalsIgnoreCase("reload")) return false;

        plugin.reload();
        sender.sendMessage("DiscordSocialSpy reloaded.");
        return true;
    }
}
