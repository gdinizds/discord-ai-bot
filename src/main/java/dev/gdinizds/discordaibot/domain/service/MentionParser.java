package dev.gdinizds.discordaibot.domain.service;

public final class MentionParser {

    private MentionParser() {}

    public static boolean mentions(String content, String botUserId) {
        if (content == null || botUserId == null) return false;
        return content.contains("<@" + botUserId + ">") || content.contains("<@!" + botUserId + ">");
    }

    public static String stripMention(String content, String botUserId) {
        if (content == null) return "";
        return content
                .replace("<@" + botUserId + ">", "")
                .replace("<@!" + botUserId + ">", "")
                .strip();
    }
}

