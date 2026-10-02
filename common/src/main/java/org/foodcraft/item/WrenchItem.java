package org.foodcraft.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;
import org.foodcraft.FoodCraft;
import org.foodcraft.machine.MachineBlockEntity;

public final class WrenchItem extends Item {
    public WrenchItem(Properties properties){super(properties);}
    @Override public InteractionResult useOn(UseOnContext context){
        var level=context.getLevel();var pos=context.getClickedPos();var player=context.getPlayer();
        if(player==null||!FoodCraft.platform.wrenchEnabled()||!level.mayInteract(player,pos))return InteractionResult.PASS;
        if(!(level.getBlockEntity(pos) instanceof MachineBlockEntity machine))return InteractionResult.PASS;
        if(!level.isClientSide){
            if(!player.getAbilities().instabuild){
                ItemStack result=new ItemStack(level.getBlockState(pos).getBlock());
                machine.saveToItem(result);
                machine.clearContent();
                Block.popResource(level,pos,result);
            }
            level.removeBlock(pos,false);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
