package org.foodcraft.agriculture;

import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import org.foodcraft.Catalog;

public final class FoodCakeBlock extends CakeBlock {
    private final boolean golden;
    public FoodCakeBlock(Catalog.Entry entry,Properties properties){super(entry.path().contains("jinputao")?properties.randomTicks():properties);golden=entry.path().contains("jinputao");}
    @Override public void randomTick(BlockState state,net.minecraft.server.level.ServerLevel level,BlockPos pos,net.minecraft.util.RandomSource random){
        if(golden&&state.getValue(BITES)>0&&level.getBlockState(pos).is(this))level.setBlock(pos,state.setValue(BITES,state.getValue(BITES)-1),3);
    }
}
