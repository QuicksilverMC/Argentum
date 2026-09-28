package dev.rdh.cera.props;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * OptiFine-style numbered rule set ({@code <key>.<n>} properties) with weighted random selection.
 */
public final class RandomRules<T> {
    private static final int MAX_VARIANTS = 65536;

    private final List<Rule<T>> rules;

    private RandomRules(List<Rule<T>> rules) {
        this.rules = rules;
    }

    public int select(T subject, int seed) {
        for (Rule<T> rule : this.rules) {
            if (rule.condition.test(subject)) return rule.pick(seed);
        }
        return 1;
    }

    public boolean isEmpty() {
        return this.rules.isEmpty();
    }

    /**
     * Parses rules keyed by {@code <key>.<n>}; conditions for rule n are built by {@code conditions}
     * (return null for an unconditional rule).
     */
    public static <T> Result<RandomRules<T>> parse(Props props, String key, Function<Integer, Predicate<T>> conditions) {
        IntArrayList indexes = new IntArrayList();
        for (String name : props.properties().stringPropertyNames()) {
            if (!name.startsWith(key + ".")) continue;
            try {
                int index = Integer.parseInt(name.substring(key.length() + 1));
                if (index >= 1) indexes.add(index);
            } catch (NumberFormatException _) {
            }
        }
        int[] sorted = indexes.toIntArray();
        Arrays.sort(sorted);

        List<Rule<T>> rules = new ArrayList<>();
        for (int index : sorted) {
            Result<Rule<T>> result = Rule.parse(props, key, index, conditions.apply(index));
            if (!result.isSuccess()) return Result.failure(result.error());
            rules.add(result.value());
        }
        return Result.success(new RandomRules<>(List.copyOf(rules)));
    }

    public static final class Rule<T> {
        private final int index;
        private final int[] variants;
        private final int variantCount;
        private final int[] cumulativeWeights;
        private final int totalWeight;
        private final Predicate<T> condition;

        private Rule(int index, int[] variants, int[] cumulativeWeights, Predicate<T> condition) {
            this.index = index;
            this.variants = variants;
            this.variantCount = variants.length;
            this.cumulativeWeights = cumulativeWeights;
            this.totalWeight = cumulativeWeights == null ? 0 : cumulativeWeights[cumulativeWeights.length - 1];
            this.condition = condition == null ? _ -> true : condition;
        }

        public int index() {
            return this.index;
        }

        public int size() {
            return this.variantCount;
        }

        public int variant(int ordinal) {
            return this.variants[ordinal];
        }

        private static <T> Result<Rule<T>> parse(Props props, String key, int index, Predicate<T> condition) {
            String spec = props.get(key + "." + index);
            Result<NumberList> variants = NumberList.parse(spec);
            if (!variants.isSuccess()) {
                return Result.failure("Invalid " + key + "." + index + ": " + variants.error());
            }
            if (variants.value().size() > MAX_VARIANTS) {
                return Result.failure("Invalid " + key + "." + index + ": selects " + variants.value().size() + " variants");
            }
            int[] list;
            try {
                list = expand(spec);
            } catch (IllegalArgumentException e) {
                return Result.failure("Invalid " + key + "." + index + ": " + e.getMessage());
            }
            int count = list.length;
            if (count == 0) {
                return Result.failure("Invalid " + key + "." + index + ": selects no variants");
            }

            int[] weights;
            try {
                weights = parseWeights(props.get("weights." + index));
            } catch (IllegalArgumentException e) {
                return Result.failure("Invalid weights." + index + ": " + e.getMessage());
            }
            if (weights == null) {
                return Result.success(new Rule<>(index, list, null, condition));
            }

            int shared = Math.min(weights.length, count);
            int[] adjusted = new int[count];
            System.arraycopy(weights, 0, adjusted, 0, shared);
            for (int i = shared; i < adjusted.length; i++) {
                adjusted[i] = average(weights);
            }

            int[] cumulative = new int[adjusted.length];
            int total = 0;
            for (int i = 0; i < adjusted.length; i++) {
                total += adjusted[i];
                cumulative[i] = total;
            }
            if (total <= 0) {
                return Result.failure("Invalid weights." + index + ": sum of weights is " + total);
            }
            return Result.success(new Rule<>(index, list, cumulative, condition));
        }

        private static int[] expand(String spec) {
            IntArrayList variants = new IntArrayList();
            for (String token : spec.trim().split("[\\s,]+")) {
                if (token.isEmpty()) continue;
                int dash = token.indexOf('-');
                if (dash > 0) {
                    int start = Integer.parseInt(token.substring(0, dash));
                    int end = Integer.parseInt(token.substring(dash + 1));
                    if (start > end) throw new IllegalArgumentException("empty range " + token);
                    for (int variant = start; variant <= end; variant++) variants.add(variant);
                } else {
                    variants.add(Integer.parseInt(token));
                }
            }
            return variants.toIntArray();
        }

        /** Returns the parsed weights, or null if the property is absent. */
        private static int[] parseWeights(String spec) {
            if (spec == null || spec.isBlank()) return null;
            String[] tokens = spec.trim().split("\\s+");
            int[] values = new int[tokens.length];
            for (int i = 0; i < tokens.length; i++) {
                values[i] = Integer.parseInt(tokens[i]);
                if (values[i] < 0) throw new IllegalArgumentException("negative weight " + values[i]);
            }
            return values;
        }

        private int pick(int seed) {
            int ordinal = this.cumulativeWeights == null
                    ? Math.floorMod(seed, this.variantCount)
                    : pickWeighted(Math.floorMod(seed, this.totalWeight));
            return variant(ordinal);
        }

        private int pickWeighted(int roll) {
            int i = 0;
            while (this.cumulativeWeights[i] <= roll) i++;
            return i;
        }

        private static int average(int[] values) {
            int total = 0;
            for (int value : values) total += value;
            return total / values.length;
        }
    }
}
