package dev.gdinizds.discordaibot.domain.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MessageSplitterTest {

    private static final String FALLBACK = "Não consegui responder agora.";
    private final MessageSplitter splitter = new MessageSplitter(1900, 5, FALLBACK);

    @Test
    void shortTextIsASingleChunk() {
        assertThat(splitter.split("  Olá, mundo!  ")).containsExactly("Olá, mundo!");
    }

    @Test
    void emptyAnswerBecomesFallback() {
        assertThat(splitter.split("   ")).containsExactly(FALLBACK);
        assertThat(splitter.split(null)).containsExactly(FALLBACK);
    }

    @Test
    void everyChunkRespectsTheLimitPlusFence() {
        String text = "palavra ".repeat(1200);
        List<String> chunks = splitter.split(text);
        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(c -> assertThat(c.codePointCount(0, c.length())).isLessThanOrEqualTo(1900));
    }

    @Test
    void cutsAtParagraphBeforeLine() {
        String first = "a".repeat(1000);
        String second = "b".repeat(500) + "\n" + "c".repeat(500);
        List<String> chunks = splitter.split(first + "\n\n" + second);
        assertThat(chunks).containsExactly(first, second);
    }

    @Test
    void cutsAtLineWhenThereIsNoParagraph() {
        String first = "a".repeat(1500);
        String second = "b".repeat(1000);
        assertThat(splitter.split(first + "\n" + second)).containsExactly(first, second);
    }

    @Test
    void cutsAtSentenceEndThenSpace() {
        String first = "x".repeat(1000) + ".";
        String rest = " " + "y".repeat(1000);
        assertThat(splitter.split(first + rest)).containsExactly(first, "y".repeat(1000));
    }

    @Test
    void hardCutWhenThereIsNoSeparator() {
        List<String> chunks = splitter.split("z".repeat(3000));
        assertThat(chunks).hasSize(2);
        assertThat(String.join("", chunks)).isEqualTo("z".repeat(3000));
    }

    @Test
    void codeBlockIsClosedAndReopenedWithItsLanguage() {
        StringBuilder code = new StringBuilder("Veja:\n```java\n");
        for (int i = 0; i < 150; i++) code.append("System.out.println(").append(i).append(");\n");
        code.append("```\nFim.");

        List<String> chunks = splitter.split(code.toString());

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks.getFirst()).endsWith("\n```");
        assertThat(chunks.get(1)).startsWith("```java\n");
        assertThat(chunks.getLast()).endsWith("```\nFim.");
        for (String chunk : chunks) {
            assertThat(MessageSplitter.openFenceAtEnd(chunk)).as("fences balanced in: %s", chunk).isNull();
            assertThat(chunk.codePointCount(0, chunk.length())).isLessThanOrEqualTo(1900);
        }
    }

    @Test
    void neverSplitsASurrogatePair() {
        String emoji = "😀";
        List<String> chunks = splitter.split(emoji.repeat(2500));
        for (String chunk : chunks) {
            assertThat(Character.isLowSurrogate(chunk.charAt(0))).isFalse();
            assertThat(Character.isHighSurrogate(chunk.charAt(chunk.length() - 1))).isFalse();
        }
        assertThat(String.join("", chunks)).isEqualTo(emoji.repeat(2500));
    }

    @Test
    void truncatesAtTheFifthChunk() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 12; i++) text.append("p".repeat(1500)).append("\n\n");

        List<String> chunks = splitter.split(text.toString());

        assertThat(chunks).hasSize(5);
        assertThat(chunks.getLast()).endsWith(MessageSplitter.TRUNCATION_NOTICE);
        assertThat(chunks.getLast().codePointCount(0, chunks.getLast().length())).isLessThanOrEqualTo(1900);
    }

    @Test
    void neutralizesMassAndRoleMentions() {
        String out = splitter.split("Oi @everyone e @here, chama o <@&123456> e o <@789>").getFirst();
        assertThat(out)
                .doesNotContain("@everyone", "@here", "<@&123456>")
                .contains("@​everyone", "@​here", "<@​&123456>")
                .contains("<@789>");
    }
}

