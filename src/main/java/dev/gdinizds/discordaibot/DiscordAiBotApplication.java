package dev.gdinizds.discordaibot;

import dev.gdinizds.discordaibot.config.NativeRuntimeHints;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@ImportRuntimeHints(NativeRuntimeHints.class)
public class DiscordAiBotApplication {

    static {
        java.security.Security.setProperty("networkaddress.cache.ttl", "60");
    }

    public static void main(String[] args) {
        SpringApplication.run(DiscordAiBotApplication.class, args);
    }

}


