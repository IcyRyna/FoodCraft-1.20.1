package org.foodcraft.agriculture;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.LevelReader;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
public final class FoodOnionBlock extends SugarCaneBlock {
    private final net.minecraft.world.phys.shapes.VoxelShape legacyShape;
    public FoodOnionBlock(org.foodcraft.Catalog.Entry entry,Properties properties){super(properties);var b=entry.collision();legacyShape=b==null?null:net.minecraft.world.level.block.Block.box(b[0],b[1],b[2],b[3],b[4],b[5]);}
    @Override public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,net.minecraft.world.level.BlockGetter level,BlockPos pos,net.minecraft.world.phys.shapes.CollisionContext context){return legacyShape==null?super.getShape(state,level,pos,context):legacyShape;}
    @Override public boolean canSurvive(BlockState state,LevelReader level,BlockPos pos){
        var ground=level.getBlockState(pos.below());return ground.is(this)||ground.is(Blocks.FARMLAND)||ground.is(BlockTags.DIRT);
    }
}
