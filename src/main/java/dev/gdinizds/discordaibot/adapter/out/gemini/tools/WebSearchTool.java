package dev.gdinizds.discordaibot.adapter.out.gemini.tools;

import dev.gdinizds.discordaibot.application.port.out.WebSearchPort;
import dev.gdinizds.discordaibot.domain.model.SearchHit;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.time.Duration;
import java.util.List;

public class WebSearchTool {

    static final String UNAVAILABLE = "Busca indisponível no momento.";

    private final WebSearchPort webSearch;
    private final ToolSupport support;
    private final Duration timeout;

    public WebSearchTool(WebSearchPort webSearch, ToolSupport support, Duration timeout) {
        this.webSearch = webSearch;
        this.support = support;
        this.timeout = timeout;
    }

    @Tool(name = "web_search", value = """
            Busca na web. Use para fatos atuais ou que mudam com o tempo: notícias, preços, versões de \
            software, resultados de jogos, eventos recentes. Não use para conhecimento geral estável, \
            conversa ou opinião. Cite as URLs usadas na resposta.""")
    public String webSearch(@P("termos da busca, curtos e objetivos") String query,
                            @P(value = "quantidade de resultados, de 1 a 5", required = false) Integer limit) {
        int n = limit == null ? 3 : Math.clamp(limit, 1, 5);
        return support.run("web_search", timeout, UNAVAILABLE, () -> format(webSearch.search(query, n)));
    }

    private static String format(List<SearchHit> hits) {
        if (hits.isEmpty()) return "Nenhum resultado encontrado.";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hits.size(); i++) {
            SearchHit hit = hits.get(i);
            sb.append(i + 1).append(". ").append(ToolSupport.cut(hit.title(), 200)).append('\n')
                    .append("   ").append(hit.url()).append('\n')
                    .append("   ").append(ToolSupport.cut(hit.snippet(), 300)).append('\n');
        }
        return sb.toString();
    }
}

