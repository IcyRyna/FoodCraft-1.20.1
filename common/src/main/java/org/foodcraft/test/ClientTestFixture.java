package org.foodcraft.test;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.foodcraft.FoodCraft;
import org.foodcraft.Catalog;
import org.foodcraft.machine.*;

/** Opt-in fixtures for isolated development clients. Disabled in ordinary installations. */
public final class ClientTestFixture {
    private record ChunkProof(BlockPos pos,MachineBlockEntity original,boolean unloaded){}
    private static final java.util.Map<java.util.UUID,ChunkProof> chunkProofs=new java.util.HashMap<>();
    private ClientTestFixture(){}
    public static BlockPos machinePos(MachineKind kind){return new BlockPos(2+(kind.ordinal()%3)*4,70,2+(kind.ordinal()/3)*4);}
    public static void commands(CommandDispatcher<CommandSourceStack> dispatcher){
        if(!Boolean.getBoolean("foodcraft.qa.server"))return;
        AuditServerFixture.commands(dispatcher);
        dispatcher.register(Commands.literal("foodcraftqa").requires(source->source.hasPermission(2))
            .then(Commands.literal("setup").executes(context->{setup(context.getSource().getPlayerOrException());return 1;}))
            .then(Commands.literal("pipeline_prepare").executes(context->{PipelineVerification.prepare(context.getSource().getLevel());return 1;}))
            .then(Commands.literal("pipeline_status").executes(context->{PipelineVerification.status(context.getSource().getLevel());return 1;}))
            .then(Commands.literal("pipeline_restart_soak").executes(context->{PipelineVerification.restartSoak(context.getSource().getLevel());return 1;}))
            .then(Commands.literal("stress_setup").executes(context->{StressFixture.setup(context.getSource().getPlayerOrException());return 1;}))
            .then(Commands.literal("stress_assert").executes(context->{StressFixture.assertLedger(context.getSource().getPlayerOrException());return 1;}))
            .then(Commands.literal("stress_cursor_assert").executes(context->{StressFixture.assertCursor(context.getSource().getPlayerOrException());return 1;}))
            .then(Commands.literal("stress_resynchronize").executes(context->{StressFixture.resynchronize(context.getSource().getPlayerOrException());return 1;}))
            .then(Commands.literal("stress_destroy").then(Commands.argument("kind",com.mojang.brigadier.arguments.StringArgumentType.word()).executes(context->{StressFixture.destroy(context.getSource().getPlayerOrException(),MachineKind.byId(com.mojang.brigadier.arguments.StringArgumentType.getString(context,"kind")));return 1;})))
            .then(Commands.literal("stress_open").then(Commands.argument("kind",com.mojang.brigadier.arguments.StringArgumentType.word()).then(Commands.argument("full",com.mojang.brigadier.arguments.BoolArgumentType.bool()).then(Commands.argument("creative",com.mojang.brigadier.arguments.BoolArgumentType.bool()).executes(context->{StressFixture.open(context.getSource().getPlayerOrException(),MachineKind.byId(com.mojang.brigadier.arguments.StringArgumentType.getString(context,"kind")),com.mojang.brigadier.arguments.BoolArgumentType.getBool(context,"full"),com.mojang.brigadier.arguments.BoolArgumentType.getBool(context,"creative"));return 1;})))))
            .then(Commands.literal("open").then(Commands.argument("kind",com.mojang.brigadier.arguments.StringArgumentType.word())
                .executes(context->{open(context.getSource().getPlayerOrException(),MachineKind.byId(com.mojang.brigadier.arguments.StringArgumentType.getString(context,"kind")));return 1;})))
            .then(Commands.literal("assert").executes(context->{assertLedger(context.getSource().getPlayerOrException());return 1;}))
            .then(Commands.literal("destroy_check").executes(context->{destroyCheck(context.getSource().getPlayerOrException());return 1;}))
            .then(Commands.literal("compat_verify").executes(context->{compatVerify(context.getSource().getLevel());return 1;}))
            .then(Commands.literal("chunk_prepare").executes(context->{chunkPrepare(context.getSource().getPlayerOrException());return 1;}))
            .then(Commands.literal("chunk_leave").executes(context->{chunkLeave(context.getSource().getPlayerOrException());return 1;}))
            .then(Commands.literal("chunk_poll").executes(context->{chunkPoll(context.getSource().getPlayerOrException());return 1;}))
            .then(Commands.literal("chunk_verify").executes(context->{chunkVerify(context.getSource().getPlayerOrException());return 1;}))
            .then(Commands.literal("recovery_verify").executes(context->{recoveryVerify(context.getSource().getLevel());return 1;}))
            .then(Commands.literal("transfer_prepare").then(Commands.argument("recipe",net.minecraft.commands.arguments.ResourceLocationArgument.id())
                .executes(context->{transferPrepare(context.getSource().getPlayerOrException(),net.minecraft.commands.arguments.ResourceLocationArgument.getId(context,"recipe"));return 1;})))
            .then(Commands.literal("transfer_assert").then(Commands.argument("recipe",net.minecraft.commands.arguments.ResourceLocationArgument.id())
                .executes(context->{transferAssert(context.getSource().getPlayerOrException(),net.minecraft.commands.arguments.ResourceLocationArgument.getId(context,"recipe"),1);return 1;})))
            .then(Commands.literal("transfer_max_assert").then(Commands.argument("recipe",net.minecraft.commands.arguments.ResourceLocationArgument.id())
                .executes(context->{transferAssert(context.getSource().getPlayerOrException(),net.minecraft.commands.arguments.ResourceLocationArgument.getId(context,"recipe"),3);return 1;})))
            .then(Commands.literal("persist_prepare").executes(context->{persistPrepare(context.getSource().getLevel());return 1;}))
            .then(Commands.literal("persist_verify").executes(context->{persistVerify(context.getSource().getLevel());return 1;})));
    }
    public static void onJoin(ServerPlayer player){
        if(Boolean.getBoolean("foodcraft.qa.server")&&player.getGameProfile().getName().startsWith("FoodCraftQA")){
            player.getServer().getPlayerList().op(player.getGameProfile());
            if(player.getTags().contains("foodcraftqa_complete")){assertLedger(player);System.out.println("FOODCRAFT RECONNECT PASS "+player.getScoreboardName());}
        }
    }
    public static void setup(ServerPlayer player){
        ServerLevel level=player.serverLevel();
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());
        level.setDayTime(6000);
        for(int x=-4;x<34;x++)for(int z=-4;z<24;z++)level.setBlock(new BlockPos(x,69,z),Blocks.STONE.defaultBlockState(),3);
        for(MachineKind kind:MachineKind.values()){
            BlockPos pos=machinePos(kind);
            if(!(level.getBlockEntity(pos) instanceof MachineBlockEntity))level.setBlock(pos,FoodCraft.BLOCKS.get(kind.id).defaultBlockState(),3);
            var machine=(MachineBlockEntity)level.getBlockEntity(pos);
            if(kind!=MachineKind.STOVE){
                var recipe=level.getRecipeManager().getAllRecipesFor(FoodCraft.RECIPE_TYPES.get(kind)).get(0);
                for(var input:recipe.inputs)machine.setItem(input.slot(),input.ingredient().getItems()[0].copyWithCount(64));
                machine.liquid=8;machine.milk=recipe.milk;
                if(kind==MachineKind.CUTTING_BOARD)machine.setItem(0,new ItemStack(FoodCraft.item("caidao")));
                if(kind.fuelSlot>=0)machine.setItem(kind.fuelSlot,new ItemStack(Items.COAL,64));
                if(kind==MachineKind.DRINK_MAKER&&recipe.cold)machine.setItem(4,new ItemStack(Blocks.ICE,64));
                if(kind.heatedExternally()){
                    level.setBlock(pos.below(),FoodCraft.BLOCKS.get("stove").defaultBlockState(),3);
                    ((MachineBlockEntity)level.getBlockEntity(pos.below())).setItem(0,new ItemStack(Items.COAL,64));
                    machine.adjustFirePower(Math.min(100,(recipe.minHeat+recipe.maxHeat)/Math.max(1,recipe.time/17)));
                }
            }else machine.setItem(0,new ItemStack(Items.COAL,64));
        }
        int index=0;
        for(Catalog.Entry entry:Catalog.ENTRIES)if(entry.kind().equals("crop")||entry.kind().equals("onion")){
            BlockPos pos=new BlockPos(2+(index%9)*2,70,16+(index/9)*2);
            level.setBlock(pos.below(),Blocks.FARMLAND.defaultBlockState(),3);
            var state=FoodCraft.BLOCKS.get(entry.path()).defaultBlockState();
            if(state.hasProperty(net.minecraft.world.level.block.CropBlock.AGE))state=state.setValue(net.minecraft.world.level.block.CropBlock.AGE,7);
            level.setBlock(pos,state,3);index++;
        }
        player.setGameMode(GameType.CREATIVE);player.teleportTo(level,16.5,75,22.5,145,25);
        System.out.println("FOODCRAFT CLIENT FIXTURE READY "+player.getScoreboardName());
    }
    private static boolean owned(ItemStack stack,String owner){return stack.hasTag()&&owner.equals(stack.getTag().getString("FoodCraftQAOwner"));}
    public static void open(ServerPlayer player,MachineKind kind){
        var level=player.serverLevel();String owner=player.getScoreboardName();
        for(var other:level.getServer().getPlayerList().getPlayers()){
            for(int i=0;i<other.getInventory().getContainerSize();i++)if(owned(other.getInventory().getItem(i),owner))other.getInventory().setItem(i,ItemStack.EMPTY);
            if(owned(other.containerMenu.getCarried(),owner))other.containerMenu.setCarried(ItemStack.EMPTY);
        }
        for(MachineKind candidate:MachineKind.values())if(level.getBlockEntity(machinePos(candidate)) instanceof MachineBlockEntity machine){
            for(int i=0;i<machine.getContainerSize();i++)if(owned(machine.getItem(i),owner))machine.setItem(i,ItemStack.EMPTY);
        }
        for(var item:level.getEntitiesOfClass(ItemEntity.class,new AABB(-64,0,-64,128,160,128)))if(owned(item.getItem(),owner))item.discard();
        ItemStack main=new ItemStack(Items.COBBLESTONE,32);main.getOrCreateTag().putString("FoodCraftQAOwner",owner);
        player.getInventory().setItem(9,main);player.getInventory().setItem(0,main.copyWithCount(16));
        var pos=machinePos(kind);player.teleportTo(level,pos.getX()+0.5,71,pos.getZ()+2.5,180,20);
        player.openMenu((MachineBlockEntity)level.getBlockEntity(pos));
    }
    public static void assertLedger(ServerPlayer player){
        String owner=player.getScoreboardName();int count=0;var level=player.serverLevel();
        var online=new java.util.ArrayList<>(level.getServer().getPlayerList().getPlayers());
        // Fabric JOIN can run before the joining player is inserted into PlayerList.
        if(!online.contains(player))online.add(player);
        for(var other:online){
            for(int i=0;i<other.getInventory().getContainerSize();i++){var stack=other.getInventory().getItem(i);if(owned(stack,owner))count+=stack.getCount();}
            if(owned(other.containerMenu.getCarried(),owner))count+=other.containerMenu.getCarried().getCount();
        }
        for(MachineKind kind:MachineKind.values())if(level.getBlockEntity(machinePos(kind)) instanceof MachineBlockEntity machine){
            for(int i=0;i<machine.getContainerSize();i++)if(owned(machine.getItem(i),owner))count+=machine.getItem(i).getCount();
        }
        for(var entity:level.getEntitiesOfClass(ItemEntity.class,new AABB(-64,0,-64,128,160,128)))if(owned(entity.getItem(),owner))count+=entity.getItem().getCount();
        if(count!=48)throw new IllegalStateException("FoodCraft client inventory ledger "+owner+": expected 48, got "+count);
        System.out.println("FOODCRAFT CLIENT INVENTORY PASS owner="+owner+" count="+count);
    }
    public static void persistPrepare(ServerLevel level){
        var pos=new BlockPos(40,70,40);level.setBlock(pos,FoodCraft.BLOCKS.get("fermenting_barrel").defaultBlockState(),3);
        var machine=(MachineBlockEntity)level.getBlockEntity(pos);machine.clearContent();
        var recipe=level.getRecipeManager().getAllRecipesFor(FoodCraft.RECIPE_TYPES.get(MachineKind.FERMENTING_BARREL)).get(0);
        for(var input:recipe.inputs)machine.setItem(input.slot(),input.ingredient().getItems()[0].copyWithCount(32));
        machine.liquid=8;machine.milk=false;MachineBlockEntity.tick(level,pos,machine.getBlockState(),machine);
        machine.setItem(5,recipe.result.copyWithCount(64));machine.progress=123;machine.proficiency=777;machine.adjustFirePower(37);machine.setChanged();
        System.out.println("FOODCRAFT PERSISTENCE PREPARED progress=123 liquid=8 skill=777 power=37");
    }
    public static void destroyCheck(ServerPlayer player){
        var level=player.serverLevel();var pos=machinePos(MachineKind.CUTTING_BOARD);
        if(!(level.getBlockEntity(pos) instanceof MachineBlockEntity))level.setBlock(pos,FoodCraft.BLOCKS.get("cutting_board").defaultBlockState(),3);
        open(player,MachineKind.CUTTING_BOARD);
        var machine=(MachineBlockEntity)level.getBlockEntity(pos);machine.clearContent();
        machine.setItem(1,player.getInventory().removeItem(9,32));
        level.destroyBlock(pos,true,player);player.closeContainer();assertLedger(player);checkpointOwner(player);
        player.addTag("foodcraftqa_complete");System.out.println("FOODCRAFT CLIENT DESTROY PASS "+player.getScoreboardName());
    }
    private static void checkpointOwner(ServerPlayer player){
        String owner=player.getScoreboardName();var level=player.serverLevel();ItemStack[] collected={ItemStack.EMPTY};
        java.util.function.Consumer<ItemStack> gather=stack->{
            if(collected[0].isEmpty())collected[0]=stack.copy();
            else{if(!ItemStack.isSameItemSameTags(collected[0],stack))throw new IllegalStateException("Mixed QA checkpoint stacks");collected[0].grow(stack.getCount());}
        };
        for(var other:level.getServer().getPlayerList().getPlayers()){
            for(int i=0;i<other.getInventory().getContainerSize();i++)if(owned(other.getInventory().getItem(i),owner)){gather.accept(other.getInventory().getItem(i));other.getInventory().setItem(i,ItemStack.EMPTY);}
            if(owned(other.containerMenu.getCarried(),owner)){gather.accept(other.containerMenu.getCarried());other.containerMenu.setCarried(ItemStack.EMPTY);}
        }
        for(MachineKind kind:MachineKind.values())if(level.getBlockEntity(machinePos(kind)) instanceof MachineBlockEntity machine){
            for(int i=0;i<machine.getContainerSize();i++)if(owned(machine.getItem(i),owner)){gather.accept(machine.getItem(i));machine.setItem(i,ItemStack.EMPTY);}
        }
        for(var entity:level.getEntitiesOfClass(ItemEntity.class,new AABB(-64,0,-64,128,160,128)))if(owned(entity.getItem(),owner)){gather.accept(entity.getItem());entity.discard();}
        if(collected[0].getCount()!=48)throw new IllegalStateException("QA checkpoint lost items");
        player.getInventory().setItem(9,collected[0]);player.getInventory().setChanged();assertLedger(player);
        System.out.println("FOODCRAFT CLIENT CHECKPOINT PASS "+owner+" owned=48");
    }
    public static void compatVerify(ServerLevel level){
        var recipe=level.getRecipeManager().byKey(new net.minecraft.resources.ResourceLocation("crafttweaker:foodcraft_probe")).orElseThrow(()->new IllegalStateException("CraftTweaker addJsonRecipe did not register the validation recipe"));
        if(!(recipe instanceof org.foodcraft.recipe.MachineRecipe machineRecipe))throw new IllegalStateException("CraftTweaker registered the wrong recipe type");
        var pos=new BlockPos(44,70,44);level.setBlock(pos,FoodCraft.BLOCKS.get("milling_machine").defaultBlockState(),3);
        var machine=(MachineBlockEntity)level.getBlockEntity(pos);machine.clearContent();
        machine.setItem(0,new ItemStack(Items.STICK,2));machine.setItem(2,new ItemStack(Items.COAL));
        for(int i=0;i<5;i++)MachineBlockEntity.tick(level,pos,machine.getBlockState(),machine);
        if(!machine.getItem(1).is(Items.BREAD)||machine.getItem(1).getCount()!=2||!machine.getItem(0).isEmpty())throw new IllegalStateException("CraftTweaker-added recipe did not process exactly once");
        System.out.println("FOODCRAFT CRAFTTWEAKER PASS recipe="+recipe.getId()+" sticks=2 bread=2");
    }
    public static void transferPrepare(ServerPlayer player,net.minecraft.resources.ResourceLocation id){
        var recipe=(org.foodcraft.recipe.MachineRecipe)player.level().getRecipeManager().byKey(id).orElseThrow();
        var level=player.serverLevel();var pos=machinePos(recipe.kind);
        player.closeContainer();
        if(!(level.getBlockEntity(pos) instanceof MachineBlockEntity))level.setBlock(pos,FoodCraft.BLOCKS.get(recipe.kind.id).defaultBlockState(),3);
        var machine=(MachineBlockEntity)level.getBlockEntity(pos);machine.clearContent();machine.liquid=8;machine.milk=recipe.milk;
        machine.setItem(recipe.kind.outputs[0],new ItemStack(Items.COBBLESTONE,64));
        if(recipe.kind==MachineKind.CUTTING_BOARD)machine.setItem(0,new ItemStack(FoodCraft.item("caidao")));
        player.getInventory().clearContent();player.containerMenu.setCarried(ItemStack.EMPTY);
        for(int index=0;index<recipe.inputs.size();index++){
            var input=recipe.inputs.get(index);player.getInventory().setItem(9+index,input.ingredient().getItems()[0].copyWithCount(input.count()*3));
        }
        for(int slot:recipe.kind.inputs)if(recipe.inputs.stream().noneMatch(input->input.slot()==slot)){machine.setItem(slot,new ItemStack(Items.DIRT,2));break;}
        player.teleportTo(level,pos.getX()+0.5,71,pos.getZ()+2.5,180,20);player.openMenu(machine);
        System.out.println("FOODCRAFT DETAIL TRANSFER PREPARED "+id+" menu="+player.containerMenu.containerId);
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("FOODCRAFT_TRANSFER_READY "+id+" "+player.containerMenu.containerId));
    }
    public static void transferAssert(ServerPlayer player,net.minecraft.resources.ResourceLocation id,int batches){
        System.out.println("FOODCRAFT DETAIL TRANSFER ASSERT CONTEXT recipe="+id+" menu="+player.containerMenu.getClass().getSimpleName()+" id="+player.containerMenu.containerId+" valid="+(player.containerMenu instanceof MachineMenu menu&&menu.stillValid(player)));
        var recipe=(org.foodcraft.recipe.MachineRecipe)player.level().getRecipeManager().byKey(id).orElseThrow();
        var machine=(MachineBlockEntity)player.serverLevel().getBlockEntity(machinePos(recipe.kind));
        for(int slot:recipe.kind.inputs){
            var input=recipe.inputs.stream().filter(value->value.slot()==slot).findFirst().orElse(null);
            var stack=machine.getItem(slot);
            if(input==null?!stack.isEmpty():!input.ingredient().test(stack)||stack.getCount()!=input.count()*batches)
                throw new IllegalStateException("JEI transfer mismatch "+id+" slot="+slot+" actual="+stack+" expected="+(input==null?0:input.count()*batches));
        }
        if(!recipe.matches(machine,player.level()))throw new IllegalStateException("JEI transferred ingredients do not match "+id);
        System.out.println("FOODCRAFT DETAIL JEI SERVER TRANSFER PASS recipe="+id+" batches="+batches);
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("FOODCRAFT_TRANSFER_CONFIRMED "+id+" "+batches));
    }
    public static void chunkPrepare(ServerPlayer player){
        var level=player.serverLevel();int coordinate=2048+Math.floorMod(player.getScoreboardName().hashCode(),8)*512;
        var pos=new BlockPos(coordinate,70,coordinate);player.closeContainer();player.teleportTo(level,pos.getX()+0.5,74,pos.getZ()+0.5,0,0);
        level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),3);level.setBlock(pos,FoodCraft.BLOCKS.get("fermenting_barrel").defaultBlockState(),3);
        var machine=(MachineBlockEntity)level.getBlockEntity(pos);machine.clearContent();
        var recipe=level.getRecipeManager().getAllRecipesFor(FoodCraft.RECIPE_TYPES.get(machine.kind)).get(0);
        for(var input:recipe.inputs)machine.setItem(input.slot(),input.ingredient().getItems()[0].copyWithCount(32));
        machine.liquid=8;machine.milk=recipe.milk;MachineBlockEntity.tick(level,pos,machine.getBlockState(),machine);
        machine.setItem(5,recipe.result.copyWithCount(64));machine.progress=123;machine.proficiency=777;machine.adjustFirePower(37);machine.setChanged();
        chunkProofs.put(player.getUUID(),new ChunkProof(pos,machine,false));
        System.out.println("FOODCRAFT DETAIL CHUNK PREPARED "+player.getScoreboardName()+" pos="+pos);
    }
    public static void chunkLeave(ServerPlayer player){
        player.teleportTo(player.serverLevel(),8192.5,74,8192.5,0,0);player.serverLevel().getChunkSource().save(true);
    }
    public static void chunkPoll(ServerPlayer player){
        var proof=chunkProofs.get(player.getUUID());if(proof==null||proof.unloaded())return;
        var chunk=new net.minecraft.world.level.ChunkPos(proof.pos());
        if(player.serverLevel().getChunkSource().getChunkNow(chunk.x,chunk.z)==null&&proof.original().isRemoved()){
            chunkProofs.put(player.getUUID(),new ChunkProof(proof.pos(),proof.original(),true));
            String message="FOODCRAFT DETAIL CHUNK UNLOADED "+player.getScoreboardName();
            System.out.println(message);player.sendSystemMessage(net.minecraft.network.chat.Component.literal(message));
        }
    }
    public static void chunkVerify(ServerPlayer player){
        var proof=chunkProofs.get(player.getUUID());if(proof==null||!proof.unloaded())throw new IllegalStateException("Real player-distance chunk unloading was not observed");
        var level=player.serverLevel();var machine=(MachineBlockEntity)level.getBlockEntity(proof.pos());
        if(machine==null||machine==proof.original()||machine.progress!=123||machine.liquid!=8||machine.proficiency!=777||machine.firePower!=37||machine.getItem(5).getCount()!=64)
            throw new IllegalStateException("Real chunk reload lost machine state");
        var recipe=(org.foodcraft.recipe.MachineRecipe)level.getRecipeManager().byKey(new net.minecraft.resources.ResourceLocation(machine.saveWithoutMetadata().getString("ActiveRecipe"))).orElseThrow();
        for(var input:recipe.inputs)if(machine.getItem(input.slot()).getCount()!=32)throw new IllegalStateException("Real chunk reload changed the input quantity");
        machine.removeItem(5,64);MachineBlockEntity.tick(level,proof.pos(),machine.getBlockState(),machine);
        if(machine.progress!=124)throw new IllegalStateException("Real chunk reload did not resume the recipe");
        chunkProofs.remove(player.getUUID());player.teleportTo(level,16.5,75,22.5,145,25);
        String message="FOODCRAFT DETAIL CHUNK RELOAD PASS "+player.getScoreboardName()+" new_entity=true progress=124 liquid=8 skill=777 power=37";
        System.out.println(message);player.sendSystemMessage(net.minecraft.network.chat.Component.literal(message));
    }
    public static void recoveryVerify(ServerLevel level){
        if(FoodCraft.platform.wrenchEnabled())throw new IllegalStateException("Disabled-wrench recovery test requires wrench=false");
        net.minecraft.world.item.Item[] tools={Items.WOODEN_PICKAXE,Items.STONE_PICKAXE,Items.GOLDEN_PICKAXE,Items.IRON_PICKAXE,Items.DIAMOND_PICKAXE,Items.NETHERITE_PICKAXE};int cases=0;
        var pos=new BlockPos(46,70,46);
        for(var kind:MachineKind.values()){
            level.setBlock(pos,FoodCraft.BLOCKS.get(kind.id).defaultBlockState(),3);var machine=(MachineBlockEntity)level.getBlockEntity(pos);machine.proficiency=777;
            var state=level.getBlockState(pos);
            for(int index=0;index<tools.length;index++){
                var params=new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                        .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN,net.minecraft.world.phys.Vec3.atCenterOf(pos))
                        .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_STATE,state)
                        .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY,machine)
                        .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.TOOL,new ItemStack(tools[index]));
                var drops=state.getBlock().getDrops(state,params);
                if(index<3?!drops.isEmpty():drops.size()!=1||!drops.get(0).is(FoodCraft.item(kind.id)))throw new IllegalStateException("Disabled-wrench tool tier recovery mismatch "+kind+" / "+tools[index]);
                if(index>=3&&kind.heatedExternally()&&(!drops.get(0).hasTag()||drops.get(0).getTag().getCompound("BlockEntityTag").getInt("Proficiency")!=777))throw new IllegalStateException("Disabled-wrench recovery lost proficiency");
                cases++;
            }
        }
        System.out.println("FOODCRAFT DETAIL WRENCH DISABLED RECOVERY PASS cases="+cases+" pickaxe_tiers=6 machines=9");
    }
    public static void persistVerify(ServerLevel level){
        var machine=(MachineBlockEntity)level.getBlockEntity(new BlockPos(40,70,40));
        if(machine==null||machine.progress!=123||machine.liquid!=8||machine.proficiency!=777||machine.firePower!=37||machine.getItem(5).getCount()!=64)throw new IllegalStateException("Packaged-JAR save/restart did not preserve the machine state");
        var id=new net.minecraft.resources.ResourceLocation(machine.saveWithoutMetadata().getString("ActiveRecipe"));
        var recipe=(org.foodcraft.recipe.MachineRecipe)level.getRecipeManager().byKey(id).orElseThrow();
        for(int slot:machine.kind.inputs){int expected=recipe.inputs.stream().anyMatch(input->input.slot()==slot)?32:0;if(machine.getItem(slot).getCount()!=expected)throw new IllegalStateException("Saved input count changed at slot "+slot);}
        machine.removeItem(5,64);MachineBlockEntity.tick(level,machine.getBlockPos(),machine.getBlockState(),machine);
        if(machine.progress!=124)throw new IllegalStateException("Machine did not resume the saved recipe");
        System.out.println("FOODCRAFT PERSISTENCE RESTART PASS progress=124 liquid=8 skill=777 power=37");
    }
}
