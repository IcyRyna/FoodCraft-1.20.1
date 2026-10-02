package org.foodcraft.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.foodcraft.FoodCraft;
import org.foodcraft.item.KitchenKnifeItem;
import org.foodcraft.recipe.MachineRecipe;
import org.foodcraft.core.InventoryTransaction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;

/** All state mutation is on the server; a processing commit replaces a validated snapshot. */
public class MachineBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {
    public static final int LIQUID_UNIT=81000,DATA_FIELDS=18;
    public final MachineKind kind;
    private NonNullList<ItemStack> items;
    public int progress, totalTime, burnTime, initialBurnTime, coldTime, liquid, proficiency, firePower=50;
    public boolean milk;
    public int accumulatedHeat,liquidFraction;
    private ResourceLocation activeRecipe;
    private String activeRecipeFingerprint="";
    private MachineRecipe cachedRecipe;
    private final ContainerData data=new ContainerData(){
        @Override public int get(int index){return switch(index){
            case 0->progress&65535;case 1->totalTime&65535;case 2->burnTime&65535;case 3->burnTime>>>16;
            case 4->liquid;case 5->milk?1:0;case 6->firePower;case 7->proficiency;case 8->Math.min(1000,accumulatedHeat);
            case 9->Math.min(2500,coldTime);case 10->initialBurnTime&65535;case 11->initialBurnTime>>>16;
            case 12->cachedRecipe==null?0:Math.min(1000,cachedRecipe.minHeat);case 13->cachedRecipe==null?0:Math.min(1000,cachedRecipe.maxHeat);
            case 14->progress>>>16;case 15->totalTime>>>16;case 16->liquidFraction&65535;case 17->liquidFraction>>>16;default->0;};}
        @Override public void set(int index,int value){switch(index){
            case 0->progress=(progress&0xffff0000)|(value&65535);case 1->totalTime=(totalTime&0xffff0000)|(value&65535);case 2->burnTime=(burnTime&0xffff0000)|(value&65535);
            case 3->burnTime=(burnTime&65535)|((value&65535)<<16);case 4->liquid=value;case 5->milk=value!=0;
            case 6->firePower=value;case 7->proficiency=value;case 8->accumulatedHeat=value;case 9->coldTime=value;
            case 10->initialBurnTime=(initialBurnTime&0xffff0000)|(value&65535);case 11->initialBurnTime=(initialBurnTime&65535)|((value&65535)<<16);
            case 14->progress=(progress&65535)|((value&65535)<<16);case 15->totalTime=(totalTime&65535)|((value&65535)<<16);
            case 16->liquidFraction=(liquidFraction&0xffff0000)|(value&65535);case 17->liquidFraction=(liquidFraction&65535)|((value&65535)<<16);
            default->throw new IllegalArgumentException("Unknown machine data field "+index);}}
        @Override public int getCount(){return DATA_FIELDS;}
    };

    public MachineBlockEntity(BlockPos pos,BlockState state){
        super(FoodCraft.MACHINE_ENTITY,pos,state);
        kind=((MachineBlock)state.getBlock()).kind;
        items=NonNullList.withSize(kind.size,ItemStack.EMPTY);totalTime=kind.ticks;
    }
    public ContainerData data(){return data;}
    public static void tick(Level level,BlockPos pos,BlockState state,MachineBlockEntity machine){
        if(level.isClientSide||machine.isRemoved())return;
        machine.serverTick();
    }
    private void serverTick(){
        if(burnTime>0)burnTime--;
        if(coldTime>0)coldTime--;
        loadLiquid();
        if(kind==MachineKind.STOVE){
            if(burnTime==0)consumeFuel(20);
            updateLit(burnTime>0);
            super.setChanged();return;
        }
        MachineRecipe recipe=findRecipe();
        if(recipe==null||liquid<recipe.water||recipe.water>0&&milk!=recipe.milk||
                kind==MachineKind.CUTTING_BOARD&&!(items.get(0).getItem() instanceof KitchenKnifeItem)){
            resetProgress();updateLit(false);super.setChanged();return;
        }
        ItemStack result=recipe.result.copy();
        if(kind==MachineKind.CUTTING_BOARD&&((KitchenKnifeItem)items.get(0).getItem()).extraYield)result.grow(1);
        if(!recipe.getId().equals(activeRecipe)||!activeRecipeFingerprint.isEmpty()&&!recipe.fingerprint.equals(activeRecipeFingerprint)){
            resetProgress();activeRecipe=recipe.getId();
        }
        activeRecipeFingerprint=recipe.fingerprint;totalTime=recipe.time;
        if(plan(recipe,result,kind.outputs[0]).isEmpty()){
            updateLit(false);super.setChanged();return;
        }
        boolean powered;
        if(kind.heatedExternally()){
            BlockEntity source=level.getBlockEntity(worldPosition.below());
            powered=source instanceof MachineBlockEntity stove&&stove.kind==MachineKind.STOVE&&stove.burnTime>0;
        }else if(kind==MachineKind.FERMENTING_BARREL||kind==MachineKind.CUTTING_BOARD){powered=true;}
        else if(kind==MachineKind.DRINK_MAKER&&recipe.cold){
            if(coldTime==0&&items.get(4).is(Blocks.ICE.asItem())){items.get(4).shrink(1);coldTime=2500;}
            powered=coldTime>0;
        }else{if(burnTime==0)consumeFuel(1);powered=burnTime>0;}
        updateLit(powered);
        if(!powered){resetProgress();super.setChanged();return;}
        if(progress<Integer.MAX_VALUE)progress++;
        if(kind.heatedExternally()&&progress%17==0)accumulatedHeat=(int)Math.min(Integer.MAX_VALUE,(long)accumulatedHeat+firePower/2);
        if(kind.heatedExternally()&&accumulatedHeat>recipe.maxHeat){
            if(commit(recipe,new ItemStack(Items.COAL),kind.outputs[1]))resetProgress();
        }else if(progress>=totalTime){
            if(kind.heatedExternally()&&accumulatedHeat<recipe.minHeat){
                // Legacy undercooked batches return one of the first ingredients.
                int refundedSlot=recipe.inputs.stream().mapToInt(MachineRecipe.Input::slot).min().orElseThrow();
                ItemStack failed=items.get(refundedSlot).copyWithCount(1);
                if(commit(recipe,failed,kind.outputs[1],refundedSlot))resetProgress();
            }else if(commit(recipe,result,kind.outputs[0])){
                if(kind.heatedExternally())proficiency=Math.min(3000,proficiency+1);
                resetProgress();
            }
        }
        super.setChanged();
    }
    private void resetProgress(){progress=0;accumulatedHeat=0;activeRecipe=null;activeRecipeFingerprint="";}
    private MachineRecipe findRecipe(){
        var manager=level.getRecipeManager();
        if(cachedRecipe!=null&&cachedRecipe.matches(this,level)&&manager.byKey(cachedRecipe.getId()).orElse(null)==cachedRecipe)return cachedRecipe;
        cachedRecipe=manager.getAllRecipesFor(FoodCraft.RECIPE_TYPES.get(kind)).stream()
                .filter(recipe->recipe.matches(this,level)&&(!kind.equals(MachineKind.DRINK_MAKER)||recipe.water==0||milk==recipe.milk))
                .sorted(java.util.Comparator.comparing(recipe->recipe.getId().toString())).findFirst().orElse(null);
        return cachedRecipe;
    }
    private void consumeFuel(int multiplier){
        if(kind.fuelSlot<0)return;
        ItemStack fuel=items.get(kind.fuelSlot);
        int duration=FoodCraft.platform.fuelTime(fuel);
        if(duration<=0)return;
        Item remainder=fuel.getItem().getCraftingRemainingItem();
        if(remainder!=null&&fuel.getCount()>1)return;
        fuel.shrink(1);if(fuel.isEmpty()&&remainder!=null)items.set(kind.fuelSlot,new ItemStack(remainder));
        burnTime=initialBurnTime=(int)Math.min(Integer.MAX_VALUE,(long)duration*multiplier);
        super.setChanged();
    }
    private void loadLiquid(){
        if(kind.liquidSlot<0||8L*LIQUID_UNIT-liquidVolume()<LIQUID_UNIT)return;
        ItemStack source=items.get(kind.liquidSlot);
        if(source.isEmpty())return;
        boolean incomingMilk=source.is(Items.MILK_BUCKET);
        boolean oil=kind==MachineKind.DEEP_FRYER;
        boolean valid=oil?source.is(FoodCraft.item("huashenyou")):
                source.is(FoodCraft.item("water"))||source.is(Items.WATER_BUCKET)||
                source.is(Items.POTION)&&PotionUtils.getPotion(source)==Potions.WATER||
                incomingMilk&&kind==MachineKind.DRINK_MAKER;
        if(!valid||!oil&&liquidVolume()>0&&milk!=incomingMilk)return;
        Item remainder=source.is(Items.POTION)?Items.GLASS_BOTTLE:source.getItem().getCraftingRemainingItem();
        if(remainder!=null&&source.getCount()>1)return;
        source.shrink(1);if(source.isEmpty()&&remainder!=null)items.set(kind.liquidSlot,new ItemStack(remainder));
        liquid++;milk=incomingMilk;super.setChanged();
    }
    private static InventoryTransaction.Stack value(ItemStack stack){
        if(stack.isEmpty())return InventoryTransaction.Stack.EMPTY;
        String id=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return new InventoryTransaction.Stack(id,stack.hasTag()?stack.getTag().toString():"",stack.getCount(),stack.getMaxStackSize());
    }
    private List<InventoryTransaction.Stack> snapshot(){return items.stream().map(MachineBlockEntity::value).toList();}
    private java.util.Optional<InventoryTransaction.Plan> plan(MachineRecipe recipe,ItemStack result,int output){
        return plan(recipe,result,output,-1);
    }
    private java.util.Optional<InventoryTransaction.Plan> plan(MachineRecipe recipe,ItemStack result,int output,int refundedSlot){
        if(result.isEmpty()||!kind.output(output)||result.getCount()>result.getMaxStackSize())return java.util.Optional.empty();
        List<InventoryTransaction.Demand> demands=new ArrayList<>();
        List<InventoryTransaction.Addition> additions=new ArrayList<>();
        for(var input:recipe.inputs){
            ItemStack stack=items.get(input.slot());
            if(!input.ingredient().test(stack))return java.util.Optional.empty();
            var key=value(stack);demands.add(new InventoryTransaction.Demand(input.slot(),key.item(),key.data(),input.count()));
            Item remainder=stack.getItem().getCraftingRemainingItem();
            int destroyed=input.count()-(input.slot()==refundedSlot?1:0);
            // A refunded full container still owns its vessel. Return empty
            // containers only for the units actually destroyed by this failure.
            if(remainder!=null&&destroyed>0)additions.add(new InventoryTransaction.Addition(input.slot(),value(new ItemStack(remainder,destroyed))));
        }
        additions.add(new InventoryTransaction.Addition(output,value(result)));
        Set<Integer> exclusive=new HashSet<>();for(int slot:recipe.exclusive)exclusive.add(slot);
        return InventoryTransaction.prepare(snapshot(),demands,exclusive,additions);
    }
    public boolean commit(MachineRecipe recipe,ItemStack result,int output){
        return commit(recipe,result,output,-1);
    }
    private boolean commit(MachineRecipe recipe,ItemStack result,int output,int refundedSlot){
        if(level==null||level.isClientSide||isRemoved()||recipe.kind!=kind||!recipe.matches(this,level)||liquid<recipe.water||recipe.water>0&&milk!=recipe.milk)return false;
        if(kind==MachineKind.CUTTING_BOARD&&!(items.get(0).getItem() instanceof KitchenKnifeItem))return false;
        var prepared=plan(recipe,result,output,refundedSlot);if(prepared.isEmpty())return false;
        var transaction=prepared.get();if(!transaction.stillValid(snapshot()))return false;
        for(var input:recipe.inputs){
            ItemStack source=items.get(input.slot());Item remainder=source.getItem().getCraftingRemainingItem();
            int destroyed=input.count()-(input.slot()==refundedSlot?1:0);
            source.shrink(input.count());if(remainder!=null&&destroyed>0&&source.isEmpty())items.set(input.slot(),new ItemStack(remainder,destroyed));
        }
        if(items.get(output).isEmpty())items.set(output,result.copy());else items.get(output).grow(result.getCount());
        if(kind==MachineKind.CUTTING_BOARD){
            ItemStack knife=items.get(0);
            if(knife.getDamageValue()>=knife.getMaxDamage()-1)items.set(0,ItemStack.EMPTY);
            else knife.setDamageValue(knife.getDamageValue()+1);
        }
        liquid-=recipe.water;if(liquid==0)milk=false;super.setChanged();return true;
    }
    private void updateLit(boolean lit){
        BlockState state=getBlockState();if(state.getValue(MachineBlock.LIT)!=lit)level.setBlock(worldPosition,state.setValue(MachineBlock.LIT,lit),3);
    }
    public void adjustFirePower(int value){firePower=Math.max(0,Math.min(100,value));super.setChanged();}
    public int fillWater(int units,boolean simulate){
        if(kind.liquidSlot<0||kind==MachineKind.DEEP_FRYER||milk||units<=0)return 0;
        int accepted=(int)Math.min(units,(8L*LIQUID_UNIT-liquidVolume())/LIQUID_UNIT);
        if(!simulate&&accepted>0){liquid+=accepted;super.setChanged();}return accepted;
    }
    public long liquidVolume(){return Math.max(0,Math.min(8L*LIQUID_UNIT,(long)liquid*LIQUID_UNIT+liquidFraction));}
    /** Exact common volume: one legacy unit = one bucket = 81000 Fabric droplets = 1000 Forge mB. */
    public long fillWaterVolume(long amount,boolean simulate){
        if(isRemoved()||kind.liquidSlot<0||kind==MachineKind.DEEP_FRYER||milk||amount<=0)return 0;
        long accepted=Math.min(amount,8L*LIQUID_UNIT-liquidVolume());
        if(!simulate&&accepted>0){long total=liquidVolume()+accepted;liquid=(int)(total/LIQUID_UNIT);liquidFraction=(int)(total%LIQUID_UNIT);super.setChanged();}
        return accepted;
    }
    @Override protected void saveAdditional(CompoundTag tag){
        super.saveAdditional(tag);ContainerHelper.saveAllItems(tag,items);tag.putInt("DataVersion",2);
        tag.putInt("Progress",progress);tag.putInt("TotalTime",totalTime);tag.putInt("BurnTime",burnTime);tag.putInt("InitialBurnTime",initialBurnTime);
        tag.putInt("ColdTime",coldTime);tag.putInt("Liquid",liquid);tag.putInt("LiquidFraction",liquidFraction);tag.putBoolean("Milk",milk);tag.putInt("Proficiency",proficiency);
        tag.putInt("FirePower",firePower);tag.putInt("AccumulatedHeat",accumulatedHeat);if(activeRecipe!=null)tag.putString("ActiveRecipe",activeRecipe.toString());
        if(!activeRecipeFingerprint.isEmpty())tag.putString("ActiveRecipeFingerprint",activeRecipeFingerprint);
    }
    @Override public void load(CompoundTag tag){
        super.load(tag);items=NonNullList.withSize(kind.size,ItemStack.EMPTY);ContainerHelper.loadAllItems(tag,items);
        for(int i=0;i<items.size();i++){ItemStack stack=items.get(i);if(stack.getCount()>stack.getMaxStackSize())stack.setCount(stack.getMaxStackSize());}
        progress=Math.max(0,tag.getInt("Progress"));totalTime=Math.max(0,tag.getInt("TotalTime"));burnTime=Math.max(0,tag.getInt("BurnTime"));
        initialBurnTime=Math.max(0,tag.getInt("InitialBurnTime"));coldTime=Math.max(0,tag.getInt("ColdTime"));liquid=Math.max(0,Math.min(8,tag.getInt("Liquid")));
        long volume=Math.min(8L*LIQUID_UNIT,(long)liquid*LIQUID_UNIT+Math.max(0,tag.getInt("LiquidFraction")));
        liquid=(int)(volume/LIQUID_UNIT);liquidFraction=(int)(volume%LIQUID_UNIT);
        milk=liquid>0&&tag.getBoolean("Milk");proficiency=Math.max(0,Math.min(3000,tag.getInt("Proficiency")));
        firePower=tag.contains("FirePower")?Math.max(0,Math.min(100,tag.getInt("FirePower"))):50;
        accumulatedHeat=Math.max(0,tag.getInt("AccumulatedHeat"));
        String recipeId=tag.getString("ActiveRecipe");
        activeRecipe=recipeId.isBlank()?null:ResourceLocation.tryParse(recipeId);
        if(activeRecipe!=null&&activeRecipe.getPath().isEmpty())activeRecipe=null;
        cachedRecipe=null;
        activeRecipeFingerprint=tag.getString("ActiveRecipeFingerprint");
    }
    public void saveToItem(ItemStack stack){var tag=new CompoundTag();saveAdditional(tag);stack.addTagElement("BlockEntityTag",tag);}
    @Override public CompoundTag getUpdateTag(){return saveWithoutMetadata();}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override public int getContainerSize(){return items.size();}
    @Override public boolean isEmpty(){return items.stream().allMatch(ItemStack::isEmpty);}
    @Override public ItemStack getItem(int slot){return slot>=0&&slot<items.size()?items.get(slot):ItemStack.EMPTY;}
    @Override public ItemStack removeItem(int slot,int count){var result=ContainerHelper.removeItem(items,slot,count);if(!result.isEmpty())setChanged();return result;}
    @Override public ItemStack removeItemNoUpdate(int slot){return ContainerHelper.takeItem(items,slot);}
    @Override public void setItem(int slot,ItemStack stack){items.set(slot,stack);if(stack.getCount()>stack.getMaxStackSize())stack.setCount(stack.getMaxStackSize());setChanged();}
    @Override public void setChanged(){cachedRecipe=null;super.setChanged();}
    @Override public boolean stillValid(Player player){return level!=null&&level.getBlockEntity(worldPosition)==this&&player.distanceToSqr(worldPosition.getX()+0.5,worldPosition.getY()+0.5,worldPosition.getZ()+0.5)<=64;}
    @Override public void clearContent(){items.clear();super.setChanged();}
    @Override public boolean canPlaceItem(int slot,ItemStack stack){
        return !isRemoved()&&accepts(kind,slot,stack);
    }
    public static boolean accepts(MachineKind kind,int slot,ItemStack stack){
        if(kind.output(slot))return false;
        if(kind==MachineKind.CUTTING_BOARD&&slot==0)return stack.getItem() instanceof KitchenKnifeItem;
        if(slot==kind.fuelSlot)return FoodCraft.platform.fuelTime(stack)>0;
        if(kind==MachineKind.DRINK_MAKER&&slot==4)return stack.is(Blocks.ICE.asItem());
        if(kind==MachineKind.FRYING_PAN&&slot==3)return false;
        if(slot==kind.liquidSlot)return kind==MachineKind.DEEP_FRYER?stack.is(FoodCraft.item("huashenyou")):
                stack.is(FoodCraft.item("water"))||stack.is(Items.WATER_BUCKET)||stack.is(Items.POTION)&&PotionUtils.getPotion(stack)==Potions.WATER||kind==MachineKind.DRINK_MAKER&&stack.is(Items.MILK_BUCKET);
        return kind.input(slot);
    }
    @Override public int[] getSlotsForFace(Direction side){
        if(side==Direction.UP)return kind.inputs.clone();
        if(side==Direction.DOWN)return java.util.stream.IntStream.range(0,kind.size).toArray();
        // A stove occupies the bottom of a heated pan/pot, so their output also has a side port.
        if(kind.heatedExternally())return kind.outputs.clone();
        return java.util.stream.IntStream.range(0,kind.size).filter(i->!kind.input(i)&&!kind.output(i)).toArray();
    }
    @Override public boolean canPlaceItemThroughFace(int slot,ItemStack stack,Direction side){
        if(isRemoved()||side==Direction.DOWN||!canPlaceItem(slot,stack))return false;
        if(side!=Direction.UP)return !kind.input(slot)&&!kind.output(slot);
        return automaticInsertionLimit(slot,stack)>0;
    }
    public int automaticInsertionLimit(int slot,ItemStack stack){
        if(isRemoved()||!kind.input(slot)||level==null||!canPlaceItem(slot,stack))return 0;
        int best=0;
        // Route ingredients by their ordered recipe role, independent of hopper delivery order.
        for(var recipe:level.getRecipeManager().getAllRecipesFor(FoodCraft.RECIPE_TYPES.get(kind))){
            var target=recipe.inputs.stream().filter(input->input.slot()==slot&&input.ingredient().test(stack)).findFirst().orElse(null);
            if(target==null)continue;
            boolean compatible=true;
            for(var input:recipe.inputs)if(!items.get(input.slot()).isEmpty()&&!input.ingredient().test(items.get(input.slot()))){compatible=false;break;}
            if(!compatible)continue;
            for(int exclusive:recipe.exclusive)if(recipe.inputs.stream().noneMatch(input->input.slot()==exclusive)&&!items.get(exclusive).isEmpty()){compatible=false;break;}
            if(!compatible)continue;
            // Repeated ingredients must not accumulate in one slot while another is empty.
            int allowed=Math.max(0,stack.getMaxStackSize()-items.get(slot).getCount());
            boolean repeated=false;
            for(var input:recipe.inputs)if(input.slot()!=slot&&input.ingredient().test(stack)){
                repeated=true;
                if((long)items.get(input.slot()).getCount()*target.count()<(long)items.get(slot).getCount()*input.count()){compatible=false;break;}
                int ceiling=(items.get(input.slot()).getCount()/input.count()+1)*target.count();
                allowed=Math.min(allowed,Math.max(0,ceiling-items.get(slot).getCount()));
            }
            if(repeated)allowed=Math.min(allowed,target.count());
            if(compatible)best=Math.max(best,allowed);
        }
        return best;
    }
    @Override public boolean canTakeItemThroughFace(int slot,ItemStack stack,Direction side){return !isRemoved()&&(side==Direction.DOWN||kind.heatedExternally()&&side.getAxis().isHorizontal())&&(kind.output(slot)||stack.is(Items.BUCKET)||stack.is(Items.GLASS_BOTTLE));}
    @Override public Component getDisplayName(){return Component.translatable("container.foodcraft."+kind.id);}
    @Override public AbstractContainerMenu createMenu(int syncId,Inventory inventory,Player player){return new MachineMenu(kind,syncId,inventory,this,data);}
}
