package dev.gdinizds.discordaibot.domain.service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SensitiveDataFilter {

    private static final Pattern CPF = Pattern.compile("(?<!\\d)\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}(?!\\d)");
    private static final Pattern CARD_CANDIDATE = Pattern.compile("(?<!\\d)(?:\\d[ -]?){12,18}\\d(?!\\d)");
    private static final List<Pattern> TOKENS = List.of(

            Pattern.compile("[A-Za-z\\d_-]{23,28}\\.[A-Za-z\\d_-]{6}\\.[A-Za-z\\d_-]{27,}"),

            Pattern.compile("eyJ[A-Za-z\\d_-]{10,}\\.eyJ[A-Za-z\\d_-]{10,}\\.[A-Za-z\\d_-]+"),

            Pattern.compile("AIza[\\w-]{35}"),
            Pattern.compile("sk-(?:ant-|proj-)?[A-Za-z\\d_-]{20,}"),
            Pattern.compile("gh[pousr]_[A-Za-z\\d]{36,}"),
            Pattern.compile("xox[abprs]-[A-Za-z\\d-]{10,}"),
            Pattern.compile("AKIA[\\dA-Z]{16}"),
            Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----"),
            Pattern.compile("(?i)(?:senha|password|passwd|api[_-]?key|secret)\\s*[:=]\\s*\\S{6,}"));

    private SensitiveDataFilter() {}

    public static boolean isSensitive(String content) {
        if (content == null || content.isBlank()) return false;
        if (CPF.matcher(content).find()) return true;
        if (containsCardNumber(content)) return true;
        return TOKENS.stream().anyMatch(p -> p.matcher(content).find());
    }

    private static boolean containsCardNumber(String content) {
        Matcher m = CARD_CANDIDATE.matcher(content);
        while (m.find()) {
            String digits = m.group().replaceAll("[ -]", "");
            if (digits.length() >= 13 && digits.length() <= 19 && passesLuhn(digits)) return true;
        }
        return false;
    }

    private static boolean passesLuhn(String digits) {
        int sum = 0;
        boolean doubleIt = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (doubleIt) {
                d *= 2;
                if (d > 9) d -= 9;
            }
            sum += d;
            doubleIt = !doubleIt;
        }
        return sum % 10 == 0;
    }
}

