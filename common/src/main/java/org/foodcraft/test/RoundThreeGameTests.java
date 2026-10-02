package org.foodcraft.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import org.foodcraft.FoodCraft;
import org.foodcraft.machine.*;

public final class RoundThreeGameTests {
    private RoundThreeGameTests(){}
    public static void fractionalBoundaries(GameTestHelper helper){
        var level=helper.getLevel();var pos=helper.absolutePos(new BlockPos(3,2,3));level.setBlock(pos,FoodCraft.BLOCKS.get("drink_maker").defaultBlockState(),3);var machine=(MachineBlockEntity)level.getBlockEntity(pos);
        for(int fraction=0;fraction<81000;fraction++){
            machine.liquidFraction=fraction;
            for(int index=16;index<18;index++){
                int signed=(short)machine.data().get(index);machine.data().set(index,signed);
            }
            helper.assertTrue(machine.liquidFraction==fraction,"Fractional menu short roundtrip failed: "+fraction);
        }
        machine.liquid=7;machine.liquidFraction=80999;
        helper.assertTrue(machine.fillWater(1,true)==0&&machine.fillWaterVolume(Long.MAX_VALUE,true)==1,"Partial-tank capacity simulation overflowed");
        helper.assertTrue(machine.fillWaterVolume(1,false)==1&&machine.liquidVolume()==648000,"Final droplet capacity failed");
        helper.assertTrue(machine.fillWaterVolume(1,false)==0,"Full tank accepted excess water");
        var tag=machine.saveWithoutMetadata();tag.remove("LiquidFraction");tag.putInt("DataVersion",1);tag.putInt("Liquid",3);machine.load(tag);
        helper.assertTrue(machine.liquidVolume()==243000,"Old whole-unit save did not load");
        machine.liquid=1;machine.milk=true;helper.assertTrue(machine.fillWaterVolume(81000,false)==0,"Water mixed into milk");
        machine.liquid=7;machine.liquidFraction=1;machine.milk=false;machine.setItem(0,new ItemStack(Items.WATER_BUCKET));MachineBlockEntity.tick(level,pos,machine.getBlockState(),machine);
        helper.assertTrue(machine.getItem(0).is(Items.WATER_BUCKET)&&machine.liquidVolume()==567001,"Container overflow consumed a bucket");
        for(var kind:java.util.List.of(MachineKind.POT,MachineKind.FRYING_PAN)){
            level.setBlock(pos,FoodCraft.BLOCKS.get(kind.id).defaultBlockState(),3);machine=(MachineBlockEntity)level.getBlockEntity(pos);
            helper.assertTrue(java.util.Arrays.equals(machine.getSlotsForFace(Direction.NORTH),kind.outputs),"Heated machine side output missing");
            machine.setItem(kind.outputs[0],new ItemStack(Items.BREAD));helper.assertTrue(machine.canTakeItemThroughFace(kind.outputs[0],machine.getItem(kind.outputs[0]),Direction.NORTH),"Heated side refused its product");
            helper.assertTrue(!machine.canPlaceItemThroughFace(kind.outputs[0],new ItemStack(Items.BREAD),Direction.NORTH),"Heated output accepted ingredients");
        }
        System.out.println("FOODCRAFT ROUND3 FRACTION BOUNDARIES PASS short_values=81000 old_save=true milk=true overflow=true heated_side=true");helper.succeed();
    }
    public static void sheepLoot(GameTestHelper helper){
        var level=helper.getLevel();var pos=helper.absolutePos(new BlockPos(3,2,3));
        var sheep=EntityType.SHEEP.create(level);sheep.setPos(pos.getX()+0.5,pos.getY(),pos.getZ()+0.5);level.addFreshEntity(sheep);sheep.hurt(level.damageSources().generic(),Float.MAX_VALUE);
        helper.runAfterDelay(3,()->{
            int raw=0,duplicate=0;
            for(var entity:level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(3)))if(entity.getItem().is(Items.MUTTON))raw+=entity.getItem().getCount();else if(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(entity.getItem().getItem()).getNamespace().equals("foodcraft"))duplicate+=entity.getItem().getCount();
            helper.assertTrue(raw>=1&&raw<=2&&duplicate==0,"Native sheep death produced extra FoodCraft meat");
            System.out.println("FOODCRAFT ROUND3 NATIVE SHEEP LOOT PASS vanilla_mutton="+raw+" extra_foodcraft=0");helper.succeed();
        });
    }
}
