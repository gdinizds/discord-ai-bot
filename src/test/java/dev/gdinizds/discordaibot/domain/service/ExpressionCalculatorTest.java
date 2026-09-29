package dev.gdinizds.discordaibot.domain.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExpressionCalculatorTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "0.1 + 0.2                 | 0.3",
            "1 + 2 * 3                 | 7",
            "(1 + 2) * 3               | 9",
            "10 / 4                    | 2.5",
            "1 / 3                     | 0.333333333333333",
            "2 ^ 10                    | 1024",
            "2 ^ 3 ^ 2                 | 512",
            "-2 ^ 2                    | -4",
            "2 ^ -1                    | 0.5",
            "2 ** 8                    | 256",
            "200 * 15%                 | 30",
            "1500 * 7.5%               | 112.5",
            "5!                        | 120",
            "sqrt(2)                   | 1.4142135623731",
            "sqrt(16) + abs(-4)        | 8",
            "round(2.345, 2)           | 2.35",
            "round(2.5)                | 3",
            "floor(-2.5) + ceil(2.1)   | 0",
            "log(1000)                 | 3",
            "log(8, 2)                 | 3",
            "log2(1024)                | 10",
            "ln(e)                     | 1",
            "min(3, 1, 2) + max(4, 9)  | 10",
            "avg(1, 2, 3, 4)           | 2.5",
            "mod(17, 5)                | 2",
            "pow(3, 4)                 | 81",
            "2(3 + 4)                  | 14",
            "2pi                       | 6.28318530717959",
            "1_000_000 / 1e3           | 1000",
            "3 × 4 ÷ 2                 | 6",
            "1e20 * 1e10               | 1e30",
            "0.0000000001 * 2          | 2e-10",
            "0.5 ^ 5000                | 0",
    })
    void evaluatesExpressions(String expression, String expected) {
        assertThat(ExpressionCalculator.format(ExpressionCalculator.evaluate(expression))).isEqualTo(expected);
    }

    @Test
    void decimalMoneyMathIsExact() {
        assertThat(ExpressionCalculator.evaluate("19.90 * 3 - 0.70")).isEqualByComparingTo("59.00");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "1 / 0", "mod(1, 0)", "sqrt(-1)", "ln(0)", "log(-5)", "(1 + 2", "1 +", "", "   ",
            "1,5 + 2", "foo(3)", "x + 1", "2.5!", "201!", "10 ^ 5000", "(-8) ^ 0.5", "1 $ 2", "log(8, 1)",
            "round(1.5, 1000)"
    })
    void rejectsInvalidOrUnsafeExpressions(String expression) {
        assertThatThrownBy(() -> ExpressionCalculator.evaluate(expression))
                .isInstanceOf(ExpressionCalculator.CalculationException.class);
    }

    @Test
    void rejectsOverlyLongOrDeeplyNestedInput() {
        assertThatThrownBy(() -> ExpressionCalculator.evaluate("1+".repeat(200) + "1"))
                .hasMessageContaining("longa");
        assertThatThrownBy(() -> ExpressionCalculator.evaluate("(".repeat(100) + "1" + ")".repeat(100)))
                .hasMessageContaining("aninhada");
    }

    @Test
    void commaGetsAHelpfulMessage() {
        assertThatThrownBy(() -> ExpressionCalculator.evaluate("1,5 * 2")).hasMessageContaining("ponto");
    }
}
