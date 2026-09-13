package net.siberanka.discordsocialspy.listener;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.siberanka.discordsocialspy.DiscordSocialSpyPlugin;
import net.siberanka.discordsocialspy.config.PluginSettings;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SuppressWarnings("deprecation")
public final class SignListener implements Listener {

    private static final int SIGN_LINES = 4;
    private static final int MAX_CAPTURED_LINE = 384;

    private final DiscordSocialSpyPlugin plugin;

    public SignListener(DiscordSocialSpyPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        PluginSettings settings = plugin.settings();
        if ((!settings.checkSigns() && !settings.logSigns())
                || event.getPlayer().hasPermission(settings.excludePermission())) {
            return;
        }

        Player player = event.getPlayer();
        String[] newLines = readNewLines(event);
        String[] oldLines = readPreviousLines(event);
        boolean[] edited = new boolean[SIGN_LINES];
        boolean oldEmpty = allEmpty(oldLines);
        boolean newEmpty = allEmpty(newLines);
        boolean changed = false;
        for (int index = 0; index < SIGN_LINES; index++) {
            edited[index] = !oldLines[index].equals(newLines[index]);
            changed |= edited[index];
        }

        boolean blocked = settings.checkSigns()
                && settings.contentFilter().find(String.join("\n", newLines)) != null;
        if (blocked) {
            event.setCancelled(true);
            plugin.send(player, plugin.language().get("sign-blocked"));
            plugin.runBlockedContentCommand(player, "sign", settings);
        }
        if (newEmpty || (!oldEmpty && !changed)) {
            return;
        }
        if (!settings.logSigns() && !blocked) {
            return;
        }

        Location location = event.getBlock().getLocation();
        String world = location.getWorld() == null ? "unknown" : location.getWorld().getName();
        String locationText = world + " | " + location.getBlockX() + ", " + location.getBlockY() + ", "
                + location.getBlockZ();
        String headerKey = oldEmpty ? "sign-placed" : "sign-edited";
        String title = plugin.language().get(headerKey)
                .replace("{player}", player.getName())
                .replace("{location}", locationText);
        if (blocked) {
            title = plugin.language().get("prefix-blocked-sign") + title;
        }

        String description = buildDescription(player.getName(), newLines, edited, oldEmpty);
        String roleId = blocked ? settings.roleId() : "";
        plugin.dispatcher().queueEmbed(title, description, locationText, true, System.currentTimeMillis(), roleId);

        if (settings.logSignsToConsole()) {
            plugin.getLogger().info("[SIGN] " + sanitizeLog(title) + " at " + sanitizeLog(locationText));
            for (int index = 0; index < SIGN_LINES; index++) {
                String suffix = edited[index] && !oldEmpty ? " *" : "";
                plugin.getLogger().info("[SIGN] " + sanitizeLog(newLines[index]) + suffix);
            }
        }

        notifyStaff(player.getName(), headerKey, locationText, location, newLines, edited, oldEmpty);
    }

    private void notifyStaff(String actor, String headerKey, String locationText, Location location,
            String[] lines, boolean[] edited, boolean oldEmpty) {
        String[] safeLines = Arrays.copyOf(lines, lines.length);
        boolean[] editFlags = Arrays.copyOf(edited, edited.length);
        plugin.scheduler().runGlobal(() -> {
            List<Player> recipients = new ArrayList<>(Bukkit.getOnlinePlayers());
            for (Player recipient : recipients) {
                plugin.scheduler().runForPlayer(recipient, () -> {
                    if (!recipient.hasPermission("discordsocialspy.notify.sign")) {
                        return;
                    }
                    boolean enabled = plugin.signNotifications().getOrDefault(recipient.getUniqueId(),
                            plugin.settings().defaultSignNotify());
                    if (!enabled) {
                        return;
                    }
                    sendStaffNotice(recipient, actor, headerKey, locationText, location, safeLines, editFlags, oldEmpty);
                });
            }
        });
    }

    private void sendStaffNotice(Player recipient, String actor, String headerKey, String locationText,
            Location location, String[] lines, boolean[] edited, boolean oldEmpty) {
        recipient.sendMessage(color(plugin.language().get("sign-header-staff")));
        recipient.sendMessage(color(plugin.language().get(headerKey)
                .replace("{player}", actor)
                .replace("{location}", locationText)));
        for (int index = 0; index < SIGN_LINES; index++) {
            String visible = lines[index].isEmpty() ? " " : lines[index];
            String line = plugin.language().get("sign-line")
                    .replace("{line}", visible + (edited[index] && !oldEmpty ? " *" : ""));
            recipient.sendMessage(color(line));
        }

        String label = plugin.language().get("sign-location-click").replace("{location}", locationText);
        String teleport = "/tp " + location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ();
        try {
            TextComponent component = new TextComponent(TextComponent.fromLegacyText(color(label)));
            component.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, teleport));
            component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    TextComponent.fromLegacyText(color("&7" + locationText))));
            recipient.spigot().sendMessage(component);
        } catch (LinkageError | RuntimeException unsupported) {
            recipient.sendMessage(color(label + " &8(&f" + teleport + "&8)"));
        }
    }

    private String[] readNewLines(SignChangeEvent event) {
        String[] result = new String[SIGN_LINES];
        for (int index = 0; index < SIGN_LINES; index++) {
            result[index] = limit(event.getLine(index));
        }
        return result;
    }

    private String[] readPreviousLines(SignChangeEvent event) {
        String[] empty = {"", "", "", ""};
        if (!(event.getBlock().getState() instanceof Sign)) {
            return empty;
        }
        Sign sign = (Sign) event.getBlock().getState();

        // 1.20+ exposes front/back sides. Reflection keeps the class loadable on 1.16.
        try {
            Method eventSide = event.getClass().getMethod("getSide");
            Object side = eventSide.invoke(event);
            Method signSide = null;
            for (Method candidate : sign.getClass().getMethods()) {
                if (candidate.getName().equals("getSide") && candidate.getParameterCount() == 1) {
                    signSide = candidate;
                    break;
                }
            }
            if (signSide != null) {
                Object sideView = signSide.invoke(sign, side);
                Method getLine = sideView.getClass().getMethod("getLine", int.class);
                String[] result = new String[SIGN_LINES];
                for (int index = 0; index < SIGN_LINES; index++) {
                    result[index] = limit(String.valueOf(getLine.invoke(sideView, index)));
                }
                return result;
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Legacy fallback below.
        }

        String[] result = new String[SIGN_LINES];
        for (int index = 0; index < SIGN_LINES; index++) {
            result[index] = limit(sign.getLine(index));
        }
        return result;
    }

    private static String buildDescription(String playerName, String[] lines, boolean[] edited, boolean oldEmpty) {
        StringBuilder description = new StringBuilder(256).append("**")
                .append(escapeMarkdown(playerName)).append("**\n\n");
        for (int index = 0; index < SIGN_LINES; index++) {
            description.append("```").append(lines[index].isEmpty() ? " " : lines[index]).append("```");
            if (edited[index] && !oldEmpty) {
                description.append(" ✍️");
            }
            description.append('\n');
        }
        return description.toString();
    }

    private static String escapeMarkdown(String input) {
        return input.replace("\\", "\\\\").replace("*", "\\*").replace("_", "\\_");
    }

    private static boolean allEmpty(String[] lines) {
        for (String line : lines) {
            if (!line.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static String limit(String input) {
        if (input == null) {
            return "";
        }
        String normalized = input.replace('\r', ' ').replace('\n', ' ')
                .replace("```", "`\u200B``");
        return normalized.length() <= MAX_CAPTURED_LINE ? normalized : normalized.substring(0, MAX_CAPTURED_LINE);
    }

    private static String sanitizeLog(String input) {
        String safe = input.replaceAll("[\\r\\n\\p{Cntrl}]", " ");
        return safe.length() <= 512 ? safe : safe.substring(0, 512);
    }

    private static String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input);
    }
}
