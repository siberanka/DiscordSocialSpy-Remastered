package net.siberanka.discordsocialspy.command;

import net.kyori.adventure.text.minimessage.MiniMessage;
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
    private final Map<UUID, Boolean> signNotify;
    private volatile List<String> helpCache = null;
    private volatile boolean cacheBuilding = false;

    public DiscordSocialSpyCommand(DiscordSocialSpyPlugin plugin, LanguageManager lang, Map<UUID, Boolean> signNotify) {
        this.plugin = plugin;
        this.lang = lang;
        this.signNotify = signNotify;
    }

    private void send(CommandSender sender, String key) {
        String msg = plugin.getConfig().getString("message-prefix") + lang.get(key);
        sender.sendMessage(MiniMessage.miniMessage().deserialize(msg));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String lbl, String[] args) {

        if (!sender.hasPermission("discordsocialspy.use")) {
            send(sender, "no-permission");
            return true;
        }

        if (args.length == 0) {
            send(sender, "help-header");
            send(sender, "help-reload");
            send(sender, "help-add");
            send(sender, "help-remove");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {

            if (!sender.hasPermission("discordsocialspy.reload")) {
                send(sender, "no-permission");
                return true;
            }

            plugin.reloadAll();
            clearCacheAsync();
            send(sender, "reload-success");
            return true;
        }

        if (args[0].equalsIgnoreCase("sign")) {

            if (!(sender instanceof Player)) {
                sender.sendMessage("Console cannot toggle sign notifications.");
                return true;
            }

            Player p = (Player) sender;

            if (args.length > 1 && args[1].equalsIgnoreCase("toggle")) {
                UUID id = p.getUniqueId();
                boolean current = signNotify.getOrDefault(id, plugin.getConfig().getBoolean("sign-notify"));
                boolean newState = !current;

                signNotify.put(id, newState);

                if (newState) {
                    send(sender, "sign-notify-enabled");
                } else {
                    send(sender, "sign-notify-disabled");
                }

                return true;
            }

            sender.sendMessage("Usage: /discordsocialspy sign toggle");
            return true;
        }

        if (args[0].equalsIgnoreCase("cmd")) {

            if (!sender.hasPermission("discordsocialspy.cmd")) {
                send(sender, "no-permission");
                return true;
            }

            if (args.length < 3) {
                send(sender, "usage-add");
                send(sender, "usage-remove");
                return true;
            }

            String action = args[1];
            String input = args[2].toLowerCase(Locale.ROOT).replace("/", "").trim();

            if (!input.matches("^[a-zA-Z0-9_-]{1,32}$")) {
                send(sender, "invalid-command-name");
                return true;
            }

            List<String> list = plugin.getConfig().getStringList("logged-commands");
            List<String> normalized = new ArrayList<>();
            for (String s : list)
                normalized.add(s.trim());

            if (action.equalsIgnoreCase("add")) {

                if (normalized.contains(input)) {
                    send(sender, "already-logged");
                    return true;
                }

                normalized.add(input);
                plugin.getConfig().set("logged-commands", normalized);
                plugin.saveConfig();
                plugin.reloadAll();
                clearCacheAsync();

                String msg = plugin.getConfig().getString("message-prefix") +
                        lang.get("added").replace("{cmd}", input);

                sender.sendMessage(MiniMessage.miniMessage().deserialize(msg));
                return true;
            }

            if (action.equalsIgnoreCase("remove")) {

                if (!normalized.contains(input)) {
                    send(sender, "not-logged");
                    return true;
                }

                normalized.remove(input);
                plugin.getConfig().set("logged-commands", normalized);
                plugin.saveConfig();
                plugin.reloadAll();
                clearCacheAsync();

                String msg = plugin.getConfig().getString("message-prefix") +
                        lang.get("removed").replace("{cmd}", input);

                sender.sendMessage(MiniMessage.miniMessage().deserialize(msg));
                return true;
            }

            send(sender, "invalid-usage");
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

        if (cacheBuilding)
            return;
        cacheBuilding = true;

        CompletableFuture.supplyAsync(() -> {

            List<String> result = new ArrayList<>();
            Collection<HelpTopic> topics = plugin.getServer().getHelpMap().getHelpTopics();

            for (HelpTopic topic : topics) {
                String name = topic.getName().replace("/", "").trim().toLowerCase(Locale.ROOT);
                if (!name.matches("^[a-zA-Z0-9_-]{1,32}$"))
                    continue;
                result.add(name);
            }

            return result;

        }).thenAccept(list -> {
            Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                helpCache = list;
                cacheBuilding = false;
            });
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {

        if (!(sender instanceof Player))
            return Collections.emptyList();
        if (!sender.hasPermission("discordsocialspy.use"))
            return Collections.emptyList();

        if (helpCache == null && !cacheBuilding)
            rebuildCacheAsync();

        if (args.length == 1)
            return Arrays.asList("reload", "cmd", "sign");

        if (args.length == 2 && args[0].equalsIgnoreCase("sign"))
            return Collections.singletonList("toggle");

        if (args.length == 2 && args[0].equalsIgnoreCase("cmd"))
            return Arrays.asList("add", "remove");

        if (args.length == 3 && args[0].equalsIgnoreCase("cmd")) {

            String mode = args[1].toLowerCase(Locale.ROOT);
            String start = args[2].toLowerCase(Locale.ROOT);

            List<String> logged = plugin.getConfig().getStringList("logged-commands");
            List<String> normalized = new ArrayList<>();
            for (String s : logged)
                normalized.add(s.trim());

            if (mode.equals("remove")) {
                List<String> result = new ArrayList<>();
                for (String name : normalized)
                    if (name.startsWith(start))
                        result.add(name);
                return result;
            }

            if (mode.equals("add")) {

                if (helpCache == null)
                    return Collections.singletonList(lang.get("loading"));

                List<String> result = new ArrayList<>();
                for (String name : helpCache)
                    if (!normalized.contains(name) && name.startsWith(start))
                        result.add(name);

                return result;
            }
        }

        return Collections.emptyList();
    }
}
