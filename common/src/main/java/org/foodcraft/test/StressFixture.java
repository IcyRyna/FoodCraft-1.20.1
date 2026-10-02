package org.foodcraft.test;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.network.chat.Component;
import org.foodcraft.FoodCraft;
import org.foodcraft.machine.*;
import java.nio.file.Files;

/** One conserved, tagged allocation per player; opening a menu never refreshes it. */
public final class StressFixture {
    private static final String OWNER="FoodCraftStressOwner";
    private record Pending(java.util.UUID player,MachineKind kind,boolean full,boolean creative){}
    private static final java.util.Map<java.util.UUID,Pending> pending=new java.util.HashMap<>();
    private static final java.util.Set<java.util.UUID> cursorReported=new java.util.HashSet<>();
    private StressFixture(){}
    private static BlockPos pos(MachineKind kind){return new BlockPos(3200+kind.ordinal()*4,70,3200);}
    private static boolean owned(ItemStack stack,String owner){return stack.hasTag()&&owner.equals(stack.getTag().getString(OWNER));}
    public static void retainDrops(net.minecraft.server.level.ServerLevel level){
        for(var entity:level.getEntitiesOfClass(ItemEntity.class,new AABB(3180,0,3180,3260,160,3230)))if(entity.getItem().hasTag()&&entity.getItem().getTag().contains(OWNER))entity.setUnlimitedLifetime();
        for(var player:level.players()){
            var carried=player.containerMenu.getCarried();
            if(owned(carried,player.getScoreboardName())&&cursorReported.add(player.getUUID()))System.out.println("FOODCRAFT STRESS CURSOR PROOF player="+player.getScoreboardName()+" owned_count="+carried.getCount());
        }
    }
    public static void setup(ServerPlayer player){
        var level=player.serverLevel();
        for(var kind:MachineKind.values()){
            var p=pos(kind);level.setChunkForced(p.getX()>>4,p.getZ()>>4,true);for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)level.setBlock(p.below().offset(x,0,z),Blocks.STONE.defaultBlockState(),3);
            if(!(level.getBlockEntity(p) instanceof MachineBlockEntity))level.setBlock(p,FoodCraft.BLOCKS.get(kind.id).defaultBlockState(),3);
        }
        player.closeContainer();
        if(!player.getTags().contains("foodcraft_stress_initialized")){
            player.getInventory().clearContent();var stack=new ItemStack(Items.COBBLESTONE,48);stack.getOrCreateTag().putString(OWNER,player.getScoreboardName());player.getInventory().setItem(9,stack);
            player.addTag("foodcraft_stress_initialized");
        }
        assertLedger(player);player.sendSystemMessage(Component.literal("FOODCRAFT_STRESS_READY"));
    }
    public static void open(ServerPlayer player,MachineKind kind,boolean full,boolean creative){
        var request=new Pending(player.getUUID(),kind,full,creative);pending.put(player.getUUID(),request);
        var match=pending.values().stream().filter(other->!other.player.equals(request.player)&&other.kind==kind&&other.full==full&&other.creative==creative&&player.getServer().getPlayerList().getPlayer(other.player)!=null).findFirst();
        if(match.isEmpty())return;
        var other=player.getServer().getPlayerList().getPlayer(match.get().player);pending.remove(player.getUUID());pending.remove(other.getUUID());
        openNow(player,kind,full,creative);openNow(other,kind,full,creative);
        System.out.println("FOODCRAFT STRESS SHARED MENU OPEN kind="+kind+" viewers=2 full="+full+" creative="+creative);
    }
    private static void openNow(ServerPlayer player,MachineKind kind,boolean full,boolean creative){
        player.closeContainer();player.setGameMode(creative?GameType.CREATIVE:GameType.SURVIVAL);
        if(kind==MachineKind.MILLING_MACHINE)checkpoint(player);
        for(int slot=0;slot<36;slot++)if(!owned(player.getInventory().getItem(slot),player.getScoreboardName())){
            var existing=player.getInventory().getItem(slot);
            if(existing.hasTag()&&existing.getTag().contains(OWNER))continue;
            player.getInventory().setItem(slot,full?new ItemStack(Items.BARRIER,64):ItemStack.EMPTY);
        }
        var level=player.serverLevel();var p=pos(kind);
        if(!(level.getBlockEntity(p) instanceof MachineBlockEntity))level.setBlock(p,FoodCraft.BLOCKS.get(kind.id).defaultBlockState(),3);
        player.teleportTo(level,p.getX()+0.5,71,p.getZ()+1.5,180,20);player.openMenu((MachineBlockEntity)level.getBlockEntity(p));
    }
    private static void checkpoint(ServerPlayer player){
        assertLedger(player);String owner=player.getScoreboardName();var level=player.serverLevel();ItemStack[] collected={ItemStack.EMPTY};
        java.util.function.Consumer<ItemStack> take=stack->{if(collected[0].isEmpty())collected[0]=stack.copy();else{if(!ItemStack.isSameItemSameTags(collected[0],stack))throw new IllegalStateException("Mixed stress checkpoint items");collected[0].grow(stack.getCount());}};
        for(var online:player.getServer().getPlayerList().getPlayers()){
            for(int slot=0;slot<online.getInventory().getContainerSize();slot++)if(owned(online.getInventory().getItem(slot),owner)){take.accept(online.getInventory().getItem(slot));online.getInventory().setItem(slot,ItemStack.EMPTY);}
            if(owned(online.containerMenu.getCarried(),owner)){take.accept(online.containerMenu.getCarried());online.containerMenu.setCarried(ItemStack.EMPTY);}
            online.containerMenu.broadcastChanges();
        }
        for(var kind:MachineKind.values())if(level.getBlockEntity(pos(kind)) instanceof MachineBlockEntity machine)for(int slot=0;slot<machine.getContainerSize();slot++)if(owned(machine.getItem(slot),owner)){take.accept(machine.getItem(slot));machine.setItem(slot,ItemStack.EMPTY);}
        for(var entity:level.getEntitiesOfClass(ItemEntity.class,new AABB(3180,0,3180,3260,160,3230)))if(owned(entity.getItem(),owner)){take.accept(entity.getItem());entity.discard();}
        if(collected[0].getCount()!=48)throw new IllegalStateException("Stress checkpoint failed conservation");
        player.getInventory().setItem(9,collected[0]);player.getInventory().setChanged();assertLedger(player);
    }
    public static void assertCursor(ServerPlayer player){
        var carried=player.containerMenu.getCarried();if(!owned(carried,player.getScoreboardName())||carried.getCount()!=48)throw new IllegalStateException("Native pickup did not place the owned allocation on the cursor: "+carried);
        for(int slot=0;slot<36;slot++)if(player.getInventory().getItem(slot).isEmpty())player.getInventory().setItem(slot,new ItemStack(Items.BARRIER,64));
        player.containerMenu.broadcastChanges();
        System.out.println("FOODCRAFT STRESS NATIVE CURSOR PASS player="+player.getScoreboardName()+" owned_count=48");player.sendSystemMessage(Component.literal("FOODCRAFT_STRESS_CURSOR_OK"));
    }
    public static void resynchronize(ServerPlayer player){
        pending.clear();
        for(var online:player.getServer().getPlayerList().getPlayers())if(online.getTags().contains("foodcraft_stress_initialized")){
            online.closeContainer();online.sendSystemMessage(Component.literal("FOODCRAFT_STRESS_RESET"));
        }
        System.out.println("FOODCRAFT STRESS PAIRED RECOVERY synchronized=true");
    }
    public static void destroy(ServerPlayer player,MachineKind kind){
        player.serverLevel().destroyBlock(pos(kind),true,player);player.closeContainer();assertLedger(player);
        System.out.println("FOODCRAFT STRESS DESTRUCTION PASS player="+player.getScoreboardName()+" mode="+player.gameMode.getGameModeForPlayer()+" kind="+kind);
    }
    public static void assertLedger(ServerPlayer player){
        String owner=player.getScoreboardName();var level=player.serverLevel();int count=0;
        for(var online:player.getServer().getPlayerList().getPlayers()){
            for(int slot=0;slot<online.getInventory().getContainerSize();slot++){var stack=online.getInventory().getItem(slot);if(owned(stack,owner))count+=stack.getCount();}
            if(owned(online.containerMenu.getCarried(),owner))count+=online.containerMenu.getCarried().getCount();
        }
        try(var data=Files.list(player.getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR))){
            for(var file:data.filter(path->path.getFileName().toString().endsWith(".dat")).toList()){
                String uuid=file.getFileName().toString().replace(".dat","");if(player.getServer().getPlayerList().getPlayers().stream().anyMatch(online->online.getUUID().toString().equals(uuid)))continue;
                var tag=NbtIo.readCompressed(file.toFile());for(var item:tag.getList("Inventory",10)){var stack=ItemStack.of((net.minecraft.nbt.CompoundTag)item);if(owned(stack,owner))count+=stack.getCount();}
            }
        }catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
        for(var kind:MachineKind.values())if(level.getBlockEntity(pos(kind)) instanceof MachineBlockEntity machine)for(int slot=0;slot<machine.getContainerSize();slot++)if(owned(machine.getItem(slot),owner))count+=machine.getItem(slot).getCount();
        for(var entity:level.getEntitiesOfClass(ItemEntity.class,new AABB(3180,0,3180,3260,160,3230)))if(owned(entity.getItem(),owner))count+=entity.getItem().getCount();
        if(count!=48)throw new IllegalStateException("Stress ledger mismatch "+owner+": "+count+" != 48");
        System.out.println("FOODCRAFT STRESS LEDGER PASS owner="+owner+" conserved="+count+" includes_offline=true");
        player.sendSystemMessage(Component.literal("FOODCRAFT_STRESS_LEDGER_OK"));
    }
}
