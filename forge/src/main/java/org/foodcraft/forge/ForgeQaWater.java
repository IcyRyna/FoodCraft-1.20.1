package org.foodcraft.forge;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.Direction;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.foodcraft.test.QaWaterSources;

/** Test source exposes the real Forge fluid interface and exact simulation semantics. */
final class ForgeQaWater implements ICapabilityProvider,IFluidHandler {
    private final BlockEntity chest;
    private final LazyOptional<IFluidHandler> storage=LazyOptional.of(()->this);
    ForgeQaWater(BlockEntity chest){this.chest=chest;}
    @Override public <T> LazyOptional<T> getCapability(Capability<T> capability,Direction side){return !chest.isRemoved()&&capability==ForgeCapabilities.FLUID_HANDLER?storage.cast():LazyOptional.empty();}
    void invalidate(){storage.invalidate();}
    private QaWaterSources data(){return QaWaterSources.get((ServerLevel)chest.getLevel());}
    public int getTanks(){return 1;}
    public int getTankCapacity(int tank){return 1_000_000;}
    public FluidStack getFluidInTank(int tank){return new FluidStack(Fluids.WATER,(int)(data().amount(chest.getBlockPos())/81));}
    public boolean isFluidValid(int tank,FluidStack fluid){return false;}
    public int fill(FluidStack fluid,FluidAction action){return 0;}
    public FluidStack drain(int maximum,FluidAction action){if(chest.isRemoved())return FluidStack.EMPTY;long amount=data().extract(chest.getBlockPos(),Math.max(0,maximum)*81L,action.simulate());return new FluidStack(Fluids.WATER,(int)(amount/81));}
    public FluidStack drain(FluidStack fluid,FluidAction action){return fluid.getFluid()==Fluids.WATER&&!fluid.hasTag()?drain(fluid.getAmount(),action):FluidStack.EMPTY;}
}
