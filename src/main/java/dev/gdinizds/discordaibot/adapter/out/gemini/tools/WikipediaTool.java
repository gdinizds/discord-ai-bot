package dev.gdinizds.discordaibot.adapter.out.gemini.tools;

import dev.gdinizds.discordaibot.application.port.out.EncyclopediaPort;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.time.Duration;
import java.util.regex.Pattern;

public class WikipediaTool {

    static final String UNAVAILABLE = "Wikipedia indisponível no momento.";
    private static final Pattern LANG = Pattern.compile("[a-z]{2,3}");

    private final EncyclopediaPort encyclopedia;
    private final ToolSupport support;
    private final Duration timeout;

    public WikipediaTool(EncyclopediaPort encyclopedia, ToolSupport support, Duration timeout) {
        this.encyclopedia = encyclopedia;
        this.support = support;
        this.timeout = timeout;
    }

    @Tool(name = "wikipedia", value = """
            Resumo de um artigo da Wikipedia. Use para definições, biografias, história, geografia e \
            outros fatos enciclopédicos estáveis. Não use para notícias ou fatos recentes; para isso \
            use web_search.""")
    public String wikipedia(@P("assunto a pesquisar") String query,
                            @P(value = "idioma da Wikipedia, como pt ou en; padrão pt", required = false) String lang) {
        String language = lang == null || !LANG.matcher(lang).matches() ? "pt" : lang;
        return support.run("wikipedia", timeout, UNAVAILABLE, () -> encyclopedia.summary(query, language)
                .map(a -> a.title() + "\n" + a.extract() + "\n" + a.url())
                .orElse("Nenhum artigo encontrado para: " + query));
    }
}

