package dev.gdinizds.discordaibot.domain.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveDataFilterTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "Meu CPF é 123.456.789-09",
            "cpf 12345678909",
            "cartão 4111 1111 1111 1111",
            "4111-1111-1111-1111",
            "minha chave AIzaSyA1234567890abcdefghijklmnopqrstuv",
            "token ghp_abcdefghijklmnopqrstuvwxyz0123456789",
            "password: hunter2hunter2",
    })
    void rejectsSensitiveContent(String content) {
        assertThat(SensitiveDataFilter.isSensitive(content)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Prefere respostas em português",
            "Usa Java 25 e Spring Boot 4",
            "Trabalha no projeto discord-ai-bot desde 2026",
            "Tem 3 gatos",
            "Nasceu em 1990 e mora em Recife",
    })
    void acceptsOrdinaryFacts(String content) {
        assertThat(SensitiveDataFilter.isSensitive(content)).isFalse();
    }
}

