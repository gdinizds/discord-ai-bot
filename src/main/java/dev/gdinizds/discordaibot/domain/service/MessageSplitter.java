package dev.gdinizds.discordaibot.domain.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class MessageSplitter {

    public static final String TRUNCATION_NOTICE = "(resposta truncada)";

    private static final String ZWSP = "​";
    private static final String FENCE = "```";
    private static final String FENCE_CLOSE = "\n" + FENCE;
    private static final Pattern EVERYONE_OR_HERE = Pattern.compile("@(everyone|here)");
    private static final Pattern ROLE_MENTION = Pattern.compile("<@&(\\d+)>");

    private final int limit;
    private final int maxChunks;
    private final String fallbackText;

    public MessageSplitter(int limit, int maxChunks, String fallbackText) {
        if (limit < 100) throw new IllegalArgumentException("limit too small: " + limit);
        if (maxChunks < 1) throw new IllegalArgumentException("maxChunks must be positive");
        this.limit = limit;
        this.maxChunks = maxChunks;
        this.fallbackText = fallbackText;
    }

    public List<String> split(String text) {
        String remaining = sanitize(text == null ? "" : text.strip());
        if (remaining.isBlank()) return List.of(fallbackText);

        List<String> chunks = new ArrayList<>();
        String reopen = null;
        while (!remaining.isEmpty()) {
            String body = reopen == null ? remaining : reopen + "\n" + remaining;
            if (body.codePointCount(0, body.length()) <= limit) {
                chunks.add(body);
                break;
            }
            if (chunks.size() == maxChunks - 1) {
                chunks.add(truncate(body));
                return chunks;
            }

            int window = body.offsetByCodePoints(0, limit - FENCE_CLOSE.length());
            Cut cut = findCut(body, window, reopen == null ? 0 : reopen.length() + 1);
            String head = stripTrailingBlankLines(body.substring(0, cut.end()));
            String openFence = openFenceAtEnd(head);
            if (openFence != null) {
                head = head + FENCE_CLOSE;
                reopen = openFence;
            } else {
                reopen = null;
            }
            chunks.add(head);
            remaining = body.substring(cut.resume());
        }
        return chunks;
    }

    public static String sanitize(String text) {
        String result = EVERYONE_OR_HERE.matcher(text).replaceAll("@" + ZWSP + "$1");
        return ROLE_MENTION.matcher(result).replaceAll("<@" + ZWSP + "&$1>");
    }

    private String truncate(String body) {
        String suffix = "\n" + TRUNCATION_NOTICE;
        int budget = limit - FENCE_CLOSE.length() - suffix.length();
        int window = body.offsetByCodePoints(0, Math.min(budget, body.codePointCount(0, body.length())));
        Cut cut = findCut(body, window, 0);
        String head = stripTrailingBlankLines(body.substring(0, cut.end()));
        if (openFenceAtEnd(head) != null) head = head + FENCE_CLOSE;
        return head + suffix;
    }

    private static Cut findCut(String body, int window, int floor) {
        String candidate = body.substring(0, window);

        int blank = candidate.lastIndexOf("\n\n");
        if (blank > floor) return new Cut(blank, skipNewlines(body, blank));

        int newline = candidate.lastIndexOf('\n');
        if (newline > floor) return new Cut(newline, newline + 1);

        int sentence = lastSentenceEnd(candidate);
        if (sentence > floor) return new Cut(sentence, sentence + 1);

        int space = candidate.lastIndexOf(' ');
        if (space > floor) return new Cut(space, space + 1);

        return new Cut(window, window);
    }

    private static int lastSentenceEnd(String candidate) {
        for (int i = candidate.length() - 2; i > 0; i--) {
            char c = candidate.charAt(i);
            if ((c == '.' || c == '!' || c == '?') && Character.isWhitespace(candidate.charAt(i + 1))) {
                return i + 1;
            }
        }
        return -1;
    }

    private static int skipNewlines(String body, int from) {
        int i = from;
        while (i < body.length() && body.charAt(i) == '\n') i++;
        return i;
    }

    private static String stripTrailingBlankLines(String s) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '\n') end--;
        return s.substring(0, end);
    }

    static String openFenceAtEnd(String text) {
        String opener = null;
        for (String line : text.split("\n", -1)) {
            String trimmed = line.strip();
            if (!trimmed.startsWith(FENCE)) continue;
            if (opener == null) {
                opener = FENCE + trimmed.substring(FENCE.length()).strip();
            } else if (trimmed.equals(FENCE)) {
                opener = null;
            }
        }
        return opener;
    }

    private record Cut(int end, int resume) {}
}

