package org.foodcraft.item;

import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.BlockTags;
import org.foodcraft.Catalog;

public final class MultiToolItem extends DiggerItem {
    private final boolean healing;
    private static final net.minecraft.world.item.Tier TIER=new net.minecraft.world.item.Tier(){
        public int getUses(){return 5000;}public float getSpeed(){return 8;}public float getAttackDamageBonus(){return 3;}
        public int getLevel(){return 3;}public int getEnchantmentValue(){return 15;}
        public net.minecraft.world.item.crafting.Ingredient getRepairIngredient(){return net.minecraft.world.item.crafting.Ingredient.EMPTY;}
    };
    public MultiToolItem(Catalog.Entry e,Properties properties){super(9.0F,-2.4F,TIER,BlockTags.MINEABLE_WITH_PICKAXE,properties.durability(5000));healing=e.path().contains("anbi");}
    @Override public float getDestroySpeed(ItemStack stack,BlockState state){
        return state.is(BlockTags.MINEABLE_WITH_PICKAXE)||state.is(BlockTags.MINEABLE_WITH_AXE)||state.is(BlockTags.MINEABLE_WITH_SHOVEL)?8.0F:1.0F;
    }
    @Override public boolean isCorrectToolForDrops(BlockState state){
        return state.is(BlockTags.MINEABLE_WITH_PICKAXE)||state.is(BlockTags.MINEABLE_WITH_AXE)||state.is(BlockTags.MINEABLE_WITH_SHOVEL);
    }
    @Override public void appendHoverText(ItemStack stack,net.minecraft.world.level.Level level,java.util.List<net.minecraft.network.chat.Component> lines,net.minecraft.world.item.TooltipFlag flag){
        super.appendHoverText(stack,level,lines,flag);
        long remaining=Math.max(1,(2000L-stack.getDamageValue()+4)/5);
        lines.add(net.minecraft.network.chat.Component.translatable("item.foodcraft.multitool.special_remaining",remaining).withStyle(net.minecraft.ChatFormatting.GRAY));
        lines.add(net.minecraft.network.chat.Component.translatable(healing?"item.foodcraft.multitool.heal":"item.foodcraft.multitool.torch").withStyle(net.minecraft.ChatFormatting.GRAY));
    }
    @Override public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context){
        var level=context.getLevel();var player=context.getPlayer();if(player==null)return net.minecraft.world.InteractionResult.PASS;
        var target=context.getClickedPos().relative(context.getClickedFace());
        if(!level.mayInteract(player,context.getClickedPos())||!player.mayUseItemAt(target,context.getClickedFace(),context.getItemInHand()))return net.minecraft.world.InteractionResult.FAIL;
        if(!healing&&(level.isOutsideBuildHeight(target)||!level.hasChunkAt(target)||!level.getBlockState(target).canBeReplaced()||!net.minecraft.world.level.block.Blocks.TORCH.defaultBlockState().canSurvive(level,target)))return net.minecraft.world.InteractionResult.FAIL;
        if(!level.isClientSide){
            if(healing)player.setHealth(Math.max(player.getHealth(),Math.min(20,player.getMaxHealth())));
            else if(!level.setBlock(target,net.minecraft.world.level.block.Blocks.TORCH.defaultBlockState(),3))return net.minecraft.world.InteractionResult.FAIL;
            level.playSound(null,context.getClickedPos(),healing?net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP:net.minecraft.sounds.SoundEvents.STONE_PLACE,net.minecraft.sounds.SoundSource.PLAYERS,1,1);
            if(!player.getAbilities().instabuild){
                // Legacy special use adds exactly five wear and breaks at 2000;
                // normal mining/combat still uses the material's 5000 durability.
                var stack=context.getItemInHand();long damage=(long)stack.getDamageValue()+5;
                if(damage>=2000){stack.shrink(1);player.broadcastBreakEvent(context.getHand());}
                else stack.setDamageValue((int)damage);
            }
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
    }
}
