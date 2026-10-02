package org.foodcraft.forge;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.foodcraft.FoodCraft;
import org.foodcraft.machine.MachineBlockEntity;

@Mod(FoodCraft.MOD_ID)
public final class FoodCraftForge {
    static final ForgeConfigSpec SPEC;
    static final ForgeConfigSpec.BooleanValue WRENCH;
    static {
        var builder=new ForgeConfigSpec.Builder();
        WRENCH=builder.comment("Use the wrench to preserve machines when dismantling.").define("wrench",true);
        SPEC=builder.build();
    }
    public FoodCraftForge(){
        var bus=FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER,SPEC);
        FoodCraft.initialize(new ForgePlatform(bus));
        ForgeNetworking.initialize();
        MinecraftForge.EVENT_BUS.addGenericListener(BlockEntity.class,FoodCraftForge::attach);
        MinecraftForge.EVENT_BUS.addListener(ForgeLoot::load);
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.RegisterCommandsEvent event)->org.foodcraft.test.ClientTestFixture.commands(event.getDispatcher()));
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.TickEvent.LevelTickEvent event)->{if(event.phase==net.minecraftforge.event.TickEvent.Phase.END&&event.level instanceof net.minecraft.server.level.ServerLevel level)org.foodcraft.test.PipelineVerification.tick(level);});
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event)->{if(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)org.foodcraft.test.ClientTestFixture.onJoin(player);});
    }
    private static void attach(AttachCapabilitiesEvent<BlockEntity> event){
        if(event.getObject() instanceof MachineBlockEntity machine){
            var provider=new ForgeMachineCapabilities(machine);
            event.addCapability(FoodCraft.id("machine"),provider);event.addListener(provider::invalidate);
        }
        if(Boolean.getBoolean("foodcraft.qa.server")&&event.getObject() instanceof net.minecraft.world.level.block.entity.ChestBlockEntity){
            var source=new ForgeQaWater(event.getObject());event.addCapability(FoodCraft.id("qa_water_source"),source);event.addListener(source::invalidate);
        }
    }
}
