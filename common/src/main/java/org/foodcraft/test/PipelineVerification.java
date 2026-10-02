package org.foodcraft.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import org.foodcraft.FoodCraft;
import org.foodcraft.machine.*;
import org.foodcraft.recipe.MachineRecipe;
import java.lang.reflect.Method;
import java.util.Arrays;

/** Genuine third-party pipe ticks; fixtures never insert into a machine or run its ticker. */
public final class PipelineVerification extends SavedData {
    private static final int BATCH=2;
    private boolean prepared,finished,blocked;
    private long ticks,elapsedMillis,lastNanos;
    private final int[] cycles=new int[8];
    private final String[] recipes=new String[8];
    public static PipelineVerification get(ServerLevel level){return level.getDataStorage().computeIfAbsent(PipelineVerification::load,PipelineVerification::new,"foodcraft_qa_pipeline");}
    private static PipelineVerification load(CompoundTag tag){
        var proof=new PipelineVerification();proof.prepared=tag.getBoolean("Prepared");proof.finished=tag.getBoolean("Finished");proof.blocked=tag.getBoolean("Blocked");proof.ticks=tag.getLong("Ticks");proof.elapsedMillis=tag.getLong("ElapsedMillis");
        for(int i=0;i<8;i++){proof.cycles[i]=tag.getInt("Cycles"+i);proof.recipes[i]=tag.getString("Recipe"+i);}return proof;
    }
    @Override public CompoundTag save(CompoundTag tag){
        tag.putBoolean("Prepared",prepared);tag.putBoolean("Finished",finished);tag.putBoolean("Blocked",blocked);tag.putLong("Ticks",ticks);tag.putLong("ElapsedMillis",elapsedMillis);
        for(int i=0;i<8;i++){tag.putInt("Cycles"+i,cycles[i]);tag.putString("Recipe"+i,recipes[i]==null?"":recipes[i]);}return tag;
    }
    public static BlockPos pos(MachineKind kind){return new BlockPos(6000+kind.ordinal()*12,75,6000);}
    private static BlockPos output(MachineKind kind){return kind.heatedExternally()?pos(kind).east(2):pos(kind).below(2);}
    private static Container chest(ServerLevel level,BlockPos pos){return (Container)level.getBlockEntity(pos);}
    private static void chestAt(ServerLevel level,BlockPos pos){level.setBlock(pos,Blocks.CHEST.defaultBlockState(),3);chest(level,pos).clearContent();}
    private static Object call(Object object,String name,Object... args){
        for(Method method:object.getClass().getMethods())if(method.getName().equals(name)&&method.getParameterCount()==args.length){
            try{return method.invoke(object,args);}catch(IllegalArgumentException incompatible){continue;}catch(ReflectiveOperationException failure){throw new IllegalStateException("Third-party pipe API failed: "+name,failure);}
        }
        throw new IllegalStateException("Third-party pipe API missing: "+name);
    }
    private static String namespace(){return FoodCraft.platform.modLoaded("pipez")?"pipez":FoodCraft.platform.modLoaded("moderndynamics")?"moderndynamics":"";}
    private static void pipe(ServerLevel level,BlockPos pos,boolean fluid,Direction extraction){
        String namespace=namespace();if(namespace.isEmpty())throw new IllegalStateException("Pipeline test requires Pipez or Modern Dynamics");
        var id=new ResourceLocation(namespace,fluid?"fluid_pipe":"item_pipe");var block=BuiltInRegistries.BLOCK.get(id);
        if(block==Blocks.AIR)throw new IllegalStateException("Missing transport block "+id);
        level.setBlock(pos,block.defaultBlockState(),3);
        if(extraction==null)return;
        if(namespace.equals("pipez"))call(block,"setExtracting",level,pos,extraction,true);
        else{
            var entity=level.getBlockEntity(pos);var attachment=BuiltInRegistries.ITEM.get(new ResourceLocation(namespace,"extractor"));
            boolean attached=false;
            Object hosts=call(entity,"getHosts");Iterable<?> iterable=hosts instanceof Object[] array?Arrays.asList(array):(Iterable<?>)hosts;
            for(var host:iterable)if((Boolean)call(host,"acceptsAttachment",attachment,new ItemStack(attachment))){call(host,"setAttachment",extraction,attachment,new CompoundTag());attached=true;break;}
            if(!attached)throw new IllegalStateException("Modern Dynamics extractor refused");
            level.blockUpdated(pos,block);call(entity,"refreshHosts");call(entity,"scheduleHostUpdates");entity.setChanged();call(entity,"sync");
        }
    }
    public static void prepare(ServerLevel level){try{get(level).prepareWorld(level);}catch(RuntimeException failure){failure.printStackTrace();throw failure;}}
    private void prepareWorld(ServerLevel level){
        if(prepared){report(level);return;}
        for(var kind:MachineKind.values()){
            var p=pos(kind);for(int dx=-4;dx<=4;dx++)for(int dy=-3;dy<=4;dy++)for(int dz=-4;dz<=4;dz++)level.setBlock(p.offset(dx,dy,dz),Blocks.AIR.defaultBlockState(),3);
            for(int x=(p.getX()-4)>>4;x<=((p.getX()+4)>>4);x++)for(int z=(p.getZ()-4)>>4;z<=((p.getZ()+4)>>4);z++)level.setChunkForced(x,z,true);
            level.setBlock(p,FoodCraft.BLOCKS.get(kind.id).defaultBlockState(),3);
            if(kind==MachineKind.STOVE){auxiliary(level,p);continue;}
            var recipe=level.getRecipeManager().getAllRecipesFor(FoodCraft.RECIPE_TYPES.get(kind)).stream().filter(r->!r.milk&&!r.cold&&r.inputs.stream().noneMatch(input->input.ingredient().test(new ItemStack(Items.COOKED_CHICKEN)))).sorted(java.util.Comparator.comparing(r->r.getId().toString())).findFirst().orElseThrow();
            recipes[kind.ordinal()]=recipe.getId().toString();
            chestAt(level,p.above(3));pipe(level,p.above(2),false,Direction.UP);pipe(level,p.above(),false,null);
            chestAt(level,output(kind));
            if(kind.heatedExternally()){
                level.setBlock(p.below(),FoodCraft.BLOCKS.get("stove").defaultBlockState(),3);auxiliary(level,p.below());
                ((MachineBlockEntity)level.getBlockEntity(p)).adjustFirePower(Math.max(1,Math.min(100,(recipe.minHeat+recipe.maxHeat)/Math.max(1,recipe.time/17))));
                pipe(level,p.east(),false,Direction.WEST);
            }else pipe(level,p.below(),false,Direction.UP);
            if(kind.fuelSlot>=0||kind==MachineKind.CUTTING_BOARD||kind==MachineKind.DEEP_FRYER)auxiliary(level,p);
            if(kind.liquidSlot>=0&&kind!=MachineKind.DEEP_FRYER){
                chestAt(level,p.north(2));QaWaterSources.get(level).set(p.north(2),1000L*MachineBlockEntity.LIQUID_UNIT);pipe(level,p.north(),true,Direction.NORTH);
            }
            fillBatch(level,kind,recipe);
            var sink=chest(level,output(kind));for(int i=0;i<sink.getContainerSize();i++)sink.setItem(i,new ItemStack(Items.BARRIER,64));
        }
        prepared=true;blocked=true;lastNanos=System.nanoTime();setDirty();
        System.out.println("FOODCRAFT PIPELINE PREPARED transport="+namespace()+" machines=9 output_blocked=true");
    }
    private static void auxiliary(ServerLevel level,BlockPos p){
        chestAt(level,p.west(2));pipe(level,p.west(),false,Direction.WEST);var source=chest(level,p.west(2));
        source.setItem(0,new ItemStack(Items.COAL,64));source.setItem(1,new ItemStack(FoodCraft.item("caidao")));source.setItem(2,new ItemStack(FoodCraft.item("huashenyou"),64));
        for(int i=3;i<source.getContainerSize();i++)source.setItem(i,new ItemStack(FoodCraft.item("caidao")));
    }
    private static void restockAuxiliary(ServerLevel level,BlockPos p){
        var source=chest(level,p.west(2));
        if(source.getItem(0).isEmpty())source.setItem(0,new ItemStack(Items.COAL,64));
        if(source.getItem(2).isEmpty())source.setItem(2,new ItemStack(FoodCraft.item("huashenyou"),64));
        for(int i=1;i<source.getContainerSize();i++)if(i!=2&&source.getItem(i).isEmpty())source.setItem(i,new ItemStack(FoodCraft.item("caidao")));
    }
    private static void fillBatch(ServerLevel level,MachineKind kind,MachineRecipe recipe){
        var source=chest(level,pos(kind).above(3));if(!source.isEmpty())throw new IllegalStateException("Previous pipe batch still has source inputs "+kind);
        for(int i=0;i<recipe.inputs.size();i++){var input=recipe.inputs.get(i);var stack=input.ingredient().getItems()[0].copyWithCount(input.count()*BATCH);if(stack.getCount()>stack.getMaxStackSize())throw new IllegalStateException("QA batch exceeds stack size");source.setItem(i,stack);}
    }
    public static void tick(ServerLevel level){if(!Boolean.getBoolean("foodcraft.qa.server")||!level.dimension().equals(Level.OVERWORLD))return;StressFixture.retainDrops(level);get(level).advance(level);LoadStressFixture.tick(level);}
    public static void restartSoak(ServerLevel level){
        var proof=get(level);if(!proof.prepared)throw new IllegalStateException("Prepare the pipeline before restarting its clock");
        proof.finished=false;proof.ticks=0;proof.elapsedMillis=0;proof.lastNanos=0;Arrays.fill(proof.cycles,0);proof.setDirty();
        for(var kind:MachineKind.values()){
            var p=pos(kind);for(int x=(p.getX()-4)>>4;x<=((p.getX()+4)>>4);x++)for(int z=(p.getZ()-4)>>4;z<=((p.getZ()+4)>>4);z++)level.setChunkForced(x,z,true);
        }
        System.out.println("FOODCRAFT PIPELINE SOAK CLOCK RESTARTED inventory_and_progress_preserved=true");
    }
    private void advance(ServerLevel level){
        if(!prepared||finished)return;
        if(lastNanos==0){
            // Forge rebuilds ticket ownership at startup; re-establish this opt-in test's tickets.
            for(var kind:MachineKind.values()){
                var p=pos(kind);for(int x=(p.getX()-4)>>4;x<=((p.getX()+4)>>4);x++)for(int z=(p.getZ()-4)>>4;z<=((p.getZ()+4)>>4);z++)level.setChunkForced(x,z,true);
            }
            System.out.println("FOODCRAFT PIPELINE TEST CHUNKS RESTORED transport="+namespace());
        }
        long now=System.nanoTime();if(lastNanos!=0)elapsedMillis+=(now-lastNanos)/1_000_000;lastNanos=now;ticks++;
        if(ticks%20!=0)return;
        for(var kind:MachineKind.values()){
            if(kind==MachineKind.STOVE||kind.fuelSlot>=0||kind==MachineKind.CUTTING_BOARD||kind==MachineKind.DEEP_FRYER)restockAuxiliary(level,pos(kind));
            if(kind.heatedExternally())restockAuxiliary(level,pos(kind).below());
        }
        if(blocked&&ticks>=600){
            for(var kind:MachineKind.values())if(kind!=MachineKind.STOVE){
                var machine=(MachineBlockEntity)level.getBlockEntity(pos(kind));var recipe=(MachineRecipe)level.getRecipeManager().byKey(new ResourceLocation(recipes[kind.ordinal()])).orElseThrow();
                int produced=0;for(int slot:kind.outputs)produced+=machine.getItem(slot).getCount();
                if(produced>recipe.result.getCount()*BATCH)throw new IllegalStateException("Output blockage duplicated a pipe batch "+kind);
                chest(level,output(kind)).clearContent();
            }
            blocked=false;System.out.println("FOODCRAFT PIPELINE BLOCKAGE PASS transport="+namespace()+" ticks="+ticks);
        }
        if(!blocked)for(var kind:MachineKind.values())if(kind!=MachineKind.STOVE){
            var recipe=(MachineRecipe)level.getRecipeManager().byKey(new ResourceLocation(recipes[kind.ordinal()])).orElseThrow();var sink=chest(level,output(kind));int count=0;
            for(int slot=0;slot<sink.getContainerSize();slot++){var stack=sink.getItem(slot);if(!stack.isEmpty()&&!ItemStack.isSameItemSameTags(stack,recipe.result))throw new IllegalStateException("Wrong native pipe product "+kind+": "+stack);count+=stack.getCount();}
            int expected=recipe.result.getCount()*BATCH;if(count>expected)throw new IllegalStateException("Native pipe output duplicated "+kind+": "+count+">"+expected);
            if(count==expected){
                var machine=(MachineBlockEntity)level.getBlockEntity(pos(kind));
                if(!chest(level,pos(kind).above(3)).isEmpty()||Arrays.stream(kind.inputs).anyMatch(slot->!machine.getItem(slot).isEmpty()))throw new IllegalStateException("Native pipe recipe produced before consuming its batch "+kind);
                for(int slot:kind.outputs)if(!machine.getItem(slot).isEmpty())throw new IllegalStateException("Native pipe leftover products "+kind);
                sink.clearContent();cycles[kind.ordinal()]++;fillBatch(level,kind,recipe);
            }
        }
        if(ticks%1200==0)report(level);
        if(elapsedMillis>=Long.getLong("foodcraft.qa.soakMillis",1_800_000L)&&Arrays.stream(cycles).allMatch(n->n>0)){finished=true;System.out.println("FOODCRAFT PIPELINE SOAK PASS transport="+namespace()+" elapsed_ms="+elapsedMillis+" ticks="+ticks+" cycles="+Arrays.toString(cycles));}
        setDirty();
    }
    public static void status(ServerLevel level){get(level).report(level);}
    private void report(ServerLevel level){System.out.println("FOODCRAFT PIPELINE STATUS transport="+namespace()+" elapsed_ms="+elapsedMillis+" ticks="+ticks+" cycles="+Arrays.toString(cycles)+" finished="+finished);}
}
