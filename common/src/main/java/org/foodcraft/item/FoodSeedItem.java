package org.foodcraft.item;

import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.foodcraft.Catalog;
import org.foodcraft.FoodCraft;

public final class FoodSeedItem extends FoodItem {
    public FoodSeedItem(Catalog.Entry entry,Properties properties){super(entry,properties);}
    @Override public InteractionResult useOn(UseOnContext context){
        var player=context.getPlayer();var level=context.getLevel();
        var pos=context.getClickedPos().above();
        if(context.getClickedFace()!=Direction.UP||player==null||!player.mayUseItemAt(pos,Direction.UP,context.getItemInHand()))return InteractionResult.PASS;
        if(level.isOutsideBuildHeight(pos)||!level.getWorldBorder().isWithinBounds(pos)||!level.mayInteract(player,pos))return InteractionResult.FAIL;
        Block crop=FoodCraft.BLOCKS.get(new ResourceLocation(entry.crop()).getPath());
        if(crop==null||!level.getBlockState(pos).canBeReplaced()||!crop.defaultBlockState().canSurvive(level,pos))return InteractionResult.FAIL;
        if(!level.isClientSide){
            if(!level.setBlock(pos,crop.defaultBlockState(),3))return InteractionResult.FAIL;
            if(!player.getAbilities().instabuild)context.getItemInHand().shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
