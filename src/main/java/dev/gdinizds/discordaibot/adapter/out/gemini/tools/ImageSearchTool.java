package dev.gdinizds.discordaibot.adapter.out.gemini.tools;

import dev.gdinizds.discordaibot.application.port.out.ImageSearchPort;
import dev.gdinizds.discordaibot.application.port.out.ImageStorePort;
import dev.gdinizds.discordaibot.domain.model.ImageHit;
import dev.gdinizds.discordaibot.domain.model.OutboundImage;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ImageSearchTool {

    static final String UNAVAILABLE = "Busca de imagens indisponível no momento.";
    static final int CANDIDATES_PER_IMAGE = 3;
    static final int MAX_CANDIDATES = 12;

    private static final Logger log = LoggerFactory.getLogger(ImageSearchTool.class);

    private final ImageSearchPort search;
    private final ImageStorePort store;
    private final ToolSupport support;
    private final Duration timeout;
    private final String guildId;
    private final String correlationId;
    private final int maxImages;
    private final List<OutboundImage> attached = new CopyOnWriteArrayList<>();

    public ImageSearchTool(ImageSearchPort search, ImageStorePort store, ToolSupport support, Duration timeout,
                           String guildId, String correlationId, int maxImages) {
        this.search = search;
        this.store = store;
        this.support = support;
        this.timeout = timeout;
        this.guildId = guildId;
        this.correlationId = correlationId;
        this.maxImages = maxImages;
    }

    @Tool(name = "image_search", value = """
            Busca imagens na web e as anexa à sua resposta no Discord. Use quando o usuário pedir para ver, \
            mostrar, mandar ou buscar uma imagem, foto, desenho, mapa ou exemplo visual. Não use quando texto \
            basta. As imagens encontradas já seguem anexadas; na resposta, diga em uma frase o que foi enviado \
            e cite as fontes. Nunca escreva links de imagem inventados.""")
    public String imageSearch(@P("o que buscar, em poucas palavras") String query,
                              @P(value = "quantidade de imagens, de 1 a 4", required = false) Integer count) {
        int remaining = maxImages - attached.size();
        if (remaining <= 0) {
            return "Limite de " + maxImages + " imagens por resposta atingido.";
        }
        int wanted = Math.clamp(count == null ? 1 : count, 1, remaining);
        return support.run("image_search", timeout, UNAVAILABLE, () -> attach(query, wanted));
    }

    public List<OutboundImage> attached() {
        return List.copyOf(attached);
    }

    private String attach(String query, int wanted) {
        List<ImageHit> candidates = search.searchImages(query, Math.min(wanted * CANDIDATES_PER_IMAGE, MAX_CANDIDATES));
        int room = Math.min(wanted, maxImages - attached.size());
        List<ImageHit> sent = new ArrayList<>();
        if (room > 0 && !candidates.isEmpty()) {
            for (ImageStorePort.Stored stored : store.storeFirst(guildId, correlationId, attached.size() + 1, candidates, room)) {
                attached.add(stored.image());
                sent.add(stored.hit());
            }
        }
        if (sent.size() < Math.min(room, candidates.size())) {
            log.debug("Only {} of {} image candidate(s) could be stored", sent.size(), candidates.size());
        }
        if (sent.isEmpty()) {
            return candidates.isEmpty()
                    ? "Nenhuma imagem encontrada para essa busca."
                    : "Encontrei imagens, mas nenhuma pôde ser baixada. Diga ao usuário que não conseguiu enviar.";
        }
        StringBuilder sb = new StringBuilder("Anexei ").append(sent.size())
                .append(sent.size() == 1 ? " imagem" : " imagens").append(" à resposta:\n");
        for (int i = 0; i < sent.size(); i++) {
            ImageHit hit = sent.get(i);
            sb.append(i + 1).append(". ").append(ToolSupport.cut(hit.title(), 150));
            if (hit.source() != null && !hit.source().isBlank()) sb.append(" (fonte: ").append(ToolSupport.cut(hit.source(), 60)).append(')');
            if (hit.pageUrl() != null && !hit.pageUrl().isBlank()) sb.append(" - ").append(hit.pageUrl());
            sb.append('\n');
        }
        return sb.toString();
    }
}
