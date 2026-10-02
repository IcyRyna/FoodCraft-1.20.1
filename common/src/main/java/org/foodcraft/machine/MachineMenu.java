package org.foodcraft.machine;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.foodcraft.FoodCraft;

public final class MachineMenu extends AbstractContainerMenu {
    public final MachineKind kind;
    public final Container inventory;
    public final ContainerData data;
    public static MachineMenu client(MachineKind kind,int id,Inventory player){return new MachineMenu(kind,id,player,new SimpleContainer(kind.size),new SimpleContainerData(MachineBlockEntity.DATA_FIELDS));}
    public double liquidUnits(){return data.get(4)+((data.get(16)&65535)|((data.get(17)&65535)<<16))/(double)MachineBlockEntity.LIQUID_UNIT;}
    public String liquidLabel(){return liquidUnits()==Math.rint(liquidUnits())?Integer.toString((int)liquidUnits()):String.format(java.util.Locale.ROOT,"%.6f",liquidUnits()).replaceFirst("0+$","");}
    public int progress(){return (data.get(0)&65535)|((data.get(14)&65535)<<16);}
    public int totalTime(){return (data.get(1)&65535)|((data.get(15)&65535)<<16);}
    public MachineMenu(MachineKind kind,int id,Inventory player,Container inventory,ContainerData data){
        super(FoodCraft.MENUS.get(kind),id);this.kind=kind;this.inventory=inventory;this.data=data;
        checkContainerSize(inventory,kind.size);checkContainerDataCount(data,MachineBlockEntity.DATA_FIELDS);
        inventory.startOpen(player.player);
        for(int[] coordinate:kind.layout()){
            int slot=coordinate[0];
            addSlot(new Slot(inventory,slot,coordinate[1],coordinate[2]){
                @Override public boolean mayPlace(ItemStack stack){return MachineBlockEntity.accepts(kind,slot,stack)&&inventory.canPlaceItem(slot,stack);}
                @Override public int getMaxStackSize(ItemStack stack){return Math.min(super.getMaxStackSize(stack),stack.getMaxStackSize());}
            });
        }
        if(slots.size()!=kind.size)throw new IllegalStateException("Incorrect menu layout for "+kind.id);
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(player,col+row*9+9,8+col*18,84+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(player,col,8+col*18,142));
        addDataSlots(data);
    }
    @Override public boolean stillValid(Player player){return inventory.stillValid(player);}
    @Override public ItemStack quickMoveStack(Player player,int index){
        if(index<0||index>=slots.size())return ItemStack.EMPTY;
        Slot slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;
        ItemStack stack=slot.getItem();ItemStack before=stack.copy();
        if(index<kind.size){if(!moveItemStackTo(stack,kind.size,slots.size(),true))return ItemStack.EMPTY;}
        else{
            boolean moved=false;
            // Prefer dedicated tool/fuel/liquid slots before broad ingredient slots.
            for(int i=0;i<kind.size;i++)if(!kind.input(i)&&slots.get(i).mayPlace(stack)&&moveItemStackTo(stack,i,i+1,false)){moved=true;if(stack.isEmpty())break;}
            if(!stack.isEmpty())for(int i=0;i<kind.size;i++)if(kind.input(i)&&slots.get(i).mayPlace(stack)&&moveItemStackTo(stack,i,i+1,false)){moved=true;if(stack.isEmpty())break;}
            if(!moved){int hotbar=kind.size+27;
                if(index<hotbar){if(!moveItemStackTo(stack,hotbar,slots.size(),false))return ItemStack.EMPTY;}
                else if(!moveItemStackTo(stack,kind.size,hotbar,false))return ItemStack.EMPTY;
            }
        }
        if(stack.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();
        if(stack.getCount()==before.getCount())return ItemStack.EMPTY;
        slot.onTake(player,stack);return before;
    }
    @Override public boolean clickMenuButton(Player player,int button){
        if(!stillValid(player)||!(inventory instanceof MachineBlockEntity machine)||!kind.heatedExternally())return false;
        if(button==0)machine.adjustFirePower(machine.firePower-1);
        else if(button==1)machine.adjustFirePower(machine.firePower+1);
        else if(button>=2&&button<=102)machine.adjustFirePower(button-2);
        else return false;
        broadcastChanges();return true;
    }
    @Override public void removed(Player player){super.removed(player);inventory.stopOpen(player);}
}
