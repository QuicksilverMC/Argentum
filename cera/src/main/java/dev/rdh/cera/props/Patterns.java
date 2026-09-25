package dev.rdh.cera.props;

import java.util.function.Predicate;
import java.util.regex.Pattern;

public final class Patterns {
    private Patterns() {}

    public static Pattern parse(String spec) {
        if (spec.startsWith("ipattern:")) return Pattern.compile(glob(spec.substring(9)), Pattern.CASE_INSENSITIVE);
        if (spec.startsWith("pattern:")) return Pattern.compile(glob(spec.substring(8)));
        if (spec.startsWith("iregex:")) return Pattern.compile(spec.substring(7), Pattern.CASE_INSENSITIVE);
        if (spec.startsWith("regex:")) return Pattern.compile(spec.substring(6));
        return null;
    }

    public static Predicate<String> matcher(String spec) {
        if (spec == null) return null;
        boolean negate = spec.startsWith("!");
        if (negate) spec = spec.substring(1);
        Pattern pattern = parse(spec);
        if (pattern == null) {
            String literal = spec;
            return value -> value != null && literal.equals(value) != negate;
        }
        return value -> value != null && pattern.matcher(value).matches() != negate;
    }

    public static String glob(String value) {
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == '*') regex.append(".*");
            else if (character == '?') regex.append('.');
            else regex.append(Pattern.quote(String.valueOf(character)));
        }
        return regex.toString();
    }
}
