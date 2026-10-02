package org.foodcraft.loot;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import org.foodcraft.FoodCraft;
import org.foodcraft.Catalog;
import java.util.List;

public final class FoodCraftLoot {
    private FoodCraftLoot(){}
    public static List<LootPool.Builder> pools(ResourceLocation table){
        if(!table.getNamespace().equals("minecraft"))return List.of();
        String path=table.getPath();
        if(path.equals("blocks/grass")||path.equals("blocks/tall_grass")){
            int total=Catalog.ENTRIES.stream().filter(entry->entry.kind().equals("seed")).mapToInt(entry->entry.path().equals("shucaizhong")?1:2).sum();
            // Forge 1.7.10's shared grass pool contains vanilla wheat seeds with weight 10.
            float chance=0.125F*total/(10+total);
            var pool=LootPool.lootPool().setRolls(ConstantValue.exactly(1)).when(LootItemRandomChanceCondition.randomChance(chance))
                    .when(net.minecraft.world.level.storage.loot.predicates.MatchTool.toolMatches(net.minecraft.advancements.critereon.ItemPredicate.Builder.item().of(net.minecraft.world.item.Items.SHEARS)).invert());
            for(Catalog.Entry entry:Catalog.ENTRIES)if(entry.kind().equals("seed"))pool.add(LootItem.lootTableItem(FoodCraft.item(entry.path())).setWeight(entry.path().equals("shucaizhong")?1:2));
            return List.of(pool);
        }
        if(path.startsWith("blocks/")&&path.endsWith("_leaves")&&!path.contains("azalea")){
            var pool=LootPool.lootPool().setRolls(ConstantValue.exactly(1)).when(LootItemRandomChanceCondition.randomChance(0.01F));
            for(Catalog.Entry entry:Catalog.ENTRIES)if(entry.kind().equals("sapling"))pool.add(LootItem.lootTableItem(FoodCraft.item(entry.path())).setWeight(1));
            return List.of(pool);
        }
        if(path.equals("entities/squid"))return List.of(LootPool.lootPool().setRolls(ConstantValue.exactly(1))
                .add(LootItem.lootTableItem(FoodCraft.item("youyurou")).apply(SetItemCountFunction.setCount(ConstantValue.exactly(3)))));
        if(List.of("chests/simple_dungeon","chests/abandoned_mineshaft","chests/stronghold_corridor","chests/stronghold_crossing","chests/stronghold_library","chests/jungle_temple").contains(path)){
            var pool=LootPool.lootPool().setRolls(ConstantValue.exactly(1));
            String[] names={"zongye","douban","galikuai","hetaosu","xiangchang","laweixunliao","kafei"};
            int[] weights={16,16,16,16,16,32,10};
            for(int i=0;i<names.length;i++)pool.add(LootItem.lootTableItem(FoodCraft.item(names[i])).setWeight(weights[i])
                    .apply(SetItemCountFunction.setCount(names[i].equals("kafei")||names[i].equals("douban")?ConstantValue.exactly(1):UniformGenerator.between(1,10))));
            return List.of(pool);
        }
        return List.of();
    }
}
