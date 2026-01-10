package net.siberanka.discordsocialspy.command;

import net.siberanka.discordsocialspy.DiscordSocialSpyPlugin;
import net.siberanka.discordsocialspy.util.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.help.HelpTopic;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class DiscordSocialSpyCommand implements CommandExecutor, TabCompleter {

    private final DiscordSocialSpyPlugin plugin;
    private final LanguageManager lang;
    private volatile List<String> helpCache = null;
    private volatile boolean cacheBuilding = false;

    public DiscordSocialSpyCommand(DiscordSocialSpyPlugin plugin, LanguageManager lang) {
        this.plugin = plugin;
        this.lang = lang;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String lbl, String[] args) {

        if (!sender.hasPermission("discordsocialspy.use")) {
            sender.sendMessage(lang.get("no-permission"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(lang.get("help-header"));
            sender.sendMessage(lang.get("help-reload"));
            sender.sendMessage(lang.get("help-add"));
            sender.sendMessage(lang.get("help-remove"));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {

            if (!sender.hasPermission("discordsocialspy.reload")) {
                sender.sendMessage(lang.get("no-permission"));
                return true;
            }

            plugin.reload();
            clearCacheAsync();
            sender.sendMessage(lang.get("reload-success"));
            return true;
        }

        if (args[0].equalsIgnoreCase("cmd")) {

            if (!sender.hasPermission("discordsocialspy.cmd")) {
                sender.sendMessage(lang.get("no-permission"));
                return true;
            }

            if (args.length < 3) {
                sender.sendMessage(lang.get("usage-add"));
                sender.sendMessage(lang.get("usage-remove"));
                return true;
            }

            String action = args[1];
            String input = args[2].toLowerCase(Locale.ROOT).replace("/", "").trim();

            if (!input.matches("^[a-zA-Z0-9_-]{1,32}$")) {
                sender.sendMessage(lang.get("invalid-command-name"));
                return true;
            }

            List<String> list = plugin.getConfig().getStringList("logged-commands");
            List<String> normalized = new ArrayList<>();
            for (String s : list) normalized.add(s.trim());

            if (action.equalsIgnoreCase("add")) {

                if (normalized.contains(input)) {
                    sender.sendMessage(lang.get("already-logged"));
                    return true;
                }

                normalized.add(input);
                plugin.getConfig().set("logged-commands", normalized);
                plugin.saveConfig();
                plugin.reload();
                clearCacheAsync();

                sender.sendMessage(lang.get("added").replace("{cmd}", input));
                return true;
            }

            if (action.equalsIgnoreCase("remove")) {

                if (!normalized.contains(input)) {
                    sender.sendMessage(lang.get("not-logged"));
                    return true;
                }

                normalized.remove(input);
                plugin.getConfig().set("logged-commands", normalized);
                plugin.saveConfig();
                plugin.reload();
                clearCacheAsync();

                sender.sendMessage(lang.get("removed").replace("{cmd}", input));
                return true;
            }

            sender.sendMessage(lang.get("invalid-usage"));
            return true;
        }

        return true;
    }

    private void clearCacheAsync() {
        helpCache = null;
        cacheBuilding = false;
        rebuildCacheAsync();
    }

    private void rebuildCacheAsync() {

        if (cacheBuilding) return;
        cacheBuilding = true;

        CompletableFuture.supplyAsync(() -> {

            List<String> result = new ArrayList<>();
            Collection<HelpTopic> topics = plugin.getServer().getHelpMap().getHelpTopics();

            for (HelpTopic topic : topics) {
                String name = topic.getName().replace("/", "").trim().toLowerCase(Locale.ROOT);
                if (!name.matches("^[a-zA-Z0-9_-]{1,32}$")) continue;
                result.add(name);
            }

            return result;

        }).thenAcceptAsync(list -> {

            helpCache = list;
            cacheBuilding = false;

        }, Bukkit.getScheduler().getMainThreadExecutor(plugin));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {

        if (!(sender instanceof Player)) return Collections.emptyList();
        if (!sender.hasPermission("discordsocialspy.use")) return Collections.emptyList();

        if (helpCache == null && !cacheBuilding) rebuildCacheAsync();

        if (args.length == 1) return Arrays.asList("reload", "cmd");

        if (args.length == 2 && args[0].equalsIgnoreCase("cmd"))
            return Arrays.asList("add", "remove");

        if (args.length == 3 && args[0].equalsIgnoreCase("cmd")) {

            String mode = args[1].toLowerCase(Locale.ROOT);
            String start = args[2].toLowerCase(Locale.ROOT);

            List<String> logged = plugin.getConfig().getStringList("logged-commands");
            List<String> normalized = new ArrayList<>();
            for (String s : logged) normalized.add(s.trim());

            if (mode.equals("remove")) {
                List<String> result = new ArrayList<>();
                for (String name : normalized)
                    if (name.startsWith(start)) result.add(name);
                return result;
            }

            if (mode.equals("add")) {

                if (helpCache == null) return Collections.singletonList(lang.get("loading"));

                List<String> result = new ArrayList<>();
                for (String name : helpCache)
                    if (!normalized.contains(name) && name.startsWith(start)) result.add(name);

                return result;
            }
        }

        return Collections.emptyList();
    }
}
