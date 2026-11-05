package dev.gdinizds.discordaibot.adapter.out.openrouter.tools;

import dev.gdinizds.discordaibot.adapter.out.image.GarageImageStore;
import dev.gdinizds.discordaibot.adapter.out.image.ImageFormat;
import dev.gdinizds.discordaibot.application.port.out.ImageGeneratePort;
import dev.gdinizds.discordaibot.domain.model.ImageHit;
import dev.gdinizds.discordaibot.domain.model.OutboundImage;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

public class ImageGenerateTool {

    static final String UNAVAILABLE = "Geração de imagens indisponível no momento.";
    private static final Logger log = LoggerFactory.getLogger(ImageGenerateTool.class);

    private final ImageGeneratePort generator;
    private final GarageImageStore store;
    private final ToolSupport support;
    private final Duration timeout;
    private final String guildId;
    private final String correlationId;
    private final int maxImages;
    private final IntSupplier currentAttachedCount;
    private final BooleanSupplier hasSearchedImages;
    private final Consumer<OutboundImage> onImageAttached;

    public ImageGenerateTool(ImageGeneratePort generator, GarageImageStore store, ToolSupport support,
                             Duration timeout, String guildId, String correlationId, int maxImages,
                             IntSupplier currentAttachedCount, BooleanSupplier hasSearchedImages,
                             Consumer<OutboundImage> onImageAttached) {
        this.generator = generator;
        this.store = store;
        this.support = support;
        this.timeout = timeout;
        this.guildId = guildId;
        this.correlationId = correlationId;
        this.maxImages = maxImages;
        this.currentAttachedCount = currentAttachedCount;
        this.hasSearchedImages = hasSearchedImages;
        this.onImageAttached = onImageAttached;
    }

    @Tool(name = "image_generate", value = """
            Gera uma imagem original por inteligência artificial a partir de uma descrição detalhada em texto e \
            a anexa diretamente à sua resposta no Discord. Use OBRIGATORIAMENTE quando o usuário pedir para gerar, \
            desenhar, pintar, ilustrar ou criar uma imagem artística, em vez de buscar fotos reais da web. \
            NUNCA combine esta ferramenta com image_search na mesma resposta. \
            Escreva um prompt em inglês descritivo e rico em detalhes visuais para obter o melhor resultado do gerador.""")
    public String generateImage(@P("descrição visual detalhada da imagem a ser gerada (prefira em inglês para melhor fidelidade)") String prompt) {
        if (hasSearchedImages.getAsBoolean()) {
            return "Imagens da web já foram anexadas nesta resposta. Não gere imagens por IA juntas.";
        }
        int currentCount = currentAttachedCount.getAsInt();
        if (currentCount >= maxImages) {
            return "Limite de " + maxImages + " imagens por resposta atingido.";
        }
        return support.run("image_generate", timeout, UNAVAILABLE, () -> {
            ImageGeneratePort.GeneratedImage generated = generator.generate(prompt);
            byte[] bytes = generated.data();
            ImageFormat format = ImageFormat.sniff(bytes).orElse(ImageFormat.JPEG);
            int nextIndex = currentAttachedCount.getAsInt() + 1;
            OutboundImage outbound = store.storeDirect(guildId, correlationId, nextIndex, bytes, format, prompt);
            onImageAttached.accept(outbound);
            return "Imagem gerada com sucesso e anexada à resposta. Não busque nem gere mais imagens.";
        });
    }
}
