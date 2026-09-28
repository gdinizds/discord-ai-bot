package dev.gdinizds.discordaibot.domain.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContentHasherTest {

    @Test
    void crlfAndSurroundingWhitespaceDoNotChangeTheHash() {
        String hash = ContentHasher.hash("linha 1\nlinha 2");
        assertThat(ContentHasher.hash("linha 1\r\nlinha 2")).isEqualTo(hash);
        assertThat(ContentHasher.hash("  \nlinha 1\nlinha 2 \n\t")).isEqualTo(hash);
    }

    @Test
    void differentContentHasDifferentHash() {
        assertThat(ContentHasher.hash("a")).isNotEqualTo(ContentHasher.hash("b"));
    }

    @Test
    void hashIsHexSha256() {
        assertThat(ContentHasher.hash("x")).hasSize(64).matches("[0-9a-f]+");
    }
}

