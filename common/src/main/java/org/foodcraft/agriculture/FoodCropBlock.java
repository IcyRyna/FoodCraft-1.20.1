package org.foodcraft.agriculture;

import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.ItemLike;
import net.minecraft.resources.ResourceLocation;
import org.foodcraft.Catalog;
import org.foodcraft.FoodCraft;

public final class FoodCropBlock extends CropBlock {
    private final String cropId;
    private final net.minecraft.world.phys.shapes.VoxelShape legacyShape;
    public FoodCropBlock(Catalog.Entry entry,Properties properties){super(properties);cropId=entry.id();var b=entry.collision();legacyShape=b==null?null:net.minecraft.world.level.block.Block.box(b[0],b[1],b[2],b[3],b[4],b[5]);}
    @Override public net.minecraft.world.phys.shapes.VoxelShape getShape(net.minecraft.world.level.block.state.BlockState state,net.minecraft.world.level.BlockGetter level,net.minecraft.core.BlockPos pos,net.minecraft.world.phys.shapes.CollisionContext context){return legacyShape==null?super.getShape(state,level,pos,context):legacyShape;}
    @Override protected ItemLike getBaseSeedId(){
        Catalog.Entry seed=Catalog.ENTRIES.stream().filter(e->cropId.equals(e.crop())&&e.kind().equals("seed")).findFirst().orElseThrow();
        return FoodCraft.item(new ResourceLocation(seed.id()).getPath());
    }
}
