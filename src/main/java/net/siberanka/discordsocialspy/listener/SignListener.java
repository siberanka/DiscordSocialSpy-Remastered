package net.siberanka.discordsocialspy.listener;

import net.siberanka.discordsocialspy.DiscordSocialSpyPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.block.sign.SignSide;

import java.util.Map;
import java.util.UUID;

public class SignListener implements Listener {

    private final DiscordSocialSpyPlugin plugin;
    private final Map<UUID, Boolean> signNotify;

    public SignListener(DiscordSocialSpyPlugin plugin, Map<UUID, Boolean> signNotify) {
        this.plugin = plugin;
        this.signNotify = signNotify;
    }

    @EventHandler
    public void onSignChange(SignChangeEvent event) {

        if (!plugin.getConfig().getBoolean("log-signs", true))
            return;

        Player player = event.getPlayer();
        Location loc = event.getBlock().getLocation();
        String world = loc.getWorld() != null ? loc.getWorld().getName() : "world";

        Sign oldSign = (Sign) event.getBlock().getState();
        SignSide oldSide = oldSign.getSide(event.getSide());

        String[] oldLines = new String[4];
        String[] newLines = new String[4];
        boolean[] edited = new boolean[4];

        boolean allOldEmpty = true;
        boolean allNewEmpty = true;
        boolean anyChange = false;

        for (int i = 0; i < 4; i++) {
            Component oldComp = oldSide.line(i);
            oldLines[i] = oldComp == null ? "" : PlainTextComponentSerializer.plainText().serialize(oldComp);

            Component newComp = event.line(i);
            newLines[i] = newComp == null ? "" : PlainTextComponentSerializer.plainText().serialize(newComp);

            if (!oldLines[i].isEmpty())
                allOldEmpty = false;
            if (!newLines[i].isEmpty())
                allNewEmpty = false;

            if (!oldLines[i].equals(newLines[i])) {
                edited[i] = true;
                anyChange = true;
            }
        }

        boolean blocked = false;
        for (int i = 0; i < 4; i++) {
            if (plugin.isBlocked(newLines[i])) {
                blocked = true;
                break;
            }
        }

        if (blocked) {
            event.setCancelled(true);
            player.sendMessage(LegacyComponentSerializer.legacyAmpersand()
                    .deserialize(plugin.getLanguageManager().get("sign-blocked")));
        }

        if (allNewEmpty)
            return;

        if (!allOldEmpty && !anyChange)
            return;

        String locationString = world + " | " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();

        String headerKey = allOldEmpty ? "sign-placed" : "sign-edited";

        String title = plugin.getLanguageManager()
                .get(headerKey)
                .replace("{player}", player.getName())
                .replace("{location}", locationString);

        if (blocked) {
            title = plugin.getLanguageManager().get("prefix-blocked-sign") + title;
        }

        StringBuilder desc = new StringBuilder();

        desc.append("**").append(player.getName()).append("**\n\n");

        for (int i = 0; i < 4; i++) {

            String raw = newLines[i];

            desc.append("```");

            if (raw.isEmpty()) {
                desc.append(" "); // boş satır için tek boşluk
            } else {
                desc.append(raw);
            }

            desc.append("```");

            if (edited[i] && !allOldEmpty)
                desc.append(" ✍️");

            desc.append("\n");
        }

        long timestamp = System.currentTimeMillis();

        plugin.getDispatcher().queueEmbed(
                title,
                desc.toString(),
                locationString,
                true,
                timestamp);

        if (plugin.getConfig().getBoolean("log-signs-to-console")) {

            plugin.getLogger().info("[SIGN] " + title);

            for (int i = 0; i < 4; i++) {
                String raw = newLines[i];
                String line = raw + (edited[i] && !allOldEmpty ? "*" : "");
                plugin.getLogger().info(line.isEmpty() ? "(empty)" : line);
            }
        }

        boolean finalAllOldEmpty = allOldEmpty;

        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            for (Player p : plugin.getServer().getOnlinePlayers()) {

                if (!p.hasPermission("discordsocialspy.use"))
                    continue;

                if (!signNotify.getOrDefault(p.getUniqueId(), false))
                    continue;

                String headerMsg = plugin.getLanguageManager().get("sign-header-staff");
                String placedMsg = plugin.getLanguageManager().get(headerKey)
                        .replace("{player}", player.getName())
                        .replace("{location}", locationString);

                p.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(headerMsg));
                p.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(placedMsg));

                for (int i = 0; i < 4; i++) {
                    String raw = newLines[i];
                    String visible = raw.isEmpty() ? " " : raw;
                    String line = plugin.getLanguageManager().get("sign-line")
                            .replace("{line}", visible + (edited[i] && !finalAllOldEmpty ? "*" : ""));
                    p.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(line));
                }

                Component teleport = LegacyComponentSerializer.legacyAmpersand().deserialize(
                        plugin.getLanguageManager().get("sign-location-click")).clickEvent(
                                ClickEvent.runCommand("/tp " + p.getName() + " "
                                        + loc.getBlockX() + " "
                                        + loc.getBlockY() + " "
                                        + loc.getBlockZ()))
                        .hoverEvent(
                                HoverEvent.showText(Component.text(locationString)));

                p.sendMessage(teleport);
            }
        });
    }
}
