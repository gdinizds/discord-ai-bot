package dev.gdinizds.discordaibot.adapter.out.kafka;

import dev.gdinizds.discordaibot.config.AiBotProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class CommandRegistrar implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CommandRegistrar.class);

    static final String SLASH_IA = """
            {
              "prefix": "SLASH",
              "name": "ia",
              "description": "Pergunte algo para a IA",
              "parameters": [
                { "name": "pergunta", "description": "Sua pergunta", "type": "STRING", "required": true }
              ],
              "is_deleted": false
            }
            """;

    static final String DOT_IA = """
            {
              "prefix": "DOT",
              "name": "ia",
              "description": "Pergunte algo para a IA, aceita imagens: .ia pergunta",
              "is_deleted": false
            }
            """;

    static final String SLASH_IA_MEMORIA = """
            {
              "prefix": "SLASH",
              "name": "ia-memoria",
              "description": "Veja ou apague o que a IA lembra sobre você neste servidor",
              "parameters": [
                { "name": "acao", "description": "listar, esquecer ou esquecer-tudo", "type": "STRING", "required": true },
                { "name": "id", "description": "Número da memória, para esquecer", "type": "INTEGER", "required": false }
              ],
              "ephemeral": true,
              "is_deleted": false
            }
            """;

    static final String SLASH_IA_LIMITES = """
            {
              "prefix": "SLASH",
              "name": "ia-limites",
              "description": "Veja quanto você e o servidor já usaram da IA",
              "ephemeral": true,
              "is_deleted": false
            }
            """;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;

    public CommandRegistrar(KafkaTemplate<String, String> kafkaTemplate, AiBotProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = properties.topics().gatewayCommands();
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            kafkaTemplate.send(topic, "ia", SLASH_IA);
            kafkaTemplate.send(topic, "ia", DOT_IA);
            kafkaTemplate.send(topic, "ia-memoria", SLASH_IA_MEMORIA);
            kafkaTemplate.send(topic, "ia-limites", SLASH_IA_LIMITES);
            log.info("Discord commands registered: /ia, .ia, /ia-memoria, /ia-limites");
        } catch (RuntimeException e) {

            log.error("Failed to register Discord commands: {}", e.toString());
        }
    }
}

