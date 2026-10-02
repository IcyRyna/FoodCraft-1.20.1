package org.foodcraft.forge;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.foodcraft.test.FoodCraftGameTests;

@GameTestHolder("foodcraft")
@PrefixGameTestTemplate(false)
public final class ForgeGameTests {
    @GameTest(template="empty",timeoutTicks=600) public static void auditHeat(GameTestHelper h){org.foodcraft.test.AuditGameTests.heatFailures(h);}
    @GameTest(template="empty") public static void auditTools(GameTestHelper h){org.foodcraft.test.AuditGameTests.specialTools(h);}
    @GameTest(template="empty",timeoutTicks=600) public static void auditLoot(GameTestHelper h){org.foodcraft.test.AuditGameTests.acquisitionLoot(h);}
    @GameTest(template="empty") public static void auditData(GameTestHelper h){org.foodcraft.test.AuditGameTests.malformedRecipes(h);}
    @GameTest(template="empty",timeoutTicks=600) public static void auditComplexTransfers(GameTestHelper h){org.foodcraft.test.AuditGameTests.complexTransfers(h);}
    @GameTest(template="empty") public static void auditDestruction(GameTestHelper h){org.foodcraft.test.AuditGameTests.destruction(h);}
    @GameTest(template="empty",timeoutTicks=300) public static void auditPistons(GameTestHelper h){org.foodcraft.test.AuditGameTests.nativePistons(h);}
    @GameTest(template="empty") public static void auditCake(GameTestHelper h){org.foodcraft.test.AuditGameTests.cakeEdges(h);}
    @GameTest(template="empty",timeoutTicks=600) public static void auditNbt(GameTestHelper h){org.foodcraft.test.AuditGameTests.malformedPersistence(h);}
    @GameTest(template="empty") public static void auditNumeric(GameTestHelper h){org.foodcraft.test.AuditGameTests.numericProcessing(h);}
    @GameTest(template="empty") public static void fractionalBoundaries(GameTestHelper helper){org.foodcraft.test.RoundThreeGameTests.fractionalBoundaries(helper);}
    @GameTest(template="empty") public static void sheepLoot(GameTestHelper helper){org.foodcraft.test.RoundThreeGameTests.sheepLoot(helper);}
    @GameTest(template="empty") public static void fractionalFluid(GameTestHelper helper){
        var level=helper.getLevel();var pos=helper.absolutePos(new net.minecraft.core.BlockPos(3,2,3));
        level.setBlock(pos,org.foodcraft.FoodCraft.BLOCKS.get("pressure_cooker").defaultBlockState(),3);
        var machine=(org.foodcraft.machine.MachineBlockEntity)level.getBlockEntity(pos);
        var tank=machine.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER,net.minecraft.core.Direction.NORTH).orElseThrow(()->new IllegalStateException("Missing fluid port"));
        var portion=new net.minecraftforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER,50);
        helper.assertTrue(tank.fill(portion,net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE)==50,"Basic Pipez 50 mB transfer is rejected");
        helper.assertTrue(tank.getFluidInTank(0).isEmpty(),"50 mB simulation mutated water");
        for(int i=0;i<20;i++)helper.assertTrue(tank.fill(portion,net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE)==50,"Small fluid transfer refused");
        helper.assertTrue(machine.liquid==1&&tank.getFluidInTank(0).getAmount()==1000,"Twenty small transfers failed to form exactly one legacy unit");
        helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=600) public static void inventoryMatrix(GameTestHelper helper){org.foodcraft.test.InventoryMatrixGameTests.capacities(helper);}
    @GameTest(template="empty") public static void recipeChangeDetails(GameTestHelper helper){org.foodcraft.test.DetailGameTests.changedRecipeProgress(helper);}
    @GameTest(template="empty",timeoutTicks=500) public static void recipeTransferDetails(GameTestHelper helper){org.foodcraft.test.DetailGameTests.recipeTransfers(helper);}
    @GameTest(template="empty") public static void heatProtocol(GameTestHelper helper){org.foodcraft.test.DetailGameTests.heatButtonProtocol(helper);}
    @GameTest(template="empty") public static void seedHeight(GameTestHelper helper){org.foodcraft.test.DetailGameTests.seedBuildHeight(helper);}
    @GameTest(template="empty",timeoutTicks=150) public static void agricultureDetails(GameTestHelper helper){org.foodcraft.test.DetailGameTests.agriculture(helper);}
    @GameTest(template="empty") public static void containersDetails(GameTestHelper helper){org.foodcraft.test.DetailGameTests.liquidContainers(helper);}
    @GameTest(template="empty") public static void edibleDetails(GameTestHelper helper){org.foodcraft.test.DetailGameTests.edibleInteractions(helper);}
    @GameTest(template="empty",timeoutTicks=1500) public static void hopperDetails(GameTestHelper helper){org.foodcraft.test.DetailGameTests.hopperAutomation(helper);}
    @net.minecraft.gametest.framework.GameTestGenerator
    public static java.util.Collection<net.minecraft.gametest.framework.TestFunction> soak(){
        return Boolean.getBoolean("foodcraft.soak")?java.util.List.of(new net.minecraft.gametest.framework.TestFunction("soak","foodcraft.automation_soak","foodcraft:empty",37000,0L,true,FoodCraftGameTests::automationSoak)):java.util.List.of();
    }
    @GameTest(template="empty",timeoutTicks=600) public static void registry(GameTestHelper helper){FoodCraftGameTests.registry(helper);}
    @GameTest(template="empty",timeoutTicks=600) public static void recipes(GameTestHelper helper){FoodCraftGameTests.everyMachineRecipe(helper);}
    @GameTest(template="empty") public static void cuttingBoard(GameTestHelper helper){FoodCraftGameTests.cuttingBoardBoundaries(helper);}
    @GameTest(template="empty") public static void persistence(GameTestHelper helper){FoodCraftGameTests.waterAndPersistence(helper);}
    @GameTest(template="empty") public static void menus(GameTestHelper helper){FoodCraftGameTests.menuLayouts(helper);}
    @GameTest(template="empty",timeoutTicks=600) public static void crafting(GameTestHelper helper){FoodCraftGameTests.craftingRecipes(helper);}
    @GameTest(template="empty",timeoutTicks=600) public static void plants(GameTestHelper helper){FoodCraftGameTests.plantsAndTrees(helper);}
    @GameTest(template="empty") public static void foods(GameTestHelper helper){FoodCraftGameTests.foodProperties(helper);}
    @GameTest(template="empty") public static void purifier(GameTestHelper helper){FoodCraftGameTests.waterPurifierAndEffects(helper);}
    @GameTest(template="empty") public static void cooking(GameTestHelper helper){FoodCraftGameTests.cookingRecipes(helper);}
    @GameTest(template="empty") public static void transfers(GameTestHelper helper){
        var pos=helper.absolutePos(new net.minecraft.core.BlockPos(3,2,3));var level=helper.getLevel();
        level.setBlock(pos,org.foodcraft.FoodCraft.BLOCKS.get("pressure_cooker").defaultBlockState(),3);
        var machine=(org.foodcraft.machine.MachineBlockEntity)level.getBlockEntity(pos);machine.clearContent();machine.liquid=0;machine.milk=false;
        var top=machine.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER,net.minecraft.core.Direction.UP).orElseThrow(()->new IllegalStateException("Missing top item capability"));
        var food=new net.minecraft.world.item.ItemStack(org.foodcraft.FoodCraft.item("fan"),12);
        helper.assertTrue(net.minecraftforge.items.ItemHandlerHelper.insertItem(top,food,true).getCount()==9&&machine.isEmpty(),"Forge insertion simulation mutated inventory or ignored repeated-input balance");
        helper.assertTrue(net.minecraftforge.items.ItemHandlerHelper.insertItem(top,food,false).getCount()==9&&machine.getItem(0).getCount()==1&&machine.getItem(1).getCount()==1&&machine.getItem(2).getCount()==1,"Forge bulk transfer monopolized one recipe slot");
        System.out.println("FOODCRAFT DETAIL FORGE BULK ROUTING PASS accepted=3 returned=9 inputs=[1,1,1]");
        var bottom=machine.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER,net.minecraft.core.Direction.DOWN).orElseThrow(()->new IllegalStateException("Missing bottom port"));
        machine.setItem(5,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD,7));
        helper.assertTrue(bottom.extractItem(5,64,true).getCount()==7&&machine.getItem(5).getCount()==7,"Forge extraction simulation mutated output");
        helper.assertTrue(bottom.extractItem(5,64,false).getCount()==7&&machine.getItem(5).isEmpty(),"Forge full-stack extraction lost items");
        helper.assertTrue(bottom.insertItem(0,food,false).getCount()==12,"Bottom port accepted ingredients");
        var tank=machine.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER,net.minecraft.core.Direction.NORTH).orElseThrow(()->new IllegalStateException("Missing tank"));
        var water=new net.minecraftforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER,10000);
        helper.assertTrue(tank.fill(water,net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE)==8000&&machine.liquid==0,"Forge fluid simulation mutated tank");
        helper.assertTrue(tank.fill(water,net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE)==8000&&machine.liquid==8,"Forge fluid capacity mismatch");
        var handle=machine.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER,net.minecraft.core.Direction.UP);
        level.removeBlock(pos,false);helper.assertTrue(!handle.isPresent(),"Removed Forge machine capability still accessible");helper.succeed();
    }
}
