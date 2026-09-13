package net.siberanka.discordsocialspy.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reconciles user YAML files with their bundled schemas. Files are only rewritten
 * after a semantic change, and every correction to an existing file is backed up.
 */
public final class YamlConfigManager {

    private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private static final Pattern SAFE_LANGUAGE = Pattern.compile("[A-Za-z0-9_-]{2,16}");
    private static final Pattern COMMAND_NAME = Pattern.compile("[a-z0-9_:-]{1,64}");
    private static final Pattern ROLE_ID = Pattern.compile("[0-9]{17,20}");
    private static final Set<String> SUPERSEDED_FILTER_WORDS = Set.of(
            "oyna.", "play.", "mc.", "craft.", ":255");

    private final Plugin plugin;
    private final Path dataDirectory;

    public YamlConfigManager(Plugin plugin) {
        this.plugin = plugin;
        this.dataDirectory = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
    }

    public void updateAll() throws IOException {
        Files.createDirectories(dataDirectory);
        synchronize("config.yml", dataDirectory.resolve("config.yml"), true);
        synchronize("lang/en.yml", dataDirectory.resolve("lang/en.yml"), false);
        synchronize("lang/tr.yml", dataDirectory.resolve("lang/tr.yml"), false);

        YamlConfiguration config = loadConfig();
        String selected = config.getString("language", "en");
        if (selected != null && SAFE_LANGUAGE.matcher(selected).matches()
                && !"en".equalsIgnoreCase(selected) && !"tr".equalsIgnoreCase(selected)) {
            Path custom = dataDirectory.resolve("lang").resolve(selected + ".yml").normalize();
            if (custom.startsWith(dataDirectory) && Files.isRegularFile(custom)) {
                synchronize("lang/en.yml", custom, false);
            }
        }
    }

    public YamlConfiguration loadConfig() throws IOException {
        return load(dataDirectory.resolve("config.yml"));
    }

    public YamlConfiguration loadLanguage(String code) throws IOException {
        String safeCode = code != null && SAFE_LANGUAGE.matcher(code).matches() ? code : "en";
        Path requested = dataDirectory.resolve("lang").resolve(safeCode + ".yml").normalize();
        if (!requested.startsWith(dataDirectory) || !Files.isRegularFile(requested)) {
            requested = dataDirectory.resolve("lang/en.yml");
        }
        return load(requested);
    }

    public synchronized void setLoggedCommands(Set<String> commands) throws IOException {
        Path target = dataDirectory.resolve("config.yml");
        YamlConfiguration config = load(target);
        config.set("logged-commands", new ArrayList<>(commands));
        atomicWrite(target, config.saveToString());
    }

    private void synchronize(String resourceName, Path target, boolean configFile) throws IOException {
        String defaultsText = readResource(resourceName);
        YamlConfiguration defaults = parse(defaultsText, "bundled " + resourceName);

        Files.createDirectories(target.getParent());
        if (!Files.exists(target)) {
            atomicWrite(target, defaultsText);
            plugin.getLogger().info("Created " + display(target) + " from bundled defaults.");
            return;
        }

        YamlConfiguration current;
        try {
            current = load(target);
        } catch (IOException malformed) {
            Path backup = backup(target);
            atomicWrite(target, defaultsText);
            plugin.getLogger().warning("Replaced malformed " + display(target) + "; backup: " + display(backup));
            return;
        }

        List<String> changes = new ArrayList<>();
        reconcile(defaults, current, changes);
        if (configFile) {
            validateConfig(current, defaults, changes);
        } else {
            validateLanguage(current, defaults, changes);
        }

        if (changes.isEmpty()) {
            return;
        }

        Path backup = backup(target);
        atomicWrite(target, current.saveToString());
        plugin.getLogger().warning("Repaired " + display(target) + " (" + changes.size()
                + " change(s)); backup: " + display(backup));
    }

    private void reconcile(YamlConfiguration defaults, YamlConfiguration current, List<String> changes) {
        List<String> existing = new ArrayList<>(current.getKeys(true));
        existing.sort(Comparator.comparingInt(YamlConfigManager::depth).reversed());
        for (String key : existing) {
            if (!defaults.contains(key)) {
                current.set(key, null);
                changes.add("removed unknown key " + key);
            }
        }

        List<String> expected = new ArrayList<>(defaults.getKeys(true));
        expected.sort(Comparator.comparingInt(YamlConfigManager::depth));
        for (String key : expected) {
            Object defaultValue = defaults.get(key);
            if (defaultValue instanceof ConfigurationSection) {
                if (!current.isConfigurationSection(key)) {
                    current.set(key, null);
                    current.createSection(key);
                    changes.add("restored section " + key);
                }
                continue;
            }

            if (!current.contains(key)) {
                current.set(key, copyValue(defaultValue));
                changes.add("added missing key " + key);
            } else if (!compatible(defaultValue, current.get(key))) {
                current.set(key, copyValue(defaultValue));
                changes.add("reset invalid value " + key);
            }
        }
    }

    private void validateConfig(YamlConfiguration current, YamlConfiguration defaults, List<String> changes) {
        normalizeString(current, defaults, "language", 2, 16, changes);
        String language = current.getString("language", "en");
        if (language == null || !SAFE_LANGUAGE.matcher(language).matches()) {
            reset(current, defaults, "language", changes);
        }

        validateWebhook(current, defaults, "webhook", true, changes);
        validateWebhook(current, defaults, "sign-webhook", false, changes);
        validateWebhook(current, defaults, "book-webhook", false, changes);
        validateHttpsUrl(current, defaults, "avatar_url", true, changes);
        normalizeString(current, defaults, "username", 1, 80, changes);
        normalizeString(current, defaults, "prefix", 0, 128, changes);
        normalizeString(current, defaults, "message-prefix", 0, 128, changes);
        normalizeString(current, defaults, "exclude-permission", 1, 128, changes);
        normalizeCommandTemplate(current, defaults, "filter.command-action.command", changes);
        normalizeTriggerLabel(current, defaults, "filter.command-action.trigger-labels.chat", changes);
        normalizeTriggerLabel(current, defaults, "filter.command-action.trigger-labels.command", changes);
        normalizeTriggerLabel(current, defaults, "filter.command-action.trigger-labels.sign", changes);
        normalizeTriggerLabel(current, defaults, "filter.command-action.trigger-labels.book", changes);

        List<String> commands = normalizedList(current.getList("logged-commands"), 64, COMMAND_NAME, true);
        setIfDifferent(current, "logged-commands", commands, changes);
        List<String> words = normalizedList(current.getList("filter.words"), 256, null, false);
        if (words.removeIf(SUPERSEDED_FILTER_WORDS::contains)) {
            changes.add("removed superseded broad filter.words entries");
        }
        appendMissing(words, normalizedList(defaults.getList("filter.words"), 256, null, false));
        if (words.size() > 256) {
            words = new ArrayList<>(words.subList(0, 256));
        }
        setIfDifferent(current, "filter.words", words, changes);
        normalizeList(current, "filter.whitelisted-words", 256, null, false, changes);

        List<String> regexes = normalizedList(current.getList("filter.regex"), 512, null, false);
        appendMissing(regexes, normalizedList(defaults.getList("filter.regex"), 512, null, false));
        regexes.removeIf(expression -> {
            try {
                if (!net.siberanka.discordsocialspy.util.RegexSafety.isSafe(expression)) {
                    return true;
                }
                Pattern.compile(expression);
                return false;
            } catch (RuntimeException ignored) {
                return true;
            }
        });
        if (regexes.size() > 128) {
            regexes = new ArrayList<>(regexes.subList(0, 128));
        }
        setIfDifferent(current, "filter.regex", regexes, changes);

        String role = current.getString("filter.role-uuid", "").trim();
        if (!role.isEmpty() && !ROLE_ID.matcher(role).matches()) {
            current.set("filter.role-uuid", "");
            changes.add("cleared invalid filter.role-uuid");
        } else if (!role.equals(current.getString("filter.role-uuid", ""))) {
            current.set("filter.role-uuid", role);
            changes.add("normalized filter.role-uuid");
        }

        boundedInt(current, defaults, "async.sender_threads", 1, 4, true, changes);
        boundedInt(current, defaults, "async.queue_size", 32, 10_000, false, changes);
        boundedInt(current, defaults, "async.max_retries", 0, 10, false, changes);
        boundedInt(current, defaults, "async.retry_interval", 1, 60, false, changes);
        boundedInt(current, defaults, "async.rate_limit_wait", 1, 120, false, changes);
        boundedInt(current, defaults, "async.request_timeout", 2, 60, false, changes);
        boundedInt(current, defaults, "async.shutdown_timeout", 1, 30, false, changes);
        boundedInt(current, defaults, "rate-limit.capacity", 1, 100, false, changes);

        double refill = current.getDouble("rate-limit.refill-per-second", -1.0);
        if (!Double.isFinite(refill) || refill < 0.1 || refill > 100.0) {
            reset(current, defaults, "rate-limit.refill-per-second", changes);
        }
    }

    private void validateLanguage(YamlConfiguration current, YamlConfiguration defaults, List<String> changes) {
        for (String key : defaults.getKeys(false)) {
            normalizeString(current, defaults, key, 0, 2_000, changes);
        }
    }

    private void normalizeList(YamlConfiguration current, String path, int maxLength, Pattern allowed,
            boolean lowerCase, List<String> changes) {
        List<String> normalized = normalizedList(current.getList(path), maxLength, allowed, lowerCase);
        if (normalized.size() > 256) {
            normalized = new ArrayList<>(normalized.subList(0, 256));
        }
        setIfDifferent(current, path, normalized, changes);
    }

    private List<String> normalizedList(List<?> input, int maxLength, Pattern allowed, boolean lowerCase) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (input != null) {
            for (Object value : input) {
                if (!(value instanceof String)) {
                    continue;
                }
                String normalized = ((String) value).trim();
                if (lowerCase) {
                    normalized = normalized.toLowerCase(Locale.ROOT);
                }
                if (normalized.isEmpty() || normalized.length() > maxLength) {
                    continue;
                }
                if (allowed == null || allowed.matcher(normalized).matches()) {
                    result.add(normalized);
                }
            }
        }
        return new ArrayList<>(result);
    }

    private void setIfDifferent(YamlConfiguration current, String path, List<String> value, List<String> changes) {
        if (!value.equals(current.getStringList(path))) {
            current.set(path, value);
            changes.add("normalized " + path);
        }
    }

    private static void appendMissing(List<String> target, List<String> requiredDefaults) {
        LinkedHashSet<String> merged = new LinkedHashSet<>(target);
        merged.addAll(requiredDefaults);
        target.clear();
        target.addAll(merged);
    }

    private void normalizeString(YamlConfiguration current, YamlConfiguration defaults, String path,
            int minimum, int maximum, List<String> changes) {
        String value = current.getString(path);
        if (value == null || value.length() < minimum || value.length() > maximum) {
            reset(current, defaults, path, changes);
        }
    }

    private void normalizeCommandTemplate(YamlConfiguration current, YamlConfiguration defaults,
            String path, List<String> changes) {
        String value = current.getString(path);
        if (value == null || value.isBlank() || value.length() > 512 || containsControlCharacter(value)) {
            reset(current, defaults, path, changes);
            return;
        }
        String normalized = value.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1).trim();
        }
        if (normalized.isEmpty()) {
            reset(current, defaults, path, changes);
        } else if (!normalized.equals(value)) {
            current.set(path, normalized);
            changes.add("normalized " + path);
        }
    }

    private void normalizeTriggerLabel(YamlConfiguration current, YamlConfiguration defaults,
            String path, List<String> changes) {
        String value = current.getString(path);
        if (value == null || value.isBlank() || value.length() > 32 || containsControlCharacter(value)) {
            reset(current, defaults, path, changes);
            return;
        }
        String normalized = value.trim();
        if (!normalized.equals(value)) {
            current.set(path, normalized);
            changes.add("normalized " + path);
        }
    }

    private static boolean containsControlCharacter(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                return true;
            }
        }
        return false;
    }

    private void validateWebhook(YamlConfiguration current, YamlConfiguration defaults, String path,
            boolean placeholderAllowed, List<String> changes) {
        String value = current.getString(path, "").trim();
        boolean placeholder = placeholderAllowed && "PUT_YOUR_DISCORD_WEBHOOK_URL_HERE".equals(value);
        if (!value.isEmpty() && !placeholder && !isDiscordWebhook(value)) {
            reset(current, defaults, path, changes);
        } else if (!value.equals(current.getString(path, ""))) {
            current.set(path, value);
            changes.add("normalized " + path);
        }
    }

    private void validateHttpsUrl(YamlConfiguration current, YamlConfiguration defaults, String path,
            boolean allowEmpty, List<String> changes) {
        String value = current.getString(path, "").trim();
        if ((!allowEmpty || !value.isEmpty()) && !isHttps(value)) {
            reset(current, defaults, path, changes);
        }
    }

    private static boolean isDiscordWebhook(String value) {
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme()) && host != null
                    && (host.equals("discord.com") || host.endsWith(".discord.com")
                    || host.equals("discordapp.com") || host.endsWith(".discordapp.com"))
                    && uri.getPath() != null && uri.getPath().startsWith("/api/webhooks/");
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean isHttps(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void boundedInt(YamlConfiguration current, YamlConfiguration defaults, String path,
            int minimum, int maximum, boolean allowMinusOne, List<String> changes) {
        int value = current.getInt(path, Integer.MIN_VALUE);
        if ((allowMinusOne && value == -1) || (value >= minimum && value <= maximum)) {
            return;
        }
        reset(current, defaults, path, changes);
    }

    private void reset(YamlConfiguration current, YamlConfiguration defaults, String path, List<String> changes) {
        current.set(path, copyValue(defaults.get(path)));
        changes.add("reset invalid value " + path);
    }

    private static boolean compatible(Object expected, Object actual) {
        if (expected == null || actual == null) {
            return expected == actual;
        }
        if (expected instanceof Number) {
            return actual instanceof Number;
        }
        if (expected instanceof List<?>) {
            if (!(actual instanceof List<?>)) {
                return false;
            }
            List<?> expectedList = (List<?>) expected;
            if (expectedList.isEmpty()) {
                return true;
            }
            Object sample = expectedList.get(0);
            for (Object item : (List<?>) actual) {
                if (!compatible(sample, item)) {
                    return false;
                }
            }
            return true;
        }
        return expected.getClass().isInstance(actual);
    }

    private static Object copyValue(Object value) {
        return value instanceof List<?> ? new ArrayList<>((List<?>) value) : value;
    }

    private String readResource(String name) throws IOException {
        try (InputStream input = plugin.getResource(name)) {
            if (input == null) {
                throw new IOException("Missing bundled resource: " + name);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private YamlConfiguration load(Path path) throws IOException {
        if (Files.size(path) > 1_048_576L) {
            throw new IOException("YAML file exceeds the 1 MiB safety limit: " + display(path));
        }
        String text = Files.readString(path, StandardCharsets.UTF_8);
        return parse(text, display(path));
    }

    private static YamlConfiguration parse(String text, String source) throws IOException {
        if (text.contains("!!") || text.contains("!<") || Pattern.compile("(?m)^\\s*==\\s*:").matcher(text).find()) {
            throw new IOException("Unsafe YAML type tag in " + source);
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(text);
            return yaml;
        } catch (InvalidConfigurationException malformed) {
            throw new IOException("Invalid YAML in " + source, malformed);
        }
    }

    private Path backup(Path target) throws IOException {
        Path backupDirectory = dataDirectory.resolve("backups");
        Files.createDirectories(backupDirectory);
        String relative = dataDirectory.relativize(target).toString().replace(File.separatorChar, '-');
        Path backup = backupDirectory.resolve(relative + "." + BACKUP_TIME.format(LocalDateTime.now()) + ".bak");
        Files.copy(target, backup, StandardCopyOption.COPY_ATTRIBUTES);
        return backup;
    }

    private static void atomicWrite(Path target, String contents) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temporary, contents, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private String display(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        return normalized.startsWith(dataDirectory) ? dataDirectory.relativize(normalized).toString() : normalized.toString();
    }

    private static int depth(String key) {
        int depth = 0;
        for (int index = 0; index < key.length(); index++) {
            if (key.charAt(index) == '.') {
                depth++;
            }
        }
        return depth;
    }
}
