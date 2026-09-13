package net.siberanka.discordsocialspy.util;

import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Checks the primary GitHub release feed, with the public GitLab mirror as fallback. */
@SuppressWarnings("deprecation")
public final class UpdateChecker {

    private static final String GITHUB_API =
            "https://api.github.com/repos/siberanka/DiscordSocialSpy-Remastered/releases/latest";
    private static final String GITLAB_API =
            "https://gitlab.com/api/v4/projects/siberanka%2FDiscordSocialSpy-Remastered/releases/permalink/latest";
    private static final Pattern TAG_NAME = Pattern.compile("\\\"tag_name\\\"\\s*:\\s*\\\"([^\\\"]{1,64})\\\"");

    private UpdateChecker() {
    }

    public static void check(Plugin plugin, boolean debug) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        String latest = requestTag(client, GITHUB_API, plugin);
        String source = "GitHub";
        if (latest == null) {
            latest = requestTag(client, GITLAB_API, plugin);
            source = "GitLab";
        }
        if (latest == null) {
            if (debug) {
                plugin.getLogger().info("Update check could not reach GitHub or the GitLab mirror.");
            }
            return;
        }

        String current = plugin.getDescription().getVersion();
        if (isNewer(latest, current)) {
            plugin.getLogger().warning("DiscordSocialSpy " + stripPrefix(latest) + " is available via " + source
                    + "; current version is " + current + ".");
        } else if (debug) {
            plugin.getLogger().info("DiscordSocialSpy is current (" + current + ", checked via " + source + ").");
        }
    }

    private static String requestTag(HttpClient client, String endpoint, Plugin plugin) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(7))
                    .header("Accept", "application/json")
                    .header("User-Agent", "DiscordSocialSpy/" + plugin.getDescription().getVersion())
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }
            Matcher tag = TAG_NAME.matcher(response.body());
            return tag.find() ? tag.group(1) : null;
        } catch (IOException failure) {
            return null;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return null;
        } catch (RuntimeException failure) {
            return null;
        }
    }

    static boolean isNewer(String candidate, String current) {
        int[] candidateParts = numericParts(candidate);
        int[] currentParts = numericParts(current);
        int length = Math.max(candidateParts.length, currentParts.length);
        for (int index = 0; index < length; index++) {
            int candidatePart = index < candidateParts.length ? candidateParts[index] : 0;
            int currentPart = index < currentParts.length ? currentParts[index] : 0;
            if (candidatePart != currentPart) {
                return candidatePart > currentPart;
            }
        }
        return false;
    }

    private static int[] numericParts(String version) {
        String clean = stripPrefix(version).split("[-+]", 2)[0];
        String[] pieces = clean.split("\\.");
        int[] result = new int[pieces.length];
        for (int index = 0; index < pieces.length; index++) {
            try {
                result[index] = Integer.parseInt(pieces[index].replaceAll("[^0-9]", ""));
            } catch (NumberFormatException ignored) {
                result[index] = 0;
            }
        }
        return result;
    }

    private static String stripPrefix(String version) {
        return version != null && (version.startsWith("v") || version.startsWith("V"))
                ? version.substring(1) : String.valueOf(version);
    }
}
