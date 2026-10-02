package org.foodcraft.test;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.SavedData;
import org.foodcraft.FoodCraft;
import org.foodcraft.machine.MachineBlockEntity;
import org.foodcraft.recipe.MachineRecipe;

/** Failed output slots, persisted by a real dedicated-server save and process restart. QA flag only. */
public final class FailurePersistenceFixture extends SavedData {
    private ListTag snapshots=new ListTag();
    private boolean prepared;
    private FailurePersistenceFixture(){}
    private static FailurePersistenceFixture load(CompoundTag tag){var f=new FailurePersistenceFixture();f.prepared=tag.getBoolean("Prepared");f.snapshots=tag.getList("Machines",10).copy();return f;}
    private static FailurePersistenceFixture get(ServerLevel level){return level.getDataStorage().computeIfAbsent(FailurePersistenceFixture::load,FailurePersistenceFixture::new,"foodcraft_failed_persistence");}
    @Override public CompoundTag save(CompoundTag tag){tag.putBoolean("Prepared",prepared);tag.put("Machines",snapshots.copy());return tag;}
    public static void commands(CommandDispatcher<CommandSourceStack> dispatcher){
        if(!Boolean.getBoolean("foodcraft.qa.server"))return;
        dispatcher.register(Commands.literal("foodcraftfailureaudit").requires(s->s.hasPermission(2))
            .then(Commands.literal("prepare").executes(c->{prepare(c.getSource().getLevel());return 1;}))
            .then(Commands.literal("verify").executes(c->{verify(c.getSource().getLevel());return 1;})));
    }
    private static BlockPos pos(int index){return new BlockPos(16000+index%12*4,75,16000+index/12*4);}
    private static MachineBlockEntity machine(ServerLevel level,int index){return (MachineBlockEntity)level.getBlockEntity(pos(index));}
    private static void require(boolean ok,String reason){if(!ok)throw new IllegalStateException(reason);}
    private static ItemStack failed(MachineRecipe recipe,boolean burnt){return burnt?new ItemStack(Items.COAL):recipe.inputs.stream().filter(i->i.slot()==recipe.kind.inputs[0]).findFirst().orElseThrow().ingredient().getItems()[0].copyWithCount(1);}
    private static void prepare(ServerLevel level){
        var audit=get(level);require(!audit.prepared,"Failed-output persistence already prepared");
        var recipes=FoodCraft.RECIPE_TYPES.values().stream().flatMap(type->level.getRecipeManager().getAllRecipesFor(type).stream()).filter(r->r.kind.heatedExternally()).sorted(java.util.Comparator.comparing(r->r.getId().toString())).toList();
        int index=0;
        for(var recipe:recipes)for(boolean burnt:new boolean[]{false,true})for(int variant=0;variant<3;variant++){
            require(recipe.minHeat>0&&recipe.maxHeat<Integer.MAX_VALUE,"Fixture requires a bounded heated baseline recipe");
            var pos=pos(index);level.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true);
            level.setBlock(pos,FoodCraft.BLOCKS.get(recipe.kind.id).defaultBlockState(),3);
            level.setBlock(pos.below(),FoodCraft.BLOCKS.get("stove").defaultBlockState(),3);
            var machine=machine(level,index);var stove=(MachineBlockEntity)level.getBlockEntity(pos.below());stove.burnTime=Integer.MAX_VALUE;
            for(var input:recipe.inputs)machine.setItem(input.slot(),input.ingredient().getItems()[0].copyWithCount(input.count()));
            var output=failed(recipe,burnt);int slot=recipe.kind.outputs[1];
            if(variant==0)machine.setItem(slot,output.copyWithCount(output.getMaxStackSize()));
            if(variant==1)machine.setItem(slot,new ItemStack(Items.BARRIER,64));
            if(variant==2){var incompatible=output.copyWithCount(63);incompatible.getOrCreateTag().putString("FailureAudit","different-nbt");machine.setItem(slot,incompatible);}
            var saved=machine.saveWithoutMetadata();saved.putInt("Progress",Integer.MAX_VALUE);saved.putInt("TotalTime",recipe.time);saved.putInt("FirePower",0);
            saved.putInt("AccumulatedHeat",burnt?recipe.maxHeat+1:0);saved.putInt("Proficiency",777);saved.putInt("Liquid",8);
            saved.putString("ActiveRecipe",recipe.getId().toString());saved.putString("ActiveRecipeFingerprint",recipe.fingerprint);machine.load(saved);
            MachineBlockEntity.tick(level,pos,machine.getBlockState(),machine);
            require(machine.progress==Integer.MAX_VALUE,"Blocked failure unexpectedly submitted/reset before saving "+recipe.getId());
            for(var input:recipe.inputs)require(machine.getItem(input.slot()).getCount()==input.count(),"Blocked failure consumed an ingredient");
            var row=new CompoundTag();row.putInt("Index",index);row.putString("Recipe",recipe.getId().toString());row.putBoolean("Burnt",burnt);row.putInt("Variant",variant);row.put("Expected",machine.saveWithoutMetadata());audit.snapshots.add(row);index++;
        }
        require(index>0,"No failure persistence cases");audit.prepared=true;audit.setDirty();
        System.out.println("FOODCRAFT FAILURE PERSISTENCE PREPARED cases="+index+" recipes="+recipes.size()+" blocked_variants=3 failed_modes=2 real_save_required=true");
    }
    private static void verify(ServerLevel level){
        var audit=get(level);require(audit.prepared&&!audit.snapshots.isEmpty(),"No saved failure audit");int cases=0;
        for(int i=0;i<audit.snapshots.size();i++){
            var row=audit.snapshots.getCompound(i);int index=row.getInt("Index");level.getChunkAt(pos(index));var machine=machine(level,index);
            require(machine!=null,"Failed-output machine was not loaded after process restart");
            require(row.getCompound("Expected").equals(machine.saveWithoutMetadata()),"Failed-output restart changed state/inventory case="+index);
            var recipe=(MachineRecipe)level.getRecipeManager().byKey(new net.minecraft.resources.ResourceLocation(row.getString("Recipe"))).orElseThrow();
            int outputSlot=recipe.kind.outputs[1];machine.setItem(outputSlot,ItemStack.EMPTY);
            MachineBlockEntity.tick(level,pos(index),machine.getBlockState(),machine);
            var expected=failed(recipe,row.getBoolean("Burnt"));
            require(ItemStack.isSameItemSameTags(machine.getItem(outputSlot),expected)&&machine.getItem(outputSlot).getCount()==1,"Restarted failure produced the wrong output case="+index);
            for(var input:recipe.inputs){
                var remainder=input.ingredient().getItems()[0].getItem().getCraftingRemainingItem();
                int remaining=input.count()-(!row.getBoolean("Burnt")&&input.slot()==recipe.kind.inputs[0]?1:0);
                var actual=machine.getItem(input.slot());
                require(remainder==null||remaining==0?actual.isEmpty():actual.is(remainder)&&actual.getCount()==remaining,"Restarted failure did not consume/return its input exactly once");
            }
            require(machine.progress==0&&machine.proficiency==777,"Failed processing changed progress/skill unexpectedly");
            MachineBlockEntity.tick(level,pos(index),machine.getBlockState(),machine);
            require(machine.getItem(outputSlot).getCount()==1,"Restarted failure duplicated its output");cases++;
        }
        System.out.println("FOODCRAFT FAILURE PERSISTENCE RESTART PASS cases="+cases+" exact_nbt_and_inventory=true failed_output_once=true");
    }
}
