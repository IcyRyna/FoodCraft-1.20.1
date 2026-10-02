package org.foodcraft.test;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.foodcraft.FoodCraft;
import org.foodcraft.machine.MachineBlockEntity;
import org.foodcraft.machine.MachineKind;
import org.foodcraft.recipe.MachineRecipe;
import java.util.Comparator;

/** Exhaust every output count and distinct matching/NBT class at the actual server commit boundary. */
public final class InventoryMatrixGameTests {
    private InventoryMatrixGameTests() {}
    public static void capacities(GameTestHelper helper) {
        int cases=0,recipes=0;
        for(MachineKind kind:MachineKind.values()) {
            if(kind==MachineKind.STOVE)continue;
            var position=helper.absolutePos(new BlockPos(3,3,3));
            helper.getLevel().setBlock(position,FoodCraft.BLOCKS.get(kind.id).defaultBlockState(),3);
            var machine=(MachineBlockEntity)helper.getLevel().getBlockEntity(position);
            if(kind.heatedExternally()) {
                helper.getLevel().setBlock(position.below(),FoodCraft.BLOCKS.get("stove").defaultBlockState(),3);
                ((MachineBlockEntity)helper.getLevel().getBlockEntity(position.below())).burnTime=1000000;
            }
            var entries=helper.getLevel().getRecipeManager().getAllRecipesFor(FoodCraft.RECIPE_TYPES.get(kind)).stream()
                .sorted(Comparator.comparing(recipe->recipe.getId().toString())).toList();
            for(var recipe:entries) {
                recipes++;
                for(boolean golden:new boolean[]{false,true}) {
                    if(golden&&kind!=MachineKind.CUTTING_BOARD)continue;
                    int yield=recipe.result.getCount()+(golden?1:0);
                    int limit=recipe.result.getMaxStackSize();
                    for(int count=0;count<=limit;count++)for(int compatibility=0;compatibility<3;compatibility++) {
                        prepare(machine,recipe,golden);
                        ItemStack output=count==0?ItemStack.EMPTY:compatibility==1?new ItemStack(Items.BARRIER,count):recipe.result.copyWithCount(count);
                        if(count>0&&compatibility==2)output.getOrCreateTag().putString("FoodCraftDifferentOutput","capacity-matrix");
                        machine.setItem(kind.outputs[0],output);
                        var before=machine.saveWithoutMetadata();
                        tick(machine);
                        boolean success=(count==0||compatibility==0)&&count+yield<=limit;
                        var actual=machine.getItem(kind.outputs[0]);
                        String label=recipe.getId()+" / output="+count+" / nbt="+compatibility+" / golden="+golden;
                        if(success) {
                            helper.assertTrue(actual.is(recipe.result.getItem())&&actual.getCount()==count+yield,"Native output capacity mismatch: "+label);
                            for(var input:recipe.inputs) {
                                var remainder=input.ingredient().getItems()[0].getItem().getCraftingRemainingItem();
                                ItemStack remaining=machine.getItem(input.slot());
                                helper.assertTrue(remainder==null?remaining.isEmpty():remaining.is(remainder)&&remaining.getCount()==input.count(),"Native consumed input or container return mismatch: "+label);
                            }
                            helper.assertTrue(machine.liquid==8-recipe.water,"Native liquid conservation mismatch: "+label);
                            if(kind==MachineKind.CUTTING_BOARD)helper.assertTrue(machine.getItem(0).getDamageValue()==1,"Native knife consumption mismatch: "+label);
                        } else {
                            helper.assertTrue(ItemStack.matches(output,actual),"Rejected operation changed output: "+label);
                            for(var input:recipe.inputs)helper.assertTrue(machine.getItem(input.slot()).getCount()==input.count(),"Rejected operation consumed input: "+label);
                            helper.assertTrue(machine.liquid==8,"Rejected operation consumed liquid: "+label);
                            if(kind==MachineKind.CUTTING_BOARD)helper.assertTrue(machine.getItem(0).getDamageValue()==0,"Rejected operation damaged knife: "+label);
                            helper.assertTrue(before.getInt("Progress")==machine.progress,"Output blockage lost batch progress: "+label);
                        }
                        cases++;
                    }
                }
                for(var missing:recipe.inputs) {
                    prepare(machine,recipe,false);
                    machine.setItem(missing.slot(),missing.count()==1?ItemStack.EMPTY:missing.ingredient().getItems()[0].copyWithCount(missing.count()-1));
                    var before=machine.saveWithoutMetadata();
                    var manager=helper.getLevel().getRecipeManager();var all=java.util.List.copyOf(manager.getRecipes());
                    // Removing a chicken slot can legitimately select a different cutting-board recipe.
                    // Isolate this quantity boundary so another valid recipe cannot replace the target.
                    try{manager.replaceRecipes(java.util.List.of(recipe));tick(machine);}finally{manager.replaceRecipes(all);}
                    helper.assertTrue(machine.getItem(kind.outputs[0]).isEmpty()&&before.getList("Items",10).equals(machine.saveWithoutMetadata().getList("Items",10)),"Insufficient quantity mutated inventory: "+recipe.getId());
                    cases++;
                }
            }
        }
        System.out.println("FOODCRAFT ACCEPTANCE NATIVE INVENTORY MATRIX PASS recipes="+recipes+" cases="+cases+" all_output_counts=true output_nbt_classes=3 golden_knife=true missing_quantity=true");
        helper.succeed();
    }
    private static void prepare(MachineBlockEntity machine,MachineRecipe recipe,boolean golden) {
        machine.clearContent();
        for(var input:recipe.inputs)machine.setItem(input.slot(),input.ingredient().getItems()[0].copyWithCount(input.count()));
        if(machine.kind==MachineKind.CUTTING_BOARD)machine.setItem(0,new ItemStack(FoodCraft.item(golden?"caidao_hj":"caidao")));
        CompoundTag tag=machine.saveWithoutMetadata();
        tag.putInt("Progress",recipe.time-1);tag.putInt("TotalTime",recipe.time);tag.putInt("BurnTime",1000000);tag.putInt("ColdTime",1000000);
        tag.putInt("Liquid",8);tag.putBoolean("Milk",recipe.milk);tag.putInt("FirePower",0);
        tag.putInt("AccumulatedHeat",recipe.minHeat+(int)(((long)recipe.maxHeat-recipe.minHeat)/2));
        tag.putString("ActiveRecipe",recipe.getId().toString());tag.putString("ActiveRecipeFingerprint",recipe.fingerprint);
        machine.load(tag);
    }
    private static void tick(MachineBlockEntity machine) {
        MachineBlockEntity.tick(machine.getLevel(),machine.getBlockPos(),machine.getBlockState(),machine);
    }
}
