package org.foodcraft.test;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import org.foodcraft.FoodCraft;
import org.foodcraft.machine.*;
import org.foodcraft.recipe.MachineRecipe;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.List;

/** Concurrent real world ticking of every baseline recipe; separate from the real-pipe test. */
public final class LoadStressFixture extends SavedData {
    private boolean prepared,checkpoint,resuming,done;
    private String[] ids=new String[0];
    private int[] cycles=new int[0],baseline=new int[0];
    private ListTag snapshots=new ListTag();
    private long ticks,lastNanos,elapsedMillis;
    private boolean tickets;
    private LoadStressFixture(){}
    private static LoadStressFixture get(ServerLevel level){return level.getDataStorage().computeIfAbsent(LoadStressFixture::load,LoadStressFixture::new,"foodcraft_load_audit");}
    private static BlockPos pos(int index){return new BlockPos(12000+index%12*4,75,12000+index/12*4);}
    private static LoadStressFixture load(CompoundTag tag){
        var p=new LoadStressFixture();p.prepared=tag.getBoolean("Prepared");p.checkpoint=tag.getBoolean("Checkpoint");p.resuming=tag.getBoolean("Resuming");p.done=tag.getBoolean("Done");
        var entries=tag.getList("Recipes",10);p.ids=new String[entries.size()];p.cycles=new int[entries.size()];p.baseline=new int[entries.size()];
        for(int i=0;i<entries.size();i++){var row=entries.getCompound(i);p.ids[i]=row.getString("Id");p.cycles[i]=row.getInt("Cycles");p.baseline[i]=row.getInt("Baseline");}
        p.snapshots=tag.getList("Snapshots",10).copy();p.ticks=tag.getLong("Ticks");p.elapsedMillis=tag.getLong("ElapsedMillis");return p;
    }
    @Override public CompoundTag save(CompoundTag tag){
        tag.putBoolean("Prepared",prepared);tag.putBoolean("Checkpoint",checkpoint);tag.putBoolean("Resuming",resuming);tag.putBoolean("Done",done);
        var entries=new ListTag();for(int i=0;i<ids.length;i++){var row=new CompoundTag();row.putString("Id",ids[i]);row.putInt("Cycles",cycles[i]);row.putInt("Baseline",baseline[i]);entries.add(row);}
        tag.put("Recipes",entries);tag.put("Snapshots",snapshots.copy());tag.putLong("Ticks",ticks);tag.putLong("ElapsedMillis",elapsedMillis);return tag;
    }
    private static MachineRecipe recipe(ServerLevel level,String id){return (MachineRecipe)level.getRecipeManager().byKey(new ResourceLocation(id)).orElseThrow();}
    private static MachineBlockEntity machine(ServerLevel level,int index){return (MachineBlockEntity)level.getBlockEntity(pos(index));}
    private static void fill(ServerLevel level,int index,MachineRecipe recipe){
        var m=machine(level,index);m.clearContent();
        for(var input:recipe.inputs)m.setItem(input.slot(),input.ingredient().getItems()[0].copyWithCount(input.count()));
        if(m.kind==MachineKind.CUTTING_BOARD)m.setItem(0,new ItemStack(FoodCraft.item("caidao")));
        if(m.kind.fuelSlot>=0)m.setItem(m.kind.fuelSlot,new ItemStack(Items.COAL,64));
        if(m.kind==MachineKind.DRINK_MAKER&&recipe.cold)m.setItem(4,new ItemStack(Blocks.ICE,64));
        m.liquid=8;m.liquidFraction=0;m.milk=recipe.milk;
        if(m.kind.heatedExternally()){
            var stove=(MachineBlockEntity)level.getBlockEntity(pos(index).below());stove.setItem(0,new ItemStack(Items.COAL,64));
            m.adjustFirePower(Math.max(1,Math.min(100,(recipe.minHeat+recipe.maxHeat)/Math.max(1,recipe.time/17))));
        }
        m.setChanged();
    }
    public static void prepare(ServerLevel level){
        var p=get(level);if(p.prepared)throw new IllegalStateException("Load audit already prepared");
        var recipes=FoodCraft.RECIPE_TYPES.values().stream().flatMap(type->level.getRecipeManager().getAllRecipesFor(type).stream()).sorted(java.util.Comparator.comparing(r->r.getId().toString())).toList();
        if(recipes.size()!=118)throw new IllegalStateException("Load fixture requires all 118 unmodified baseline recipes");
        p.ids=recipes.stream().map(r->r.getId().toString()).toArray(String[]::new);p.cycles=new int[118];p.baseline=new int[118];
        for(int i=0;i<118;i++){
            var pos=pos(i);level.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true);
            var r=recipes.get(i);level.setBlock(pos,FoodCraft.BLOCKS.get(r.kind.id).defaultBlockState(),3);
            if(r.kind.heatedExternally())level.setBlock(pos.below(),FoodCraft.BLOCKS.get("stove").defaultBlockState(),3);
            fill(level,i,r);
        }
        p.prepared=true;p.setDirty();System.out.println("FOODCRAFT LOAD AUDIT PREPARED recipes=118 simultaneous_machines=118 real_world_ticks=true");
    }
    public static void tick(ServerLevel level){
        var p=get(level);if(!p.prepared||p.done)return;
        if(!p.tickets){for(int i=0;i<p.ids.length;i++){var pos=pos(i);level.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true);}p.tickets=true;}
        if(p.checkpoint)return;
        long now=System.nanoTime();if(p.lastNanos!=0)p.elapsedMillis+=(now-p.lastNanos)/1000000;p.lastNanos=now;p.ticks++;
        if(p.ticks%20==0){
            p.collect(level);
            if(p.resuming){boolean all=true;for(int i=0;i<p.cycles.length;i++)all&=p.cycles[i]>p.baseline[i];
                if(all){p.done=true;System.out.println("FOODCRAFT LOAD AUDIT RESTART PROCESSING PASS recipes=118 each_new_batch=true");}}
        }
        if(p.ticks%1200==0)p.report(level);p.setDirty();
    }
    private void collect(ServerLevel level){
        for(int i=0;i<ids.length;i++){
            var r=recipe(level,ids[i]);var m=machine(level,i);var output=m.getItem(m.kind.outputs[0]);
            if(m.kind.outputs.length>1&&!m.getItem(m.kind.outputs[1]).isEmpty())throw new IllegalStateException("Load audit cooking failed "+ids[i]);
            if(output.isEmpty())continue;
            if(!ItemStack.isSameItemSameTags(output,r.result)||output.getCount()!=r.result.getCount())throw new IllegalStateException("Load output duplicated or changed "+ids[i]);
            for(var input:r.inputs){var remainder=input.ingredient().getItems()[0].getItem().getCraftingRemainingItem();var left=m.getItem(input.slot());
                if(remainder==null?!left.isEmpty():!left.is(remainder)||left.getCount()!=input.count())throw new IllegalStateException("Load input conservation failed "+ids[i]);}
            if(m.liquid!=8-r.water)throw new IllegalStateException("Load liquid conservation failed "+ids[i]);
            cycles[i]++;fill(level,i,r);
        }
    }
    public static void checkpoint(ServerLevel level){
        var p=get(level);p.collect(level);p.snapshots=new ListTag();p.baseline=p.cycles.clone();
        for(int i=0;i<p.ids.length;i++){
            var m=machine(level,i);m.setItem(m.kind.outputs[0],new ItemStack(Items.BARRIER,64));
            p.snapshots.add(m.saveWithoutMetadata());
        }
        p.checkpoint=true;p.setDirty();p.report(level);
        if(Arrays.stream(p.cycles).min().orElse(0)<1)throw new IllegalStateException("Not every load recipe completed");
        System.out.println("FOODCRAFT LOAD AUDIT CHECKPOINT PASS recipes=118 input_output_liquid_conserved=true");
    }
    public static void resume(ServerLevel level){
        var p=get(level);if(!p.checkpoint||p.snapshots.size()!=118)throw new IllegalStateException("No load checkpoint");
        for(int i=0;i<p.ids.length;i++){
            var m=machine(level,i);var old=p.snapshots.getCompound(i);var current=m.saveWithoutMetadata();
            for(String field:List.of("Progress","Liquid","LiquidFraction","Proficiency","AccumulatedHeat","FirePower"))
                if(old.getInt(field)!=current.getInt(field))throw new IllegalStateException("Load restart lost "+field+" "+p.ids[i]);
            if(!old.getList("Items",10).equals(current.getList("Items",10)))throw new IllegalStateException("Load restart changed inventory "+p.ids[i]);
            m.setItem(m.kind.outputs[0],ItemStack.EMPTY);
        }
        p.checkpoint=false;p.resuming=true;p.lastNanos=0;p.setDirty();
        System.out.println("FOODCRAFT LOAD AUDIT RESTART STATE PASS recipes=118 fields_and_inventory=true");
    }
    private void report(ServerLevel level){
        long used=ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
        long[] times=level.getServer().tickTimes.clone();Arrays.sort(times);double p95=times[(int)(times.length*.95)-1]/1000000.0;
        System.out.println("FOODCRAFT LOAD AUDIT STATUS elapsed_ms="+elapsedMillis+" ticks="+ticks+" min_cycles="+Arrays.stream(cycles).min().orElse(0)+" total_cycles="+Arrays.stream(cycles).asLongStream().sum()+" heap_used="+used+" average_tick_ms="+level.getServer().getAverageTickTime()+" p95_tick_ms="+p95);
    }
}
