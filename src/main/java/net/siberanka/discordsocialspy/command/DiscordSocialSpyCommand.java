package net.siberanka.discordsocialspy.command;

import net.siberanka.discordsocialspy.DiscordSocialSpyPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.List;

public class DiscordSocialSpyCommand implements CommandExecutor {

    private final DiscordSocialSpyPlugin plugin;

    public DiscordSocialSpyCommand(DiscordSocialSpyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String lbl, String[] args) {

        if (args.length == 0) {
            sender.sendMessage("/discordsocialspy reload");
            sender.sendMessage("/discordsocialspy cmd add <command>");
            sender.sendMessage("/discordsocialspy cmd remove <command>");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("discordsocialspy.reload")) {
                sender.sendMessage("You do not have permission.");
                return true;
            }
            plugin.reload();
            sender.sendMessage("Configuration reloaded.");
            return true;
        }

        if (args[0].equalsIgnoreCase("cmd")) {

            if (!sender.hasPermission("discordsocialspy.cmd")) {
                sender.sendMessage("You do not have permission.");
                return true;
            }

            if (args.length < 3) {
                sender.sendMessage("/discordsocialspy cmd add <command>");
                sender.sendMessage("/discordsocialspy cmd remove <command>");
                return true;
            }

            String action = args[1];
            String targetCmd = args[2].toLowerCase();

            List<String> list = plugin.getConfig().getStringList("logged-commands");

            if (action.equalsIgnoreCase("add")) {
                if (list.contains(targetCmd)) {
                    sender.sendMessage("This command is already logged.");
                    return true;
                }

                list.add(targetCmd);
                plugin.getConfig().set("logged-commands", list);
                plugin.saveConfig();
                plugin.reload();
                sender.sendMessage("Command added to logging list: " + targetCmd);
                return true;
            }

            if (action.equalsIgnoreCase("remove")) {
                if (!list.contains(targetCmd)) {
                    sender.sendMessage("This command is not in logging list.");
                    return true;
                }

                list.remove(targetCmd);
                plugin.getConfig().set("logged-commands", list);
                plugin.saveConfig();
                plugin.reload();
                sender.sendMessage("Command removed from logging list: " + targetCmd);
                return true;
            }

            sender.sendMessage("/discordsocialspy cmd add <command>");
            sender.sendMessage("/discordsocialspy cmd remove <command>");
            return true;
        }

        sender.sendMessage("Invalid usage.");
        return true;
    }
}
