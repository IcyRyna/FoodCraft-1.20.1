package org.foodcraft.agriculture;

import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.grower.AbstractTreeGrower;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import org.foodcraft.Catalog;
import org.foodcraft.FoodCraft;
import java.util.Map;
import java.util.LinkedHashMap;

public final class FoodSaplingBlock extends SaplingBlock {
    private final Catalog.Entry entry;
    private final net.minecraft.world.phys.shapes.VoxelShape legacyShape;
    public FoodSaplingBlock(Catalog.Entry entry,Properties properties){
        super(new AbstractTreeGrower(){@Override protected net.minecraft.resources.ResourceKey<ConfiguredFeature<?,?>> getConfiguredFeature(RandomSource random,boolean bees){return null;}},properties);
        this.entry=entry;
        var b=entry.collision();legacyShape=b==null?null:net.minecraft.world.level.block.Block.box(b[0],b[1],b[2],b[3],b[4],b[5]);
    }
    @Override public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,net.minecraft.world.level.BlockGetter level,BlockPos pos,net.minecraft.world.phys.shapes.CollisionContext context){return legacyShape==null?super.getShape(state,level,pos,context):legacyShape;}
    @Override public void advanceTree(ServerLevel level,BlockPos pos,BlockState state,RandomSource random){
        String fruit=entry.constructor_args().length>0?entry.constructor_args()[0].replace("Plant.",""):"";
        Catalog.Entry fruitEntry=Catalog.ENTRIES.stream().filter(e->e.legacy().equals(fruit)).findFirst().orElse(null);
        if(fruitEntry==null)return;
        var fruitBlock=FoodCraft.BLOCKS.get(fruitEntry.path());
        Map<BlockPos,BlockState> changes=new LinkedHashMap<>();
        for(var voxel:Catalog.TREES.get(entry.legacy_class())){
            var block=voxel.block().equals("fruit")?fruitBlock:net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(new ResourceLocation(voxel.block()));
            int[] offset=voxel.offset();changes.put(pos.offset(offset[0],offset[1],offset[2]),block.defaultBlockState());
        }
        for(var target:changes.keySet()){
            if(level.isOutsideBuildHeight(target)||!level.hasChunkAt(target))return;
            var existing=level.getBlockState(target);
            if(!target.equals(pos)&&!existing.isAir()&&!(existing.getBlock() instanceof LeavesBlock)&&!(existing.getBlock() instanceof FruitBlock)&&!existing.canBeReplaced())return;
        }
        changes.forEach((target,value)->level.setBlock(target,value,3));
    }
}
