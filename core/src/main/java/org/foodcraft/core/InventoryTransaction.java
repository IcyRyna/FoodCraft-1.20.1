package org.foodcraft.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Side-effect-free planning: rejected operations leave every inventory slot untouched. */
public final class InventoryTransaction {
    private InventoryTransaction() {}

    public record Stack(String item, String data, int count, int limit) {
        public static final Stack EMPTY = new Stack("", "", 0, 64);
        public Stack {
            Objects.requireNonNull(item);
            Objects.requireNonNull(data);
            if (limit < 1 || count < 0 || count > limit || (count > 0 && item.isEmpty())) {
                throw new IllegalArgumentException("Invalid stack: " + item + " x " + count + "/" + limit);
            }
        }
        public boolean empty() { return count == 0; }
        public boolean compatible(Stack other) {
            return item.equals(other.item) && data.equals(other.data);
        }
        public Stack withCount(int amount) { return amount == 0 ? EMPTY : new Stack(item, data, amount, limit); }
    }

    public record Demand(int slot, String item, String data, int count) {
        public Demand {
            if (slot < 0 || count < 1) throw new IllegalArgumentException("Invalid demand");
            Objects.requireNonNull(item);
            Objects.requireNonNull(data);
        }
    }

    public record Addition(int slot, Stack stack) {
        public Addition {
            if (slot < 0 || stack.empty()) throw new IllegalArgumentException("Invalid addition");
        }
    }

    public record Plan(List<Stack> before, List<Stack> after) {
        public Plan { before = List.copyOf(before); after = List.copyOf(after); }
        public boolean stillValid(List<Stack> actual) { return before.equals(actual); }
    }

    public static Optional<Plan> prepare(List<Stack> inventory, List<Demand> demands,
                                         Set<Integer> exclusiveSlots, List<Addition> additions) {
        Objects.requireNonNull(inventory);
        List<Stack> before = List.copyOf(inventory);
        List<Stack> after = new ArrayList<>(before);
        Set<Integer> used = new HashSet<>();
        for (Demand demand : demands) {
            if (demand.slot() >= after.size() || !used.add(demand.slot())) return Optional.empty();
            Stack input = after.get(demand.slot());
            if (!input.item().equals(demand.item()) || !input.data().equals(demand.data())
                    || input.count() < demand.count()) return Optional.empty();
            after.set(demand.slot(), input.withCount(input.count() - demand.count()));
        }
        for (int slot : exclusiveSlots) {
            if (slot < 0 || slot >= before.size()) return Optional.empty();
            if (!used.contains(slot) && !before.get(slot).empty()) return Optional.empty();
        }
        for (Addition addition : additions) {
            if (addition.slot() >= after.size()) return Optional.empty();
            Stack output = after.get(addition.slot());
            Stack incoming = addition.stack();
            if (output.empty()) {
                after.set(addition.slot(), incoming);
            } else {
                if (!output.compatible(incoming)) return Optional.empty();
                long total = (long) output.count() + incoming.count();
                if (total > Math.min(output.limit(), incoming.limit())) return Optional.empty();
                after.set(addition.slot(), output.withCount((int) total));
            }
        }
        return Optional.of(new Plan(before, after));
    }

    /** Proficiency is recorded by legacy pots; the actual recipe heat range stays fixed. */
    public static boolean correctHeat(double heat, int minimum, int maximum, int proficiency) {
        if (!Double.isFinite(heat) || minimum < 0 || maximum < minimum) return false;
        return heat >= minimum && heat <= maximum;
    }
}
