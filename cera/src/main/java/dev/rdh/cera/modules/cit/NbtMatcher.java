package dev.rdh.cera.modules.cit;

import dev.rdh.cera.props.NumberList;
import dev.rdh.cera.props.Patterns;
import dev.rdh.cera.props.Result;
import net.minecraft.nbt.NbtByte;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtLong;
import net.minecraft.nbt.NbtShort;
import net.minecraft.nbt.NbtString;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Matches an OptiFine-style {@code nbt.<path>=<matcher>} entry against an item's NBT tree. */
public record NbtMatcher(String[] path, ValueMatcher matcher) {
    public static NbtMatcher parse(String path, String value) {
        return new NbtMatcher(path.split("\\."), ValueMatcher.parse(value));
    }

    public boolean matches(NbtCompound nbt) {
        return matches(nbt, 0);
    }

    private boolean matches(NbtElement element, int index) {
        if (index == path.length) return matcher.matches(element);
        if (element instanceof NbtCompound compound) {
            String segment = path[index];
            if ("*".equals(segment)) {
                for (String key : compound.getKeys()) if (matches(compound.get(key), index + 1)) return true;
                return matcher.matches(null);
            }
            return matches(compound.get(segment), index + 1);
        }
        if (element instanceof NbtList list) {
            String segment = path[index];
            if ("count".equals(segment)) return index + 1 == path.length && matcher.matches(list.size());
            if ("*".equals(segment)) {
                for (int i = 0; i < list.size(); i++) if (matches(list.getElement(i), index + 1)) return true;
                return matcher.matches(null);
            }
            try {
                int elementIndex = Integer.parseInt(segment);
                return elementIndex >= 0 && elementIndex < list.size()
                        ? matches(list.getElement(elementIndex), index + 1) : matcher.matches(null);
            } catch (NumberFormatException e) {
                return matcher.matches(null);
            }
        }
        return matcher.matches(null);
    }

    record ValueMatcher(boolean negate, Boolean exists, NumberList range, Pattern pattern, boolean raw, String value) {
        static ValueMatcher parse(String input) {
            boolean negate = input.startsWith("!");
            if (negate) input = input.substring(1);
            if (input.startsWith("exists:")) return new ValueMatcher(negate, Boolean.parseBoolean(input.substring(7)), null, null, false, null);
            boolean raw = input.startsWith("raw:");
            if (raw) input = input.substring(4);
            if (input.startsWith("range:")) return new ValueMatcher(negate, null, numbers(input.substring(6)), null, raw, null);
            try {
                Pattern pattern = Patterns.parse(input);
                return pattern == null
                        ? new ValueMatcher(negate, null, null, null, raw, input)
                        : new ValueMatcher(negate, null, null, pattern, raw, null);
            } catch (PatternSyntaxException e) {
                throw new IllegalArgumentException("Invalid NBT matcher", e);
            }
        }

        boolean matches(NbtElement element) {
            boolean matched;
            if (exists != null) matched = (element != null) == exists;
            else if (element == null) matched = false;
            else if (range != null) matched = element instanceof NbtElement.Number number && range.contains(number.getInt());
            else {
                String actual = raw ? element.toString() : text(element);
                matched = pattern == null ? value.equals(actual) : pattern.matcher(actual).matches();
            }
            return negate != matched;
        }

        private static String text(NbtElement element) {
            return switch (element) {
                case NbtString string -> string.asString();
                case NbtByte number -> Byte.toString(number.getByte());
                case NbtShort number -> Short.toString(number.getShort());
                case NbtInt number -> Integer.toString(number.getInt());
                case NbtLong number -> Long.toString(number.getLong());
                case NbtFloat number -> Float.toString(number.getFloat());
                case NbtDouble number -> Double.toString(number.getDouble());
                default -> element.toString();
            };
        }

        boolean matches(int value) {
            boolean matched;
            if (exists != null) matched = exists;
            else if (range != null) matched = range.contains(value);
            else {
                String actual = Integer.toString(value);
                matched = pattern == null ? this.value.equals(actual) : pattern.matcher(actual).matches();
            }
            return negate != matched;
        }

        private static NumberList numbers(String value) {
            Result<NumberList> result = NumberList.parse(value);
            if (!result.isSuccess()) throw new IllegalArgumentException(result.error());
            return result.value();
        }
    }
}
