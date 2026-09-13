package net.siberanka.discordsocialspy.listener;

import net.siberanka.discordsocialspy.DiscordSocialSpyPlugin;
import net.siberanka.discordsocialspy.config.PluginSettings;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.inventory.meta.BookMeta;

import java.util.ArrayList;
import java.util.List;

/** Audits completed book edits while leaving ordinary book use untouched. */
@SuppressWarnings({"deprecation", "removal"})
public final class BookListener implements Listener {

    private static final int CHUNK_SIZE = 3_600;
    private static final int MAX_AUDIT_CHARACTERS = 12_000;
    private static final int MAX_CONSOLE_PAGES = 20;

    private final DiscordSocialSpyPlugin plugin;

    public BookListener(DiscordSocialSpyPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBookEdit(PlayerEditBookEvent event) {
        PluginSettings settings = plugin.settings();
        Player player = event.getPlayer();
        if ((!settings.checkBooks() && !settings.logBooks())
                || player.hasPermission(settings.excludePermission())) {
            return;
        }

        BookMeta book = event.getNewBookMeta();
        boolean blocked = settings.checkBooks() && containsBlockedContent(book, settings);
        if (blocked) {
            event.setCancelled(true);
            plugin.send(player, plugin.language().get("book-blocked"));
        }
        if (!settings.logBooks() && !blocked) {
            return;
        }

        String action = plugin.language().get(event.isSigning() ? "book-signed" : "book-edited");
        String title = action + " - " + player.getName();
        String footer = footer(player, event.getSlot(), book.getPageCount());
        List<String> chunks = render(book);
        String roleId = blocked ? settings.roleId() : "";
        if (blocked) {
            title = plugin.language().get("prefix-blocked-book") + title;
        }
        for (int index = 0; index < chunks.size(); index++) {
            String partTitle = chunks.size() == 1 ? title : title + " (" + (index + 1) + "/" + chunks.size() + ")";
            plugin.dispatcher().queueBookEmbed(partTitle, chunks.get(index), footer,
                    System.currentTimeMillis(), index == 0 ? roleId : "");
        }

        if (settings.logBooksToConsole()) {
            plugin.getLogger().info("[BOOK] " + sanitize(title) + " | " + sanitize(footer));
            int consolePages = Math.min(book.getPages().size(), MAX_CONSOLE_PAGES);
            for (int page = 0; page < consolePages; page++) {
                plugin.getLogger().info("[BOOK] Page " + (page + 1) + ": " + sanitize(book.getPages().get(page)));
            }
            if (book.getPages().size() > consolePages) {
                plugin.getLogger().info("[BOOK] " + (book.getPages().size() - consolePages)
                        + " additional page(s) omitted from the bounded console audit.");
            }
        }
    }

    private boolean containsBlockedContent(BookMeta book, PluginSettings settings) {
        if (settings.contentFilter().find(book.getTitle()) != null
                || settings.contentFilter().find(book.getAuthor()) != null) {
            return true;
        }
        for (String page : book.getPages()) {
            if (settings.contentFilter().find(page) != null) {
                return true;
            }
        }
        return false;
    }

    private static List<String> render(BookMeta book) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        if (book.hasTitle()) {
            current.append("**Title:** ").append(escape(book.getTitle())).append('\n');
        }
        if (book.hasAuthor()) {
            current.append("**Author:** ").append(escape(book.getAuthor())).append('\n');
        }
        if (current.length() > 0) {
            current.append('\n');
        }

        int captured = current.length();
        boolean truncated = false;
        List<String> pages = book.getPages();
        for (int page = 0; page < pages.size(); page++) {
            int remaining = MAX_AUDIT_CHARACTERS - captured;
            if (remaining <= 0) {
                truncated = true;
                break;
            }
            String pageText = escapeCodeBlock(pages.get(page));
            if (pageText.length() > remaining) {
                pageText = safePrefix(pageText, remaining);
                truncated = true;
            }
            captured += pageText.length();

            int part = 1;
            for (int offset = 0; offset < pageText.length();) {
                int end = Math.min(pageText.length(), offset + 3_200);
                if (end < pageText.length() && Character.isHighSurrogate(pageText.charAt(end - 1))) {
                    end--;
                }
                String suffix = pageText.length() > 3_200 ? " (part " + part++ + ")" : "";
                String block = "**Page " + (page + 1) + suffix + "**\n```\n"
                        + pageText.substring(offset, end) + "\n```\n";
                if (current.length() > 0 && current.length() + block.length() > CHUNK_SIZE) {
                    chunks.add(current.toString());
                    current.setLength(0);
                }
                current.append(block);
                offset = end;
            }
            if (truncated) {
                break;
            }
        }
        if (truncated) {
            String notice = "\n_Additional content omitted after the bounded audit limit._";
            if (current.length() + notice.length() > CHUNK_SIZE) {
                chunks.add(current.toString());
                current.setLength(0);
            }
            current.append(notice);
        }
        if (current.length() == 0 && chunks.isEmpty()) {
            current.append("_(empty book)_");
        }
        if (current.length() > 0) {
            chunks.add(current.toString());
        }
        return chunks;
    }

    private static String safePrefix(String value, int maximumChars) {
        int end = Math.min(value.length(), Math.max(0, maximumChars));
        if (end > 0 && end < value.length() && Character.isHighSurrogate(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }

    private static String footer(Player player, int slot, int pages) {
        Location location = player.getLocation();
        String world = location.getWorld() == null ? "unknown" : location.getWorld().getName();
        return player.getName() + " | " + world + " | " + location.getBlockX() + ", "
                + location.getBlockY() + ", " + location.getBlockZ() + " | slot " + slot + " | " + pages + " page(s)";
    }

    private static String escape(String input) {
        return input == null ? "" : input.replace("\\", "\\\\").replace("*", "\\*").replace("_", "\\_");
    }

    private static String escapeCodeBlock(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\r", " ").replace("```", "`\u200B``");
    }

    private static String sanitize(String input) {
        String safe = input == null ? "" : input.replaceAll("[\\r\\n\\p{Cntrl}]", " ");
        return safe.length() <= 512 ? safe : safe.substring(0, 512);
    }
}
