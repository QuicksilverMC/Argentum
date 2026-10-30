package dev.rdh.cera.modules.cem;

import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

final class ExpressionParser {
    interface FloatExpr {
        float eval();
    }

    interface BoolExpr {
        boolean eval();
    }

    private enum Type {
        IDENTIFIER, NUMBER, OPERATOR, COMMA, OPEN, CLOSE
    }

    private record Token(Type type, String text) {
    }

    private final Function<String, Object> variables;
    private final AnimationContext context;

    ExpressionParser(Function<String, Object> variables, AnimationContext context) {
        this.variables = variables;
        this.context = context;
    }

    Object parse(String text) {
        List<Token> tokens = tokenize(text);
        if (tokens.isEmpty()) throw new IllegalArgumentException("Empty expression");
        return this.infix(tokens);
    }

    private static List<Token> tokenize(String text) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            Type type;
            String next;
            if (Character.isLetter(c)) {
                type = Type.IDENTIFIER;
                next = "_:.";
            } else if (Character.isDigit(c)) {
                type = Type.NUMBER;
                next = ".";
            } else if ("+-*/%!&|<>=".indexOf(c) >= 0) {
                type = Type.OPERATOR;
                next = "&|=";
            } else if (c == ',') {
                type = Type.COMMA;
                next = "";
            } else if (c == '(') {
                type = Type.OPEN;
                next = "";
            } else if (c == ')') {
                type = Type.CLOSE;
                next = "";
            } else {
                throw new IllegalArgumentException("Invalid character '" + c + "' in: " + text);
            }
            int start = i++;
            while (i < text.length() && continues(type, next, text.charAt(i))) i++;
            tokens.add(new Token(type, text.substring(start, i)));
        }
        return tokens;
    }

    private static boolean continues(Type type, String next, char c) {
        if (next.indexOf(c) >= 0) return true;
        return switch (type) {
            case IDENTIFIER -> Character.isLetterOrDigit(c);
            case NUMBER -> Character.isDigit(c);
            default -> false;
        };
    }

    private Object infix(List<Token> tokens) {
        int[] cursor = {0};
        List<Object> operands = new ArrayList<>();
        List<String> operators = new ArrayList<>();
        operands.add(this.primary(tokens, cursor));
        while (cursor[0] < tokens.size()) {
            Token operator = tokens.get(cursor[0]++);
            if (operator.type() != Type.OPERATOR) throw new IllegalArgumentException("Invalid operator: " + operator.text());
            precedence(operator.text());
            operators.add(operator.text());
            operands.add(this.primary(tokens, cursor));
        }
        for (int precedence = 11; precedence >= 2; precedence--) {
            for (int i = 0; i < operators.size(); i++) {
                if (precedence(operators.get(i)) != precedence) continue;
                operands.add(i, binary(operators.remove(i), operands.remove(i), operands.remove(i)));
                i--;
            }
        }
        return operands.getFirst();
    }

    private static int precedence(String operator) {
        return switch (operator) {
            case "*", "/", "%" -> 11;
            case "+", "-" -> 10;
            case ">", ">=", "<", "<=" -> 8;
            case "==", "!=" -> 7;
            case "&&" -> 3;
            case "||" -> 2;
            default -> throw new IllegalArgumentException("Invalid operator: " + operator);
        };
    }

    private Object primary(List<Token> tokens, int[] cursor) {
        if (cursor[0] >= tokens.size()) throw new IllegalArgumentException("Missing expression");
        Token token = tokens.get(cursor[0]++);
        switch (token.type()) {
            case NUMBER -> {
                float value = Float.parseFloat(token.text());
                return (FloatExpr)() -> value;
            }
            case IDENTIFIER -> {
                if (cursor[0] < tokens.size() && tokens.get(cursor[0]).type() == Type.OPEN) {
                    cursor[0]++;
                    return this.function(token.text(), this.arguments(group(tokens, cursor)));
                }
                if (ZERO_ARGUMENTS.contains(token.text())) return this.function(token.text(), new Object[0]);
                Object variable = this.variables.apply(token.text());
                if (variable == null) throw new IllegalArgumentException("Unknown variable: " + token.text());
                return variable;
            }
            case OPEN -> {
                return this.infix(group(tokens, cursor));
            }
            case OPERATOR -> {
                switch (token.text()) {
                    case "+" -> {
                        return this.primary(tokens, cursor);
                    }
                    case "-" -> {
                        FloatExpr value = asFloat(this.primary(tokens, cursor), "-");
                        return (FloatExpr)() -> -value.eval();
                    }
                    case "!" -> {
                        BoolExpr value = asBool(this.primary(tokens, cursor), "!");
                        return (BoolExpr)() -> !value.eval();
                    }
                    default -> throw new IllegalArgumentException("Invalid expression: " + token.text());
                }
            }
            default -> throw new IllegalArgumentException("Invalid expression: " + token.text());
        }
    }

    private static final List<String> ZERO_ARGUMENTS = List.of("pi", "time", "true", "false", "random");

    private static List<Token> group(List<Token> tokens, int[] cursor) {
        int level = 0;
        for (int i = cursor[0]; i < tokens.size(); i++) {
            Type type = tokens.get(i).type();
            if (type == Type.CLOSE && level == 0) {
                List<Token> group = tokens.subList(cursor[0], i);
                cursor[0] = i + 1;
                return group;
            }
            if (type == Type.OPEN) level++;
            if (type == Type.CLOSE) level--;
        }
        throw new IllegalArgumentException("Missing )");
    }

    private Object[] arguments(List<Token> tokens) {
        List<Object> arguments = new ArrayList<>();
        int start = 0;
        int level = 0;
        for (int i = 0; i <= tokens.size(); i++) {
            Type type = i < tokens.size() ? tokens.get(i).type() : Type.COMMA;
            if (type == Type.OPEN) level++;
            if (type == Type.CLOSE) level--;
            if (type != Type.COMMA || level != 0) continue;
            if (i == start) break;
            arguments.add(this.infix(tokens.subList(start, i)));
            start = i + 1;
        }
        return arguments.toArray();
    }

    private static Object binary(String operator, Object left, Object right) {
        if (operator.equals("&&") || operator.equals("||")) {
            BoolExpr a = asBool(left, operator);
            BoolExpr b = asBool(right, operator);
            return operator.equals("&&") ? (BoolExpr)() -> a.eval() && b.eval() : (BoolExpr)() -> a.eval() || b.eval();
        }
        FloatExpr a = asFloat(left, operator);
        FloatExpr b = asFloat(right, operator);
        return switch (operator) {
            case "+" -> (FloatExpr)() -> a.eval() + b.eval();
            case "-" -> (FloatExpr)() -> a.eval() - b.eval();
            case "*" -> (FloatExpr)() -> a.eval() * b.eval();
            case "/" -> (FloatExpr)() -> a.eval() / b.eval();
            case "%" -> (FloatExpr)() -> {
                float x = a.eval();
                float y = b.eval();
                return x - y * (int)(x / y);
            };
            case ">" -> (BoolExpr)() -> a.eval() > b.eval();
            case ">=" -> (BoolExpr)() -> a.eval() >= b.eval();
            case "<" -> (BoolExpr)() -> a.eval() < b.eval();
            case "<=" -> (BoolExpr)() -> a.eval() <= b.eval();
            case "==" -> (BoolExpr)() -> a.eval() == b.eval();
            default -> (BoolExpr)() -> a.eval() != b.eval();
        };
    }

    private Object function(String name, Object[] args) {
        return switch (name) {
            case "pi" -> constant(name, args, (float)Math.PI);
            case "true" -> {
                arity(name, args, 0, 0);
                yield (BoolExpr)() -> true;
            }
            case "false" -> {
                arity(name, args, 0, 0);
                yield (BoolExpr)() -> false;
            }
            case "time" -> {
                arity(name, args, 0, 0);
                AnimationContext context = this.context;
                yield (FloatExpr)context::time;
            }
            case "random" -> {
                FloatExpr[] f = floats(name, args, 0, 1);
                yield f.length == 0 ? (FloatExpr)() -> (float)Math.random() : (FloatExpr)() -> random(f[0].eval());
            }
            case "sin" -> unary(name, args, MathHelper::sin);
            case "cos" -> unary(name, args, MathHelper::cos);
            case "asin" -> unary(name, args, x -> (float)Math.asin(x));
            case "acos" -> unary(name, args, x -> (float)Math.acos(x));
            case "tan" -> unary(name, args, x -> (float)Math.tan(x));
            case "atan" -> unary(name, args, x -> (float)Math.atan(x));
            case "torad" -> unary(name, args, x -> x * ((float)Math.PI / 180.0F));
            case "todeg" -> unary(name, args, x -> x * (180.0F / (float)Math.PI));
            case "abs" -> unary(name, args, MathHelper::abs);
            case "floor" -> unary(name, args, x -> MathHelper.floor(x));
            case "ceil" -> unary(name, args, x -> MathHelper.ceil(x));
            case "exp" -> unary(name, args, x -> (float)Math.exp(x));
            case "frac" -> unary(name, args, x -> (float)(x - Math.floor(x)));
            case "log" -> unary(name, args, x -> (float)Math.log(x));
            case "round" -> unary(name, args, x -> Math.round(x));
            case "signum" -> unary(name, args, Math::signum);
            case "sqrt" -> unary(name, args, MathHelper::sqrt);
            case "atan2" -> {
                FloatExpr[] f = floats(name, args, 2, 2);
                yield (FloatExpr)() -> (float)MathHelper.fastAtan2(f[0].eval(), f[1].eval());
            }
            case "pow" -> {
                FloatExpr[] f = floats(name, args, 2, 2);
                yield (FloatExpr)() -> (float)Math.pow(f[0].eval(), f[1].eval());
            }
            case "fmod" -> {
                FloatExpr[] f = floats(name, args, 2, 2);
                yield (FloatExpr)() -> {
                    float x = f[0].eval();
                    float y = f[1].eval();
                    return x - y * MathHelper.floor(x / y);
                };
            }
            case "clamp" -> {
                FloatExpr[] f = floats(name, args, 3, 3);
                yield (FloatExpr)() -> MathHelper.clamp(f[0].eval(), f[1].eval(), f[2].eval());
            }
            case "lerp" -> {
                FloatExpr[] f = floats(name, args, 3, 3);
                yield (FloatExpr)() -> {
                    float k = f[0].eval();
                    float x = f[1].eval();
                    return x + k * (f[2].eval() - x);
                };
            }
            case "min", "max" -> {
                FloatExpr[] f = floats(name, args, 1, Integer.MAX_VALUE);
                boolean min = name.equals("min");
                yield (FloatExpr)() -> {
                    float result = f[0].eval();
                    for (int i = 1; i < f.length; i++) result = min ? Math.min(result, f[i].eval()) : Math.max(result, f[i].eval());
                    return result;
                };
            }
            case "if" -> {
                FloatExpr[] values = new FloatExpr[(args.length + 1) / 2];
                BoolExpr[] conditions = conditions(name, args, values.length);
                for (int i = 0; i < values.length; i++) values[i] = asFloat(args[Math.min(i * 2 + 1, args.length - 1)], name);
                yield (FloatExpr)() -> {
                    for (int i = 0; i < conditions.length; i++) if (conditions[i].eval()) return values[i].eval();
                    return values[conditions.length].eval();
                };
            }
            case "ifb" -> {
                BoolExpr[] values = new BoolExpr[(args.length + 1) / 2];
                BoolExpr[] conditions = conditions(name, args, values.length);
                for (int i = 0; i < values.length; i++) values[i] = asBool(args[Math.min(i * 2 + 1, args.length - 1)], name);
                yield (BoolExpr)() -> {
                    for (int i = 0; i < conditions.length; i++) if (conditions[i].eval()) return values[i].eval();
                    return values[conditions.length].eval();
                };
            }
            case "between" -> {
                FloatExpr[] f = floats(name, args, 3, 3);
                yield (BoolExpr)() -> {
                    float x = f[0].eval();
                    return x >= f[1].eval() && x <= f[2].eval();
                };
            }
            case "equals" -> {
                FloatExpr[] f = floats(name, args, 3, 3);
                yield (BoolExpr)() -> Math.abs(f[0].eval() - f[1].eval()) <= f[2].eval();
            }
            case "in" -> {
                FloatExpr[] f = floats(name, args, 2, Integer.MAX_VALUE);
                yield (BoolExpr)() -> {
                    float x = f[0].eval();
                    for (int i = 1; i < f.length; i++) if (x == f[i].eval()) return true;
                    return false;
                };
            }
            case "smooth" -> {
                FloatExpr[] f = floats(name, args, 2, 4);
                AnimationContext context = this.context;
                yield (FloatExpr)() -> {
                    float up = f.length > 2 ? f[2].eval() : 1.0F;
                    return context.smooth((int)f[0].eval(), f[1].eval(), up, f.length > 3 ? f[3].eval() : up);
                };
            }
            case "print" -> {
                arity(name, args, 3, 3);
                FloatExpr[] f = {asFloat(args[0], name), asFloat(args[1], name), asFloat(args[2], name)};
                AnimationContext context = this.context;
                yield (FloatExpr)() -> {
                    float value = f[2].eval();
                    context.print((int)f[0].eval(), (int)f[1].eval(), Float.toString(value));
                    return value;
                };
            }
            case "printb" -> {
                arity(name, args, 3, 3);
                FloatExpr id = asFloat(args[0], name);
                FloatExpr every = asFloat(args[1], name);
                BoolExpr value = asBool(args[2], name);
                AnimationContext context = this.context;
                yield (BoolExpr)() -> {
                    boolean result = value.eval();
                    context.print((int)id.eval(), (int)every.eval(), Boolean.toString(result));
                    return result;
                };
            }
            default -> throw new IllegalArgumentException("Unknown function: " + name);
        };
    }

    private static float random(float seed) {
        int hash = Float.floatToIntBits(seed) * 0x9E3779B9;
        hash ^= hash >>> 16;
        hash *= 0x85EBCA6B;
        hash ^= hash >>> 13;
        return (hash >>> 8) / (float)(1 << 24);
    }

    private interface FloatFunction {
        float apply(float x);
    }

    private static FloatExpr unary(String name, Object[] args, FloatFunction function) {
        FloatExpr x = floats(name, args, 1, 1)[0];
        return () -> function.apply(x.eval());
    }

    private static FloatExpr constant(String name, Object[] args, float value) {
        arity(name, args, 0, 0);
        return () -> value;
    }

    private static BoolExpr[] conditions(String name, Object[] args, int values) {
        if (args.length < 3 || args.length % 2 == 0) throw new IllegalArgumentException("Invalid number of arguments for " + name);
        BoolExpr[] conditions = new BoolExpr[values - 1];
        for (int i = 0; i < conditions.length; i++) conditions[i] = asBool(args[i * 2], name);
        return conditions;
    }

    private static FloatExpr[] floats(String name, Object[] args, int min, int max) {
        arity(name, args, min, max);
        FloatExpr[] floats = new FloatExpr[args.length];
        for (int i = 0; i < args.length; i++) floats[i] = asFloat(args[i], name);
        return floats;
    }

    private static void arity(String name, Object[] args, int min, int max) {
        if (args.length < min || args.length > max) throw new IllegalArgumentException("Invalid number of arguments for " + name);
    }

    static FloatExpr asFloat(Object expression, String where) {
        if (expression instanceof FloatExpr value) return value;
        throw new IllegalArgumentException("Expected a number in " + where);
    }

    static BoolExpr asBool(Object expression, String where) {
        if (expression instanceof BoolExpr value) return value;
        throw new IllegalArgumentException("Expected a boolean in " + where);
    }
}
