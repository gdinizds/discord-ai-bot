package dev.gdinizds.discordaibot.domain.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ExpressionCalculator {

    public static final int MAX_LENGTH = 300;
    static final int MAX_DEPTH = 64;
    static final int MAX_INTEGER_EXPONENT = 10_000;
    static final int MAX_FACTORIAL = 200;
    static final BigDecimal MAX_MAGNITUDE = new BigDecimal("1e1000");

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final MathContext DISPLAY = new MathContext(15, RoundingMode.HALF_EVEN);

    public static final class CalculationException extends RuntimeException {
        public CalculationException(String message) {
            super(message);
        }
    }

    private final String input;
    private int pos;
    private int depth;

    private ExpressionCalculator(String input) {
        this.input = input;
    }

    public static BigDecimal evaluate(String expression) {
        if (expression == null || expression.isBlank()) throw new CalculationException("expressão vazia");
        if (expression.length() > MAX_LENGTH) throw new CalculationException("expressão longa demais");
        String normalized = expression.strip()
                .replace('×', '*').replace('÷', '/').replace('−', '-').replace("**", "^");
        var parser = new ExpressionCalculator(normalized);
        BigDecimal result = parser.expression();
        parser.skipSpaces();
        if (parser.pos < normalized.length()) {
            char c = normalized.charAt(parser.pos);
            if (c == ',') throw new CalculationException("use ponto como separador decimal (ex.: 1.5)");
            throw new CalculationException("símbolo inesperado '" + c + "' na posição " + (parser.pos + 1));
        }
        return result;
    }

    public static String format(BigDecimal value) {
        BigDecimal rounded = value.round(DISPLAY).stripTrailingZeros();
        if (rounded.signum() == 0) return "0";
        int magnitude = rounded.precision() - rounded.scale() - 1;
        if (magnitude >= 15 || magnitude < -9) return rounded.toString().replace("E+", "e").replace("E", "e");
        return rounded.toPlainString();
    }

    private BigDecimal expression() {
        enter();
        BigDecimal value = term();
        while (true) {
            skipSpaces();
            if (eat('+')) value = checked(value.add(term(), MC));
            else if (eat('-')) value = checked(value.subtract(term(), MC));
            else break;
        }
        depth--;
        return value;
    }

    private BigDecimal term() {
        BigDecimal value = unary();
        while (true) {
            skipSpaces();
            if (eat('*')) {
                value = checked(value.multiply(unary(), MC));
            } else if (eat('/')) {
                BigDecimal divisor = unary();
                if (divisor.signum() == 0) throw new CalculationException("divisão por zero");
                value = checked(value.divide(divisor, MC));
            } else if (peekImplicitMultiplication()) {
                value = checked(value.multiply(unary(), MC));
            } else {
                break;
            }
        }
        return value;
    }

    private BigDecimal unary() {
        skipSpaces();
        if (eat('-')) {
            enter();
            BigDecimal v = unary().negate();
            depth--;
            return v;
        }
        if (eat('+')) {
            enter();
            BigDecimal v = unary();
            depth--;
            return v;
        }
        return power();
    }

    private BigDecimal power() {
        BigDecimal base = postfix();
        skipSpaces();
        if (eat('^')) {
            enter();
            BigDecimal exponent = unary();
            depth--;
            return pow(base, exponent);
        }
        return base;
    }

    private BigDecimal postfix() {
        BigDecimal value = primary();
        while (true) {
            skipSpaces();
            if (eat('%')) value = value.divide(BigDecimal.valueOf(100), MC);
            else if (peek() == '!' && !peekAt(1, '=')) {
                pos++;
                value = factorial(value);
            } else break;
        }
        return value;
    }

    private BigDecimal primary() {
        skipSpaces();
        if (pos >= input.length()) throw new CalculationException("expressão incompleta");
        char c = input.charAt(pos);
        if (c == '(') {
            pos++;
            BigDecimal inner = expression();
            skipSpaces();
            if (!eat(')')) throw new CalculationException("falta fechar parêntese");
            return inner;
        }
        if (Character.isDigit(c) || c == '.') return number();
        if (c == 'π') {
            pos++;
            return BigDecimal.valueOf(Math.PI);
        }
        if (Character.isLetter(c)) return identifier();
        throw new CalculationException("símbolo inesperado '" + c + "' na posição " + (pos + 1));
    }

    private BigDecimal number() {
        int start = pos;
        while (pos < input.length() && (Character.isDigit(input.charAt(pos)) || input.charAt(pos) == '_')) pos++;
        if (pos < input.length() && input.charAt(pos) == '.') {
            pos++;
            while (pos < input.length() && Character.isDigit(input.charAt(pos))) pos++;
        }
        if (pos < input.length() && (input.charAt(pos) == 'e' || input.charAt(pos) == 'E')) {
            int mark = pos;
            pos++;
            if (pos < input.length() && (input.charAt(pos) == '+' || input.charAt(pos) == '-')) pos++;
            if (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                while (pos < input.length() && Character.isDigit(input.charAt(pos))) pos++;
            } else {
                pos = mark;
            }
        }
        String text = input.substring(start, pos).replace("_", "");
        try {
            return checked(new BigDecimal(text));
        } catch (NumberFormatException e) {
            throw new CalculationException("número inválido '" + text + "'");
        }
    }

    private BigDecimal identifier() {
        int start = pos;
        while (pos < input.length() && (Character.isLetterOrDigit(input.charAt(pos)))) pos++;
        String name = input.substring(start, pos).toLowerCase(Locale.ROOT);
        skipSpaces();
        if (peek() != '(') {
            return switch (name) {
                case "pi" -> BigDecimal.valueOf(Math.PI);
                case "e" -> BigDecimal.valueOf(Math.E);
                default -> throw new CalculationException("nome desconhecido '" + name + "'");
            };
        }
        pos++;
        List<BigDecimal> args = new ArrayList<>();
        skipSpaces();
        if (!eat(')')) {
            do {
                args.add(expression());
                skipSpaces();
            } while (eat(','));
            if (!eat(')')) throw new CalculationException("falta fechar parêntese em " + name + "(...)");
        }
        return function(name, args);
    }

    private BigDecimal function(String name, List<BigDecimal> args) {
        return switch (name) {
            case "sqrt", "raiz" -> {
                BigDecimal x = one(name, args);
                if (x.signum() < 0) throw new CalculationException("raiz de número negativo");
                yield x.sqrt(MC);
            }
            case "cbrt" -> real(Math.cbrt(one(name, args).doubleValue()));
            case "abs" -> one(name, args).abs();
            case "floor" -> one(name, args).setScale(0, RoundingMode.FLOOR);
            case "ceil" -> one(name, args).setScale(0, RoundingMode.CEILING);
            case "round" -> {
                if (args.size() == 1) yield args.getFirst().setScale(0, RoundingMode.HALF_UP);
                if (args.size() == 2) yield args.getFirst().setScale(integer(args.get(1), "casas"), RoundingMode.HALF_UP);
                throw new CalculationException("round aceita 1 ou 2 argumentos");
            }
            case "ln" -> real(Math.log(positive(one(name, args), name).doubleValue()));
            case "log", "log10" -> {
                if (args.size() == 2 && name.equals("log")) {
                    double base = positive(args.get(1), name).doubleValue();
                    if (base == 1) throw new CalculationException("base de log não pode ser 1");
                    yield real(Math.log(positive(args.getFirst(), name).doubleValue()) / Math.log(base));
                }
                yield real(Math.log10(positive(one(name, args), name).doubleValue()));
            }
            case "log2" -> real(Math.log(positive(one(name, args), name).doubleValue()) / Math.log(2));
            case "exp" -> real(Math.exp(one(name, args).doubleValue()));
            case "sin", "sen" -> real(Math.sin(one(name, args).doubleValue()));
            case "cos" -> real(Math.cos(one(name, args).doubleValue()));
            case "tan" -> real(Math.tan(one(name, args).doubleValue()));
            case "asin" -> real(Math.asin(one(name, args).doubleValue()));
            case "acos" -> real(Math.acos(one(name, args).doubleValue()));
            case "atan" -> real(Math.atan(one(name, args).doubleValue()));
            case "min" -> atLeastOne(name, args).stream().min(BigDecimal::compareTo).orElseThrow();
            case "max" -> atLeastOne(name, args).stream().max(BigDecimal::compareTo).orElseThrow();
            case "avg", "media" -> {
                List<BigDecimal> values = atLeastOne(name, args);
                BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, (a, b) -> a.add(b, MC));
                yield sum.divide(BigDecimal.valueOf(values.size()), MC);
            }
            case "pow" -> {
                if (args.size() != 2) throw new CalculationException("pow precisa de 2 argumentos");
                yield pow(args.getFirst(), args.get(1));
            }
            case "mod" -> {
                if (args.size() != 2) throw new CalculationException("mod precisa de 2 argumentos");
                if (args.get(1).signum() == 0) throw new CalculationException("divisão por zero");
                yield args.getFirst().remainder(args.get(1), MC);
            }
            default -> throw new CalculationException("função desconhecida '" + name + "'");
        };
    }

    private static BigDecimal pow(BigDecimal base, BigDecimal exponent) {
        if (isInteger(exponent) && exponent.abs().compareTo(BigDecimal.valueOf(MAX_INTEGER_EXPONENT)) <= 0) {
            int n = exponent.intValueExact();
            if (base.signum() == 0 && n < 0) throw new CalculationException("divisão por zero");
            if (base.signum() != 0) {
                double estimate = n * Math.log10(base.abs().doubleValue());
                if (estimate > 1000) throw new CalculationException("resultado grande demais");
                if (estimate < -1000) return BigDecimal.ZERO;
            }
            return checked(base.pow(n, MC));
        }
        if (base.signum() < 0) throw new CalculationException("potência fracionária de número negativo");
        return real(Math.pow(base.doubleValue(), exponent.doubleValue()));
    }

    private static BigDecimal factorial(BigDecimal value) {
        if (!isInteger(value) || value.signum() < 0) {
            throw new CalculationException("fatorial só de inteiros não negativos");
        }
        if (value.compareTo(BigDecimal.valueOf(MAX_FACTORIAL)) > 0) {
            throw new CalculationException("fatorial até " + MAX_FACTORIAL);
        }
        BigInteger result = BigInteger.ONE;
        for (int i = 2; i <= value.intValueExact(); i++) result = result.multiply(BigInteger.valueOf(i));
        return new BigDecimal(result);
    }

    private static BigDecimal one(String name, List<BigDecimal> args) {
        if (args.size() != 1) throw new CalculationException(name + " precisa de 1 argumento");
        return args.getFirst();
    }

    private static List<BigDecimal> atLeastOne(String name, List<BigDecimal> args) {
        if (args.isEmpty()) throw new CalculationException(name + " precisa de pelo menos 1 argumento");
        return args;
    }

    private static BigDecimal positive(BigDecimal value, String name) {
        if (value.signum() <= 0) throw new CalculationException(name + " só de números positivos");
        return value;
    }

    private static int integer(BigDecimal value, String what) {
        if (!isInteger(value) || value.abs().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new CalculationException(what + " precisa ser inteiro entre -100 e 100");
        }
        return value.intValueExact();
    }

    private static boolean isInteger(BigDecimal value) {
        return value.signum() == 0 || value.stripTrailingZeros().scale() <= 0;
    }

    private static BigDecimal real(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) throw new CalculationException("resultado indefinido");
        return checked(new BigDecimal(value, MC));
    }

    private static BigDecimal checked(BigDecimal value) {
        if (value.abs().compareTo(MAX_MAGNITUDE) > 0) throw new CalculationException("resultado grande demais");
        return value;
    }

    private boolean peekImplicitMultiplication() {
        char c = peek();
        return c == '(' || c == 'π' || (Character.isLetter(c) && c != 'e' && c != 'E');
    }

    private void enter() {
        if (++depth > MAX_DEPTH) throw new CalculationException("expressão aninhada demais");
    }

    private void skipSpaces() {
        while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) pos++;
    }

    private char peek() {
        return pos < input.length() ? input.charAt(pos) : '\0';
    }

    private boolean peekAt(int offset, char expected) {
        int i = pos + offset;
        return i < input.length() && input.charAt(i) == expected;
    }

    private boolean eat(char expected) {
        if (pos < input.length() && input.charAt(pos) == expected) {
            pos++;
            return true;
        }
        return false;
    }
}
