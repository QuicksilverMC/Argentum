package dev.rdh.cera.modules.random;

import dev.rdh.cera.props.RandomRules;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import net.minecraft.world.World;

public final class RandomChoices {
    private static final int CACHE_LIMIT = 4096;

    public record Choice(int variant, int rule) {
    }

    private final Object2ObjectOpenHashMap<Object, Reference2ObjectArrayMap<Object, Entry>> cache = new Object2ObjectOpenHashMap<>();

    public Choice choose(Subject subject, Object group, RandomRules<Subject> rules, int count) {
        Reference2ObjectArrayMap<Object, Entry> entries = this.cache.get(subject.key());
        Entry entry = entries == null ? null : entries.get(group);
        World world = subject.world();
        long now = world == null ? 0L : world.getTime();
        if (entry != null && (rules == null || now < entry.expiresAt())) return entry.choice();

        int seed = subject.seed();
        Choice choice;
        if (rules == null) {
            choice = new Choice(Math.floorMod(seed, count) + 1, 0);
        } else {
            RandomRules.Rule<Subject> rule = rules.match(subject);
            choice = rule == null ? new Choice(1, 0) : new Choice(rule.pick(seed), rule.index());
        }
        if (entries == null) this.cache.put(subject.key(), entries = new Reference2ObjectArrayMap<>(2));
        entries.put(group, new Entry(choice, rules == null ? Long.MAX_VALUE : now + 20));
        if (this.cache.size() > CACHE_LIMIT) this.cache.clear();
        return choice;
    }

    public void clear() {
        this.cache.clear();
    }

    private record Entry(Choice choice, long expiresAt) {
    }
}
