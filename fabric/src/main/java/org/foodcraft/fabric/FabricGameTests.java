package org.foodcraft.fabric;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import org.foodcraft.test.FoodCraftGameTests;

public final class FabricGameTests implements FabricGameTest {
    @GameTest(template="foodcraft:empty",timeoutTicks=600) public void auditHeat(GameTestHelper h){org.foodcraft.test.AuditGameTests.heatFailures(h);}
    @GameTest(template="foodcraft:empty") public void auditTools(GameTestHelper h){org.foodcraft.test.AuditGameTests.specialTools(h);}
    @GameTest(template="foodcraft:empty",timeoutTicks=600) public void auditLoot(GameTestHelper h){org.foodcraft.test.AuditGameTests.acquisitionLoot(h);}
    @GameTest(template="foodcraft:empty") public void auditData(GameTestHelper h){org.foodcraft.test.AuditGameTests.malformedRecipes(h);}
    @GameTest(template="foodcraft:empty",timeoutTicks=600) public void auditComplexTransfers(GameTestHelper h){org.foodcraft.test.AuditGameTests.complexTransfers(h);}
    @GameTest(template="foodcraft:empty") public void auditDestruction(GameTestHelper h){org.foodcraft.test.AuditGameTests.destruction(h);}
    @GameTest(template="foodcraft:empty",timeoutTicks=300) public void auditPistons(GameTestHelper h){org.foodcraft.test.AuditGameTests.nativePistons(h);}
    @GameTest(template="foodcraft:empty") public void auditCake(GameTestHelper h){org.foodcraft.test.AuditGameTests.cakeEdges(h);}
    @GameTest(template="foodcraft:empty",timeoutTicks=600) public void auditNbt(GameTestHelper h){org.foodcraft.test.AuditGameTests.malformedPersistence(h);}
    @GameTest(template="foodcraft:empty") public void auditNumeric(GameTestHelper h){org.foodcraft.test.AuditGameTests.numericProcessing(h);}
    @GameTest(template="foodcraft:empty") public void fractionalBoundaries(GameTestHelper helper){org.foodcraft.test.RoundThreeGameTests.fractionalBoundaries(helper);}
    @GameTest(template="foodcraft:empty") public void sheepLoot(GameTestHelper helper){org.foodcraft.test.RoundThreeGameTests.sheepLoot(helper);}
    @GameTest(template="foodcraft:empty") public void fractionalFluid(GameTestHelper helper){
        var level=helper.getLevel();var pos=helper.absolutePos(new net.minecraft.core.BlockPos(3,2,3));
        level.setBlock(pos,org.foodcraft.FoodCraft.BLOCKS.get("pressure_cooker").defaultBlockState(),3);
        var machine=(org.foodcraft.machine.MachineBlockEntity)level.getBlockEntity(pos);
        var tank=net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage.SIDED.find(level,pos,net.minecraft.core.Direction.NORTH);
        var water=net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant.of(net.minecraft.world.level.material.Fluids.WATER);
        try(var transaction=net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()){
            helper.assertTrue(tank.insert(water,1,transaction)==1,"Single droplet transfer refused");
        }
        helper.assertTrue(machine.liquidVolume()==0,"Fractional fluid rollback mutated volume");
        for(int i=0;i<20;i++)try(var transaction=net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()){
            helper.assertTrue(tank.insert(water,4050,transaction)==4050,"Small Fabric fluid transfer refused");transaction.commit();
        }
        long stored=0;for(var view:tank)stored+=view.getAmount();
        helper.assertTrue(machine.liquid==1&&stored==81000,"Small transfers did not form exactly one legacy unit");
        try(var transaction=net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()){
            helper.assertTrue(tank.insert(water,80999,transaction)==80999,"Fractional tank insert failed");transaction.commit();
        }
        machine.load(machine.saveWithoutMetadata());helper.assertTrue(machine.liquidVolume()==161999,"Fractional water was lost on save/load");
        for(int i=0;i<18;i++){
            var packet=new net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket(1,i,machine.data().get(i));
            var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());packet.write(buffer);
            var decoded=new net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket(buffer);
            if(i==16||i==17)machine.data().set(i,decoded.getValue());buffer.release();
        }
        helper.assertTrue(machine.liquidFraction==80999,"Fractional water was truncated by menu packets");
        System.out.println("FOODCRAFT ACCEPTANCE SMALL FLUID PASS loader=fabric rollback=true single_droplet=true save_load=true menu_protocol=true");helper.succeed();
    }
    @GameTest(template="foodcraft:empty",timeoutTicks=600) public void inventoryMatrix(GameTestHelper helper){org.foodcraft.test.InventoryMatrixGameTests.capacities(helper);}
    @GameTest(template="foodcraft:empty") public void recipeChangeDetails(GameTestHelper helper){org.foodcraft.test.DetailGameTests.changedRecipeProgress(helper);}
    @GameTest(template="foodcraft:empty",timeoutTicks=500) public void recipeTransferDetails(GameTestHelper helper){org.foodcraft.test.DetailGameTests.recipeTransfers(helper);}
    @GameTest(template="foodcraft:empty") public void heatProtocol(GameTestHelper helper){org.foodcraft.test.DetailGameTests.heatButtonProtocol(helper);}
    @GameTest(template="foodcraft:empty") public void seedHeight(GameTestHelper helper){org.foodcraft.test.DetailGameTests.seedBuildHeight(helper);}
    @GameTest(template="foodcraft:empty",timeoutTicks=150) public void agricultureDetails(GameTestHelper helper){org.foodcraft.test.DetailGameTests.agriculture(helper);}
    @GameTest(template="foodcraft:empty") public void containersDetails(GameTestHelper helper){org.foodcraft.test.DetailGameTests.liquidContainers(helper);}
    @GameTest(template="foodcraft:empty") public void edibleDetails(GameTestHelper helper){org.foodcraft.test.DetailGameTests.edibleInteractions(helper);}
    @GameTest(template="foodcraft:empty",timeoutTicks=1500) public void hopperDetails(GameTestHelper helper){org.foodcraft.test.DetailGameTests.hopperAutomation(helper);}
    @GameTest(template="foodcraft:empty") public void retainedStorage(GameTestHelper helper){
        var level=helper.getLevel();var pos=helper.absolutePos(new net.minecraft.core.BlockPos(3,2,3));
        level.setBlock(pos,org.foodcraft.FoodCraft.BLOCKS.get("pressure_cooker").defaultBlockState(),3);
        var top=net.fabricmc.fabric.api.transfer.v1.item.ItemStorage.SIDED.find(level,pos,net.minecraft.core.Direction.UP);
        var food=net.fabricmc.fabric.api.transfer.v1.item.ItemVariant.of(org.foodcraft.FoodCraft.item("fan"));
        level.removeBlock(pos,false);
        try(var transaction=net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()){
            helper.assertTrue(top.insert(food,12,transaction)==0,"Removed Fabric machine retained a writable item storage");transaction.commit();
        }
        System.out.println("FOODCRAFT DETAIL FABRIC STORAGE INVALIDATION PASS");helper.succeed();
    }
    @GameTest(template="foodcraft:empty",timeoutTicks=37000) public void automation(GameTestHelper helper){FoodCraftGameTests.automationSoak(helper);}
    @GameTest(template="foodcraft:empty",timeoutTicks=600) public void registry(GameTestHelper helper){FoodCraftGameTests.registry(helper);}
    @GameTest(template="foodcraft:empty",timeoutTicks=600) public void recipes(GameTestHelper helper){FoodCraftGameTests.everyMachineRecipe(helper);}
    @GameTest(template="foodcraft:empty") public void cuttingBoard(GameTestHelper helper){FoodCraftGameTests.cuttingBoardBoundaries(helper);}
    @GameTest(template="foodcraft:empty") public void persistence(GameTestHelper helper){FoodCraftGameTests.waterAndPersistence(helper);}
    @GameTest(template="foodcraft:empty") public void menus(GameTestHelper helper){FoodCraftGameTests.menuLayouts(helper);}
    @GameTest(template="foodcraft:empty",timeoutTicks=600) public void crafting(GameTestHelper helper){FoodCraftGameTests.craftingRecipes(helper);}
    @GameTest(template="foodcraft:empty",timeoutTicks=600) public void plants(GameTestHelper helper){FoodCraftGameTests.plantsAndTrees(helper);}
    @GameTest(template="foodcraft:empty") public void foods(GameTestHelper helper){FoodCraftGameTests.foodProperties(helper);}
    @GameTest(template="foodcraft:empty") public void purifier(GameTestHelper helper){FoodCraftGameTests.waterPurifierAndEffects(helper);}
    @GameTest(template="foodcraft:empty") public void cooking(GameTestHelper helper){FoodCraftGameTests.cookingRecipes(helper);}
    @GameTest(template="foodcraft:empty") public void transfers(GameTestHelper helper){
        var pos=helper.absolutePos(new net.minecraft.core.BlockPos(3,2,3));var level=helper.getLevel();
        level.setBlock(pos,org.foodcraft.FoodCraft.BLOCKS.get("pressure_cooker").defaultBlockState(),3);
        var machine=(org.foodcraft.machine.MachineBlockEntity)level.getBlockEntity(pos);machine.clearContent();machine.liquid=0;machine.milk=false;
        var top=net.fabricmc.fabric.api.transfer.v1.item.ItemStorage.SIDED.find(level,pos,net.minecraft.core.Direction.UP);
        var food=net.fabricmc.fabric.api.transfer.v1.item.ItemVariant.of(org.foodcraft.FoodCraft.item("fan"));
        try(var transaction=net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()){
            helper.assertTrue(top.insert(food,12,transaction)==12,"Fabric insertion simulation refused food");
        }
        helper.assertTrue(machine.isEmpty(),"Aborted Fabric item transaction changed inventory");
        try(var transaction=net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()){helper.assertTrue(top.insert(food,12,transaction)==12,"Fabric insertion failed");transaction.commit();}
        helper.assertTrue(machine.getItem(0).getCount()==4&&machine.getItem(1).getCount()==4&&machine.getItem(2).getCount()==4,"Fabric bulk transfer monopolized one recipe slot");
        System.out.println("FOODCRAFT DETAIL FABRIC BULK ROUTING PASS accepted=12 inputs=[4,4,4]");
        var bottom=net.fabricmc.fabric.api.transfer.v1.item.ItemStorage.SIDED.find(level,pos,net.minecraft.core.Direction.DOWN);
        machine.setItem(5,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD,7));
        try(var transaction=net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()){helper.assertTrue(bottom.extract(net.fabricmc.fabric.api.transfer.v1.item.ItemVariant.of(net.minecraft.world.item.Items.BREAD),64,transaction)==7,"Fabric extraction failed");transaction.commit();}
        helper.assertTrue(machine.getItem(5).isEmpty(),"Fabric extraction duplicated output");
        var tank=net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage.SIDED.find(level,pos,net.minecraft.core.Direction.NORTH);
        var water=net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant.of(net.minecraft.world.level.material.Fluids.WATER);
        long bucket=net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants.BUCKET;
        try(var transaction=net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()){helper.assertTrue(tank.insert(water,10*bucket,transaction)==8*bucket,"Fabric capacity mismatch");}
        helper.assertTrue(machine.liquid==0,"Aborted Fabric fluid transaction changed water");
        try(var transaction=net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()){tank.insert(water,10*bucket,transaction);transaction.commit();}
        helper.assertTrue(machine.liquid==8,"Committed Fabric fluid transaction lost water");helper.succeed();
    }
}
