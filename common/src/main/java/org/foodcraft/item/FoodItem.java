package org.foodcraft.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import org.foodcraft.Catalog;

public class FoodItem extends Item {
    protected final Catalog.Entry entry;
    public FoodItem(Catalog.Entry entry,Properties properties){super(properties);this.entry=entry;}
    @Override public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context){
        if(!entry.path().equals("jinkela"))return super.useOn(context);
        var state=context.getLevel().getBlockState(context.getClickedPos());
        if(!(state.getBlock() instanceof org.foodcraft.agriculture.FoodSaplingBlock sapling))return net.minecraft.world.InteractionResult.PASS;
        if(!context.getLevel().isClientSide){
            var level=(net.minecraft.server.level.ServerLevel)context.getLevel();
            sapling.advanceTree(level,context.getClickedPos(),state,level.random);
            if(context.getPlayer()==null||!context.getPlayer().getAbilities().instabuild)context.getItemInHand().shrink(1);
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }
    @Override public UseAnim getUseAnimation(ItemStack stack){
        String type=entry.legacy_class();
        return type.equals("ItemYingliao")||type.equals("ItemMilk")||type.startsWith("ItemFood")&&type.toLowerCase(java.util.Locale.ROOT).endsWith("jiu")?UseAnim.DRINK:super.getUseAnimation(stack);
    }
    @Override public boolean isFoil(ItemStack stack){return entry.glint()||entry.effectName().startsWith("gold_")||entry.legacy_class().equals("ItemBook")||entry.legacy_class().equals("ItemShi")||super.isFoil(stack);}
    @Override public ItemStack finishUsingItem(ItemStack stack,Level level,LivingEntity entity){
        if(!level.isClientSide){
            switch(entry.effectName()){
                case "milk" -> org.foodcraft.FoodCraft.platform.cureMilk(entity);
                case "chili" -> entity.setSecondsOnFire(3);
                case "staple" -> {
                    MobEffect[] effects={MobEffects.DIG_SPEED,MobEffects.FIRE_RESISTANCE,MobEffects.INVISIBILITY,MobEffects.JUMP,
                            MobEffects.MOVEMENT_SPEED,MobEffects.NIGHT_VISION,MobEffects.WATER_BREATHING};
                    entity.addEffect(new MobEffectInstance(effects[level.random.nextInt(effects.length)],600,1));
                }
                case "gold_cookie" -> {effect(entity,MobEffects.JUMP,1200,1);effect(entity,MobEffects.MOVEMENT_SPEED,1200,1);effect(entity,MobEffects.DIG_SPEED,1200,1);}
                case "gold_grape" -> {effect(entity,MobEffects.HEAL,36000,4);effect(entity,MobEffects.FIRE_RESISTANCE,36000,4);effect(entity,MobEffects.DAMAGE_BOOST,36000,4);}
                case "gold_apple" -> {effect(entity,MobEffects.DAMAGE_RESISTANCE,36000,4);effect(entity,MobEffects.REGENERATION,36000,4);effect(entity,MobEffects.ABSORPTION,36000,4);}
                case "gold_grape_wine" -> {for(MobEffect effect:new MobEffect[]{MobEffects.JUMP,MobEffects.MOVEMENT_SPEED,MobEffects.DIG_SPEED,MobEffects.HEAL,MobEffects.FIRE_RESISTANCE,MobEffects.DAMAGE_BOOST})effect(entity,effect,3600,4);}
                case "gold_apple_wine" -> {for(MobEffect effect:new MobEffect[]{MobEffects.NIGHT_VISION,MobEffects.INVISIBILITY,MobEffects.WATER_BREATHING,MobEffects.DAMAGE_RESISTANCE,MobEffects.REGENERATION,MobEffects.ABSORPTION})effect(entity,effect,3600,4);}
                case "wine" -> {
                    MobEffect[] effects=level.random.nextBoolean()?new MobEffect[]{MobEffects.JUMP,MobEffects.MOVEMENT_SPEED,MobEffects.DIG_SPEED,MobEffects.HEAL}:
                            new MobEffect[]{MobEffects.HUNGER,MobEffects.DIG_SLOWDOWN,MobEffects.MOVEMENT_SLOWDOWN,MobEffects.CONFUSION};
                    for(MobEffect effect:effects)effect(entity,effect,600,3);
                }
                default -> {}
            }
        }
        return super.finishUsingItem(stack,level,entity);
    }
    private static void effect(LivingEntity target,MobEffect effect,int duration,int amplifier){target.addEffect(new MobEffectInstance(effect,duration,amplifier));}
}
