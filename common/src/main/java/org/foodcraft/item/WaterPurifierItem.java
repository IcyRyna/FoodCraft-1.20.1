package org.foodcraft.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.HitResult;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.foodcraft.FoodCraft;

public final class WaterPurifierItem extends Item {
    public WaterPurifierItem(Properties properties){super(properties);}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        ItemStack stack=player.getItemInHand(hand);
        var hit=getPlayerPOVHitResult(level,player,ClipContext.Fluid.SOURCE_ONLY);
        if(hit.getType()!=HitResult.Type.BLOCK)return InteractionResultHolder.pass(stack);
        var pos=hit.getBlockPos();
        if(!level.mayInteract(player,pos)||!player.mayUseItemAt(pos,hit.getDirection(),stack)||
                !level.getFluidState(pos).is(Fluids.WATER)||!level.getFluidState(pos).isSource())return InteractionResultHolder.fail(stack);
        if(level.isClientSide)return InteractionResultHolder.success(stack);
        if(!(level.getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.BucketPickup pickup))return InteractionResultHolder.fail(stack);
        ItemStack removed=pickup.pickupBlock(level,pos,level.getBlockState(pos));
        if(removed.isEmpty())return InteractionResultHolder.fail(stack);
        ItemStack water=new ItemStack(FoodCraft.item("water"));
        if(!player.getInventory().add(water))player.drop(water,false);
        level.playSound(null,pos,SoundEvents.BUCKET_FILL,SoundSource.PLAYERS,1,1);
        if(!player.getAbilities().instabuild){
            int damage=stack.getDamageValue()+1;
            if(damage>=stack.getMaxDamage())return InteractionResultHolder.success(new ItemStack(Items.BUCKET));
            stack.setDamageValue(damage);
        }
        return InteractionResultHolder.success(stack);
    }
}
