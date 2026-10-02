package org.foodcraft.forge;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.foodcraft.machine.MachineBlockEntity;
import org.foodcraft.machine.MachineKind;
import java.util.EnumMap;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

final class ForgeMachineCapabilities implements ICapabilityProvider {
    private final MachineBlockEntity machine;
    private final Map<Direction,LazyOptional<IItemHandler>> items=new EnumMap<>(Direction.class);
    private final LazyOptional<IFluidHandler> fluids;
    ForgeMachineCapabilities(MachineBlockEntity machine){
        this.machine=machine;
        for(Direction side:Direction.values())items.put(side,LazyOptional.of(()->new Items(side)));
        fluids=LazyOptional.of(()->new Tank());
    }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> capability,@Nullable Direction side){
        if(machine.isRemoved())return LazyOptional.empty();
        if(capability==ForgeCapabilities.ITEM_HANDLER)return items.get(side==null?Direction.UP:side).cast();
        if(capability==ForgeCapabilities.FLUID_HANDLER&&side!=Direction.DOWN&&machine.kind.liquidSlot>=0&&machine.kind!=MachineKind.DEEP_FRYER)return fluids.cast();
        return LazyOptional.empty();
    }
    void invalidate(){items.values().forEach(LazyOptional::invalidate);fluids.invalidate();}
    private final class Items implements IItemHandler {
        private final Direction side;private final int[] slots;
        Items(Direction side){this.side=side;slots=machine.getSlotsForFace(side);}
        public int getSlots(){return slots.length;}
        private int index(int slot){if(slot<0||slot>=slots.length)throw new IndexOutOfBoundsException(slot);return slots[slot];}
        public ItemStack getStackInSlot(int slot){return machine.getItem(index(slot)).copy();}
        public int getSlotLimit(int slot){index(slot);return 64;}
        public boolean isItemValid(int slot,ItemStack stack){return machine.canPlaceItemThroughFace(index(slot),stack,side);}
        public ItemStack insertItem(int slot,ItemStack stack,boolean simulate){
            int target=index(slot);if(stack.isEmpty()||machine.isRemoved()||!isItemValid(slot,stack))return stack;
            ItemStack existing=machine.getItem(target);
            if(!existing.isEmpty()&&!ItemStack.isSameItemSameTags(existing,stack))return stack;
            int amount=Math.min(stack.getCount(),Math.min(stack.getMaxStackSize(),64)-existing.getCount());
            if(side==Direction.UP)amount=Math.min(amount,machine.automaticInsertionLimit(target,stack));
            if(amount<=0)return stack;
            if(!simulate){ItemStack changed=existing.isEmpty()?stack.copyWithCount(amount):existing.copyWithCount(existing.getCount()+amount);machine.setItem(target,changed);}
            return amount==stack.getCount()?ItemStack.EMPTY:stack.copyWithCount(stack.getCount()-amount);
        }
        public ItemStack extractItem(int slot,int amount,boolean simulate){
            int target=index(slot);ItemStack existing=machine.getItem(target);
            if(amount<=0||machine.isRemoved()||existing.isEmpty()||!machine.canTakeItemThroughFace(target,existing,side))return ItemStack.EMPTY;
            int extracted=Math.min(amount,existing.getCount());
            ItemStack result=existing.copyWithCount(extracted);
            if(!simulate)machine.removeItem(target,extracted);
            return result;
        }
    }
    private final class Tank implements IFluidHandler {
        public int getTanks(){return 1;}
        public FluidStack getFluidInTank(int tank){if(tank!=0)throw new IndexOutOfBoundsException(tank);return machine.milk?FluidStack.EMPTY:new FluidStack(Fluids.WATER,(int)(machine.liquidVolume()/81));}
        public int getTankCapacity(int tank){if(tank!=0)throw new IndexOutOfBoundsException(tank);return 8000;}
        public boolean isFluidValid(int tank,FluidStack stack){return tank==0&&stack.getFluid()==Fluids.WATER&&!stack.hasTag();}
        public int fill(FluidStack stack,FluidAction action){
            if(machine.isRemoved()||!isFluidValid(0,stack))return 0;
            long wholeMilliBuckets=Math.min(stack.getAmount(),(8L*org.foodcraft.machine.MachineBlockEntity.LIQUID_UNIT-machine.liquidVolume())/81);
            return (int)(machine.fillWaterVolume(wholeMilliBuckets*81,action.simulate())/81);
        }
        public FluidStack drain(FluidStack resource,FluidAction action){return FluidStack.EMPTY;}
        public FluidStack drain(int amount,FluidAction action){return FluidStack.EMPTY;}
    }
}
