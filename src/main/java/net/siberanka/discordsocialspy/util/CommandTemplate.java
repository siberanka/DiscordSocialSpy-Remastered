package net.siberanka.discordsocialspy.util;

/** Renders the bounded, administrator-controlled command used for blocked content. */
public final class CommandTemplate {

    private CommandTemplate() {
    }

    public static String render(String template, String playerName, String triggerLabel) {
        if (template == null || playerName == null || triggerLabel == null) {
            return "";
        }
        String command = template
                .replace("%player%", playerName)
                .replace("%trigger%", triggerLabel)
                .trim();
        while (command.startsWith("/")) {
            command = command.substring(1).trim();
        }
        return command;
    }
}
