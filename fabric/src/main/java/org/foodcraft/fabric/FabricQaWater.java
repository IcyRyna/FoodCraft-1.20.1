package org.foodcraft.fabric;

import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluids;
import org.foodcraft.test.QaWaterSources;

/** The pipe performs native transactions; only a committed source change is saved. */
final class FabricQaWater extends SingleVariantStorage<FluidVariant> {
    private final ChestBlockEntity chest;
    FabricQaWater(ChestBlockEntity chest){this.chest=chest;amount=QaWaterSources.get((ServerLevel)chest.getLevel()).amount(chest.getBlockPos());variant=amount>0?FluidVariant.of(Fluids.WATER):FluidVariant.blank();}
    protected FluidVariant getBlankVariant(){return FluidVariant.blank();}
    protected long getCapacity(FluidVariant variant){return 1000L*org.foodcraft.machine.MachineBlockEntity.LIQUID_UNIT;}
    protected boolean canInsert(FluidVariant variant){return false;}
    protected void onFinalCommit(){QaWaterSources.get((ServerLevel)chest.getLevel()).set(chest.getBlockPos(),amount);}
}
