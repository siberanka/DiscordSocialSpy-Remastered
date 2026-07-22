package net.siberanka.discordsocialspy.command;

import net.siberanka.discordsocialspy.DiscordSocialSpyPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.help.HelpTopic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@SuppressWarnings("deprecation")
public final class DiscordSocialSpyCommand implements CommandExecutor, TabCompleter {

    private static final Pattern COMMAND_NAME = Pattern.compile("[a-z0-9_:-]{1,64}");

    private final DiscordSocialSpyPlugin plugin;
    private volatile List<String> commandCache = Collections.emptyList();

    public DiscordSocialSpyCommand(DiscordSocialSpyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("discordsocialspy.use")) {
            send(sender, "no-permission");
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("discordsocialspy.reload")) {
                send(sender, "no-permission");
                return true;
            }
            send(sender, "loading");
            plugin.reloadAsync(sender, () -> {
                rebuildCommandCache();
                send(sender, "reload-success");
            }, ignored -> send(sender, "reload-failed"));
            return true;
        }

        if (args[0].equalsIgnoreCase("sign")) {
            toggleSignNotifications(sender, args);
            return true;
        }

        if (args[0].equalsIgnoreCase("cmd")) {
            updateCommand(sender, args);
            return true;
        }

        send(sender, "invalid-usage");
        return true;
    }

    private void toggleSignNotifications(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            send(sender, "console-player-only");
            return;
        }
        if (!sender.hasPermission("discordsocialspy.notify.sign")) {
            send(sender, "no-permission");
            return;
        }
        if (args.length != 2 || !args[1].equalsIgnoreCase("toggle")) {
            send(sender, "usage-sign");
            return;
        }
        Player player = (Player) sender;
        UUID playerId = player.getUniqueId();
        boolean current = plugin.signNotifications().getOrDefault(playerId, plugin.settings().defaultSignNotify());
        plugin.signNotifications().put(playerId, !current);
        send(sender, current ? "sign-notify-disabled" : "sign-notify-enabled");
    }

    private void updateCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("discordsocialspy.cmd")) {
            send(sender, "no-permission");
            return;
        }
        if (args.length != 3) {
            send(sender, "usage-add");
            send(sender, "usage-remove");
            return;
        }

        boolean add;
        if (args[1].equalsIgnoreCase("add")) {
            add = true;
        } else if (args[1].equalsIgnoreCase("remove")) {
            add = false;
        } else {
            send(sender, "invalid-usage");
            return;
        }

        String input = args[2].toLowerCase(Locale.ROOT).trim();
        if (input.startsWith("/")) {
            input = input.substring(1);
        }
        if (!COMMAND_NAME.matcher(input).matches()) {
            send(sender, "invalid-command-name");
            return;
        }

        boolean alreadyPresent = plugin.settings().loggedCommands().contains(input);
        if (add == alreadyPresent) {
            send(sender, add ? "already-logged" : "not-logged");
            return;
        }

        String normalized = input;
        send(sender, "loading");
        plugin.updateLoggedCommandAsync(sender, normalized, add, changed -> {
            if (changed) {
                send(sender, add ? "added" : "removed", normalized);
            } else {
                send(sender, add ? "already-logged" : "not-logged");
            }
        }, ignored -> send(sender, "reload-failed"));
    }

    public void rebuildCommandCache() {
        plugin.scheduler().runGlobal(() -> {
            Collection<HelpTopic> topics = plugin.getServer().getHelpMap().getHelpTopics();
            Set<String> discovered = new LinkedHashSet<>();
            for (HelpTopic topic : topics) {
                String name = topic.getName();
                if (name.startsWith("/")) {
                    name = name.substring(1);
                }
                name = name.toLowerCase(Locale.ROOT).trim();
                if (COMMAND_NAME.matcher(name).matches()) {
                    discovered.add(name);
                }
            }
            List<String> sorted = new ArrayList<>(discovered);
            Collections.sort(sorted);
            commandCache = Collections.unmodifiableList(sorted);
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("discordsocialspy.use")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return matching(Arrays.asList("reload", "cmd", "sign"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("sign")) {
            return matching(Collections.singletonList("toggle"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("cmd")) {
            return matching(Arrays.asList("add", "remove"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("cmd")) {
            if (args[1].equalsIgnoreCase("remove")) {
                return matching(new ArrayList<>(plugin.settings().loggedCommands()), args[2]);
            }
            if (args[1].equalsIgnoreCase("add")) {
                List<String> candidates = new ArrayList<>();
                for (String candidate : commandCache) {
                    if (!plugin.settings().loggedCommands().contains(candidate)) {
                        candidates.add(candidate);
                    }
                }
                return matching(candidates, args[2]);
            }
        }
        return Collections.emptyList();
    }

    private static List<String> matching(List<String> candidates, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.startsWith(normalized)) {
                result.add(candidate);
                if (result.size() == 100) {
                    break;
                }
            }
        }
        return result;
    }

    private void sendHelp(CommandSender sender) {
        send(sender, "help-header");
        send(sender, "help-reload");
        send(sender, "help-add");
        send(sender, "help-remove");
        send(sender, "help-sign");
    }

    private void send(CommandSender sender, String key) {
        plugin.send(sender, plugin.language().get(key));
    }

    private void send(CommandSender sender, String key, String command) {
        plugin.send(sender, plugin.language().get(key).replace("{cmd}", command));
    }
}
