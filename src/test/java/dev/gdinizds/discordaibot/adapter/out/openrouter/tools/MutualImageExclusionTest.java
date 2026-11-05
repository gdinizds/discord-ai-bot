package dev.gdinizds.discordaibot.adapter.out.openrouter.tools;

import dev.gdinizds.discordaibot.adapter.out.image.GarageImageStore;
import dev.gdinizds.discordaibot.adapter.out.image.ImageFormat;
import dev.gdinizds.discordaibot.application.port.out.ImageGeneratePort;
import dev.gdinizds.discordaibot.application.port.out.ImageSearchPort;
import dev.gdinizds.discordaibot.application.port.out.ImageStorePort;
import dev.gdinizds.discordaibot.domain.model.ImageHit;
import dev.gdinizds.discordaibot.domain.model.OutboundImage;
import dev.gdinizds.discordaibot.application.port.out.MetricsPort;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MutualImageExclusionTest {

    @Test
    void imageSearchIsBlockedWhenImageAlreadyGenerated() {
        ImageSearchPort search = mock(ImageSearchPort.class);
        ImageStorePort store = mock(ImageStorePort.class);
        ToolSupport support = new ToolSupport(MetricsPort.NOOP, Executors.newVirtualThreadPerTaskExecutor(), 1000);

        ImageSearchTool tool = new ImageSearchTool(search, store, support, Duration.ofSeconds(5),
                "guild-1", "corr-1", 4, () -> true);

        String result = tool.imageSearch("davi", 1);

        assertThat(result).contains("Uma imagem já foi gerada por IA nesta resposta");
        verify(search, never()).searchImages(anyString(), anyInt());
    }

    @Test
    void imageGenerateIsBlockedWhenImageAlreadySearched() {
        ImageGeneratePort generator = mock(ImageGeneratePort.class);
        GarageImageStore store = mock(GarageImageStore.class);
        ToolSupport support = new ToolSupport(MetricsPort.NOOP, Executors.newVirtualThreadPerTaskExecutor(), 1000);

        List<OutboundImage> attached = new ArrayList<>();
        ImageGenerateTool tool = new ImageGenerateTool(generator, store, support, Duration.ofSeconds(5),
                "guild-1", "corr-1", 4, () -> 1, () -> true, attached::add);

        String result = tool.generateImage("davi gamer portrait");

        assertThat(result).contains("Imagens da web já foram anexadas nesta resposta");
        verify(generator, never()).generate(anyString());
    }
}
