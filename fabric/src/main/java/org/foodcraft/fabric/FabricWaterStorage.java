package org.foodcraft.fabric;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.core.Direction;
import net.minecraft.world.level.material.Fluids;
import org.foodcraft.machine.MachineBlockEntity;
import org.foodcraft.machine.MachineKind;
import java.util.Iterator;
import java.util.List;

/** Preserve whole legacy water units while supporting rollback of Fabric transactions. */
final class FabricWaterStorage extends SnapshotParticipant<Long> implements Storage<FluidVariant>,StorageView<FluidVariant> {
    private final MachineBlockEntity machine;private final Direction side;
    FabricWaterStorage(MachineBlockEntity machine,Direction side){this.machine=machine;this.side=side;}
    @Override public long insert(FluidVariant resource,long maxAmount,TransactionContext transaction){
        if(maxAmount<0)throw new IllegalArgumentException("Negative insertion amount");
        if(machine.isRemoved()||side==Direction.DOWN||!resource.equals(FluidVariant.of(Fluids.WATER)))return 0;
        long accepted=machine.fillWaterVolume(maxAmount,true);if(accepted==0)return 0;
        updateSnapshots(transaction);machine.fillWaterVolume(accepted,false);return accepted;
    }
    @Override public long extract(FluidVariant resource,long maxAmount,TransactionContext transaction){if(maxAmount<0)throw new IllegalArgumentException("Negative extraction amount");return 0;}
    @Override protected Long createSnapshot(){return machine.liquidVolume();}
    @Override protected void readSnapshot(Long snapshot){machine.liquid=(int)(snapshot/MachineBlockEntity.LIQUID_UNIT);machine.liquidFraction=(int)(snapshot%MachineBlockEntity.LIQUID_UNIT);machine.setChanged();}
    @Override protected void onFinalCommit(){machine.setChanged();}
    @Override public boolean isResourceBlank(){return machine.milk||machine.liquidVolume()==0||getCapacity()==0;}
    @Override public FluidVariant getResource(){return isResourceBlank()?FluidVariant.blank():FluidVariant.of(Fluids.WATER);}
    @Override public long getAmount(){return isResourceBlank()?0:machine.liquidVolume();}
    @Override public long getCapacity(){return machine.kind.liquidSlot<0||machine.kind==MachineKind.DEEP_FRYER?0:8*FluidConstants.BUCKET;}
    @Override public Iterator<StorageView<FluidVariant>> iterator(){return List.<StorageView<FluidVariant>>of(this).iterator();}
}
