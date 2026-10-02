package org.foodcraft.agriculture;

import net.minecraft.world.level.block.Block;
import org.foodcraft.Catalog;

public final class FruitBlock extends Block {
    public final Catalog.Entry entry;
    private final net.minecraft.world.phys.shapes.VoxelShape legacyShape;
    public FruitBlock(Catalog.Entry entry,Properties properties){super(properties);this.entry=entry;var b=entry.collision();legacyShape=b==null?null:Block.box(b[0],b[1],b[2],b[3],b[4],b[5]);}
    @Override public net.minecraft.world.phys.shapes.VoxelShape getShape(net.minecraft.world.level.block.state.BlockState state,net.minecraft.world.level.BlockGetter level,net.minecraft.core.BlockPos pos,net.minecraft.world.phys.shapes.CollisionContext context){return legacyShape==null?super.getShape(state,level,pos,context):legacyShape;}
}
