package org.foodcraft.machine;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.foodcraft.recipe.MachineRecipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Quantity-aware recipe transfer, planned without mutation and committed only by the server. */
public final class MachineTransfers {
    public static final ResourceLocation CHANNEL = new ResourceLocation("foodcraft", "recipe_transfer");
    private static Consumer<Request> clientSender;
    private MachineTransfers() {}

    public record Request(int menuId, ResourceLocation recipeId, boolean maximum) {
        public Request {
            if (menuId < 0 || menuId > 100) throw new IllegalArgumentException("Invalid menu id");
            Objects.requireNonNull(recipeId);
        }
        public void write(FriendlyByteBuf buffer) {
            buffer.writeVarInt(menuId); buffer.writeResourceLocation(recipeId); buffer.writeBoolean(maximum);
        }
        public static Request read(FriendlyByteBuf buffer) {
            var request=new Request(buffer.readVarInt(), buffer.readResourceLocation(), buffer.readBoolean());
            if(buffer.isReadable())throw new io.netty.handler.codec.DecoderException("Trailing FoodCraft recipe-transfer data");
            return request;
        }
    }

    public enum Failure { MISSING_INGREDIENTS, INVENTORY_FULL }

    public record Preparation(Plan plan, Failure failure) {
        public boolean successful() { return plan != null; }
    }

    public static final class Plan {
        private final List<ItemStack> before, after;
        public final int batches;
        private Plan(List<ItemStack> before, List<ItemStack> after, int batches) {
            this.before = copy(before); this.after = copy(after); this.batches = batches;
        }
        public boolean stillValid(MachineMenu menu) { return same(before, snapshot(menu)); }
        public boolean commit(MachineMenu menu) {
            if (!(menu.inventory instanceof MachineBlockEntity machine)||machine.getLevel()==null||machine.getLevel().isClientSide||machine.isRemoved()||!stillValid(menu)) return false;
            for (int slot : menu.kind.inputs) menu.slots.get(slot).set(after.get(slot).copy());
            for (int slot = menu.kind.size; slot < menu.slots.size(); slot++) menu.slots.get(slot).set(after.get(slot).copy());
            menu.broadcastChanges();
            return true;
        }
    }

    private static final class Group {
        final ItemStack template;
        int count;
        Group(ItemStack template) { this.template = template.copyWithCount(1); }
    }

    public static void setClientSender(Consumer<Request> sender) { clientSender = Objects.requireNonNull(sender); }
    public static void send(Request request) {
        if (clientSender == null) throw new IllegalStateException("Client recipe-transfer networking has not initialized");
        clientSender.accept(request);
    }

    public static boolean handle(ServerPlayer player, Request request) {
        if (player == null || player.isSpectator() || !(player.containerMenu instanceof MachineMenu menu)
                || menu.containerId != request.menuId() || !menu.stillValid(player)) return false;
        var loaded = player.level().getRecipeManager().byKey(request.recipeId()).orElse(null);
        if (!(loaded instanceof MachineRecipe recipe) || recipe.kind != menu.kind) return false;
        var preparation = prepare(menu, recipe, request.maximum());
        return preparation.successful() && preparation.plan().commit(menu);
    }

    public static Preparation prepare(MachineMenu menu, MachineRecipe recipe, boolean maximum) {
        if (recipe.kind != menu.kind) return new Preparation(null, Failure.MISSING_INGREDIENTS);
        List<ItemStack> before = snapshot(menu);
        List<Integer> sources = new ArrayList<>();
        for (int slot : menu.kind.inputs) sources.add(slot);
        for (int slot = menu.kind.size; slot < menu.slots.size(); slot++) sources.add(slot);
        List<Group> groups = new ArrayList<>();
        for (int slot : sources) {
            ItemStack stack = before.get(slot);
            if (stack.isEmpty()) continue;
            Group group = groups.stream().filter(candidate -> ItemStack.isSameItemSameTags(candidate.template, stack)).findFirst().orElse(null);
            if (group == null) { group = new Group(stack); groups.add(group); }
            group.count += stack.getCount();
        }
        int upper = maximum ? 64 : 1;
        for (var input : recipe.inputs) {
            int available = groups.stream().filter(group -> input.ingredient().test(group.template)).mapToInt(group -> group.count).sum();
            upper = Math.min(upper, available / input.count());
        }
        int[] masks=new int[groups.size()];
        for(int group=0;group<groups.size();group++)for(int input=0;input<recipe.inputs.size();input++)
            if(recipe.inputs.get(input).ingredient().test(groups.get(group).template))masks[group]|=1<<input;
        upper=Math.min(upper,org.foodcraft.core.RecipeAllocationBounds.maximumBatches(
            recipe.inputs.stream().mapToInt(MachineRecipe.Input::count).toArray(),
            groups.stream().mapToInt(group->group.count).toArray(),masks,
            groups.stream().mapToInt(group->group.template.getMaxStackSize()).toArray(),upper));
        if (upper == 0) return new Preparation(null, Failure.MISSING_INGREDIENTS);
        int[] budget = {20000};
        boolean[] completeIngredients = {false};
        for (int batches = upper; batches >= 1; batches--) {
            int[] remaining = groups.stream().mapToInt(group -> group.count).toArray();
            Integer[] order = new Integer[recipe.inputs.size()];
            for (int index = 0; index < order.length; index++) order[index] = index;
            Arrays.sort(order, Comparator.comparingLong(index -> groups.stream().filter(group -> recipe.inputs.get(index).ingredient().test(group.template)).count()));
            int[] chosen = new int[recipe.inputs.size()]; Arrays.fill(chosen, -1);
            Plan plan = search(menu, recipe, before, groups, sources, remaining, order, chosen, 0, batches, budget, completeIngredients);
            if (plan != null) return new Preparation(plan, null);
            if (budget[0] <= 0) break;
        }
        return new Preparation(null, completeIngredients[0] ? Failure.INVENTORY_FULL : Failure.MISSING_INGREDIENTS);
    }

    private static Plan search(MachineMenu menu, MachineRecipe recipe, List<ItemStack> before, List<Group> groups,
                               List<Integer> sources, int[] remaining, Integer[] order, int[] chosen, int depth,
                               int batches, int[] budget, boolean[] completeIngredients) {
        if (--budget[0] < 0) return null;
        if (depth == order.length) {
            completeIngredients[0] = true;
            return arrange(menu, recipe, before, groups, sources, chosen, batches);
        }
        int index = order[depth]; var input = recipe.inputs.get(index); int needed = input.count() * batches;
        for (int group = 0; group < groups.size(); group++) {
            var candidate = groups.get(group);
            if (remaining[group] < needed || needed > candidate.template.getMaxStackSize() || !input.ingredient().test(candidate.template)) continue;
            remaining[group] -= needed; chosen[index] = group;
            Plan result = search(menu, recipe, before, groups, sources, remaining, order, chosen, depth + 1, batches, budget, completeIngredients);
            remaining[group] += needed;
            if (result != null) return result;
        }
        chosen[index] = -1; return null;
    }

    private static Plan arrange(MachineMenu menu, MachineRecipe recipe, List<ItemStack> before, List<Group> groups,
                                List<Integer> sources, int[] chosen, int batches) {
        List<ItemStack> remaining = copy(before), after = copy(before);
        for (int slot : menu.kind.inputs) after.set(slot, ItemStack.EMPTY);
        for (int index = 0; index < recipe.inputs.size(); index++) {
            var input = recipe.inputs.get(index); var template = groups.get(chosen[index]).template;
            int needed = input.count() * batches, left = needed;
            for (int slot : sources) {
                var stack = remaining.get(slot);
                if (stack.isEmpty() || !ItemStack.isSameItemSameTags(stack, template)) continue;
                int used = Math.min(left, stack.getCount()); stack.shrink(used); left -= used;
                if (left == 0) break;
            }
            if (left != 0) throw new IllegalStateException("Recipe-transfer allocation exceeded its validated stock");
            after.set(input.slot(), template.copyWithCount(needed));
        }
        for (int slot = menu.kind.size; slot < menu.slots.size(); slot++) after.set(slot, remaining.get(slot).copy());
        for (int slot : menu.kind.inputs) {
            ItemStack leftover = remaining.get(slot).copy();
            if (!putAway(after, menu.kind.size, leftover)) return null;
        }
        return new Plan(before, after, batches);
    }

    private static boolean putAway(List<ItemStack> slots, int start, ItemStack incoming) {
        if (incoming.isEmpty()) return true;
        for (int slot = start; slot < slots.size(); slot++) {
            var target = slots.get(slot);
            if (!target.isEmpty() && ItemStack.isSameItemSameTags(target, incoming)) {
                int moved = Math.min(incoming.getCount(), target.getMaxStackSize() - target.getCount());
                target.grow(moved); incoming.shrink(moved);
                if (incoming.isEmpty()) return true;
            }
        }
        for (int slot = start; slot < slots.size(); slot++) if (slots.get(slot).isEmpty()) {
            int moved = Math.min(incoming.getCount(), incoming.getMaxStackSize());
            slots.set(slot, incoming.copyWithCount(moved)); incoming.shrink(moved);
            if (incoming.isEmpty()) return true;
        }
        return incoming.isEmpty();
    }

    private static List<ItemStack> snapshot(MachineMenu menu) { return menu.slots.stream().map(slot -> slot.getItem().copy()).toList(); }
    private static List<ItemStack> copy(List<ItemStack> slots) { return new ArrayList<>(slots.stream().map(ItemStack::copy).toList()); }
    private static boolean same(List<ItemStack> first, List<ItemStack> second) {
        if (first.size() != second.size()) return false;
        for (int slot = 0; slot < first.size(); slot++) if (!ItemStack.matches(first.get(slot), second.get(slot))) return false;
        return true;
    }
}
