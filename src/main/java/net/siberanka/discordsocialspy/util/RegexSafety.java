package net.siberanka.discordsocialspy.util;

/** Rejects constructs commonly responsible for catastrophic Java-regex backtracking. */
public final class RegexSafety {

    private RegexSafety() {
    }

    public static boolean isSafe(String expression) {
        if (expression == null || expression.isEmpty() || expression.length() > 512
                || expression.contains("(?<=") || expression.contains("(?<!")) {
            return false;
        }

        java.util.ArrayDeque<GroupState> groups = new java.util.ArrayDeque<>();
        boolean escaped = false;
        boolean characterClass = false;
        for (int index = 0; index < expression.length(); index++) {
            char current = expression.charAt(index);
            if (escaped) {
                if (!characterClass && current >= '1' && current <= '9') {
                    return false;
                }
                escaped = false;
                continue;
            }
            if (current == '\\') {
                escaped = true;
                continue;
            }
            if (current == '[') {
                characterClass = true;
                continue;
            }
            if (current == ']' && characterClass) {
                characterClass = false;
                continue;
            }
            if (characterClass) {
                continue;
            }
            if (current == '(') {
                groups.push(new GroupState());
            } else if (current == '|') {
                if (!groups.isEmpty()) groups.peek().riskyContent = true;
            } else if (current == '*' || current == '+') {
                if (!groups.isEmpty()) groups.peek().riskyContent = true;
            } else if (current == '{') {
                int end = expression.indexOf('}', index + 1);
                if (end > index && expression.substring(index + 1, end).matches("\\d*,")) {
                    if (!groups.isEmpty()) groups.peek().riskyContent = true;
                }
            } else if (current == ')' && !groups.isEmpty()) {
                GroupState completed = groups.pop();
                if (completed.riskyContent && followedByUnboundedQuantifier(expression, index + 1)) {
                    return false;
                }
                if (completed.riskyContent && !groups.isEmpty()) {
                    groups.peek().riskyContent = true;
                }
            }
        }
        return !escaped && !characterClass && groups.isEmpty();
    }

    private static boolean followedByUnboundedQuantifier(String expression, int index) {
        if (index >= expression.length()) return false;
        char next = expression.charAt(index);
        if (next == '*' || next == '+') return true;
        if (next != '{') return false;
        int end = expression.indexOf('}', index + 1);
        return end > index && expression.substring(index + 1, end).matches("\\d*,");
    }

    private static final class GroupState {
        private boolean riskyContent;
    }
}
