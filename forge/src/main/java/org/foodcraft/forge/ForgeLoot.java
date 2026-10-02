package org.foodcraft.forge;
import net.minecraftforge.event.LootTableLoadEvent;
final class ForgeLoot {
    private ForgeLoot(){}
    static void load(LootTableLoadEvent event){org.foodcraft.loot.FoodCraftLoot.pools(event.getName()).forEach(pool->event.getTable().addPool(pool.build()));}
}
