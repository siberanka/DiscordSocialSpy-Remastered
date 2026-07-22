package net.siberanka.discordsocialspy.worker;

public final class JsonPayloads {

    private JsonPayloads() {
    }

    public static String text(String text, String username, String avatarUrl, String roleId) {
        String content = roleId.isEmpty() ? text : "<@&" + roleId + "> " + text;
        StringBuilder json = new StringBuilder(256)
                .append('{')
                .append("\"content\":\"").append(escape(limit(content, 2_000))).append("\",")
                .append("\"username\":\"").append(escape(limit(username, 80))).append("\",")
                .append("\"avatar_url\":\"").append(escape(limit(avatarUrl, 2_000))).append("\",");
        appendAllowedMentions(json, roleId);
        return json.append('}').toString();
    }

    public static String embed(String title, String description, String footer, String username,
            String avatarUrl, String timestamp, String roleId) {
        StringBuilder json = new StringBuilder(512).append('{');
        if (!roleId.isEmpty()) {
            json.append("\"content\":\"<@&").append(roleId).append(">\",");
        }
        json.append("\"username\":\"").append(escape(limit(username, 80))).append("\",")
                .append("\"avatar_url\":\"").append(escape(limit(avatarUrl, 2_000))).append("\",")
                .append("\"embeds\":[{")
                .append("\"title\":\"").append(escape(limit(title, 256))).append("\",")
                .append("\"description\":\"").append(escape(limit(description, 4_096))).append("\",")
                .append("\"footer\":{\"text\":\"").append(escape(limit(footer, 2_048))).append("\"},")
                .append("\"timestamp\":\"").append(escape(timestamp)).append("\"")
                .append("}],");
        appendAllowedMentions(json, roleId);
        return json.append('}').toString();
    }

    static String escape(String input) {
        if (input == null) {
            return "";
        }
        StringBuilder output = new StringBuilder(input.length() + 16);
        for (int index = 0; index < input.length(); index++) {
            char character = input.charAt(index);
            switch (character) {
                case '\\': output.append("\\\\"); break;
                case '"': output.append("\\\""); break;
                case '\b': output.append("\\b"); break;
                case '\f': output.append("\\f"); break;
                case '\n': output.append("\\n"); break;
                case '\r': output.append("\\r"); break;
                case '\t': output.append("\\t"); break;
                default:
                    if (character < 0x20 || character == '\u2028' || character == '\u2029') {
                        output.append(String.format("\\u%04x", (int) character));
                    } else {
                        output.append(character);
                    }
            }
        }
        return output.toString();
    }

    static String limit(String input, int maximumCodePoints) {
        if (input == null) {
            return "";
        }
        int count = input.codePointCount(0, input.length());
        if (count <= maximumCodePoints) {
            return input;
        }
        int end = input.offsetByCodePoints(0, Math.max(0, maximumCodePoints - 1));
        return input.substring(0, end) + "…";
    }

    private static void appendAllowedMentions(StringBuilder json, String roleId) {
        if (roleId.isEmpty()) {
            json.append("\"allowed_mentions\":{\"parse\":[]}");
        } else {
            json.append("\"allowed_mentions\":{\"parse\":[],\"roles\":[\"")
                    .append(roleId).append("\"]}");
        }
    }
}
