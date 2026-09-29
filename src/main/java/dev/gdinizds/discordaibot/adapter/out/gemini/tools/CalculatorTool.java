package dev.gdinizds.discordaibot.adapter.out.gemini.tools;

import dev.gdinizds.discordaibot.domain.service.ExpressionCalculator;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.time.Duration;

public class CalculatorTool {

    static final String UNAVAILABLE = "Não consegui calcular agora.";

    private final ToolSupport support;
    private final Duration timeout;

    public CalculatorTool(ToolSupport support, Duration timeout) {
        this.support = support;
        this.timeout = timeout;
    }

    @Tool(name = "calculator", value = """
            Calcula expressões com precisão decimal. Use para qualquer conta (custos, porcentagens, conversões, \
            médias, juros) em vez de calcular de cabeça. Operadores: + - * / ^, parênteses, x% (vale x/100) e \
            n!. Funções: sqrt, cbrt, abs, round(x, casas), floor, ceil, ln, log (base 10), log(x, base), log2, \
            exp, sin, cos, tan em radianos, min, max, avg, pow, mod. Constantes: pi, e. Use ponto como separador \
            decimal e vírgula só entre argumentos de função.""")
    public String calculate(@P("expressão, por exemplo 1500 * 7.5% + 32^2") String expression) {
        return support.run("calculator", timeout, UNAVAILABLE, () -> evaluate(expression));
    }

    static String evaluate(String expression) {
        try {
            return expression.strip() + " = " + ExpressionCalculator.format(ExpressionCalculator.evaluate(expression));
        } catch (ExpressionCalculator.CalculationException e) {
            return "Não calculei: " + e.getMessage() + ".";
        } catch (NullPointerException e) {
            return "Não calculei: expressão vazia.";
        }
    }
}
