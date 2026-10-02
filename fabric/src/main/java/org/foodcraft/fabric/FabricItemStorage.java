package org.foodcraft.fabric;

import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.core.Direction;
import org.foodcraft.machine.MachineBlockEntity;
import java.util.Iterator;

/** A bulk transfer must obey the same ordered and repeated-input rules as native hoppers. */
final class FabricItemStorage implements Storage<ItemVariant> {
    private final MachineBlockEntity machine;
    private final Direction side;
    private final InventoryStorage inventory;
    FabricItemStorage(MachineBlockEntity machine,Direction side){
        this.machine=machine;this.side=side==null?Direction.UP:side;inventory=InventoryStorage.of(machine,this.side);
    }
    @Override public long insert(ItemVariant resource,long maximum,TransactionContext transaction){
        if(resource.isBlank()||maximum<0)throw new IllegalArgumentException("Invalid item transfer");
        if(machine.isRemoved())return 0;
        if(side!=Direction.UP||machine.kind.inputs.length<2)return inventory.insert(resource,maximum,transaction);
        long inserted=0;
        // SidedInventory checks each insertion against the transaction's current snapshot.
        while(inserted<maximum&&inventory.insert(resource,1,transaction)==1)inserted++;
        return inserted;
    }
    @Override public long extract(ItemVariant resource,long maximum,TransactionContext transaction){
        if(resource.isBlank()||maximum<0)throw new IllegalArgumentException("Invalid item transfer");
        return machine.isRemoved()?0:inventory.extract(resource,maximum,transaction);
    }
    @Override public Iterator<StorageView<ItemVariant>> iterator(){return inventory.iterator();}
}
