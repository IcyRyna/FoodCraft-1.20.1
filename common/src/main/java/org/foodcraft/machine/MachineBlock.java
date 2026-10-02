package org.foodcraft.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.BlockGetter;
import org.foodcraft.FoodCraft;

public final class MachineBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING=HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty LIT=BooleanProperty.create("lit");
    public final MachineKind kind;
    private final VoxelShape collision;
    public MachineBlock(MachineKind kind,Properties properties,float[] bounds,int light){
        super(properties.noOcclusion().lightLevel(state->state.getValue(LIT)?light:0));this.kind=kind;
        collision=bounds==null?Block.box(0,0,0,16,16,16):Block.box(bounds[0],bounds[1],bounds[2],bounds[3],bounds[4],bounds[5]);
        registerDefaultState(stateDefinition.any().setValue(FACING,net.minecraft.core.Direction.NORTH).setValue(LIT,false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,LIT);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());}
    @Override public RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override public java.util.List<ItemStack> getDrops(BlockState state,net.minecraft.world.level.storage.loot.LootParams.Builder params){
        if(FoodCraft.platform.wrenchEnabled())return java.util.List.of(new ItemStack(FoodCraft.item("waike")));
        ItemStack tool=params.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.TOOL);
        if(tool==null||!tool.is(net.minecraft.tags.ItemTags.PICKAXES)||!(tool.getItem() instanceof net.minecraft.world.item.TieredItem tiered)||tiered.getTier().getLevel()<2)return java.util.List.of();
        ItemStack result=new ItemStack(this);
        // Ordinary harvesting drops the inventory separately; only skill travels with the block.
        if(kind.heatedExternally()&&params.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY) instanceof MachineBlockEntity machine){
            var tag=new net.minecraft.nbt.CompoundTag();tag.putInt("Proficiency",machine.proficiency);result.addTagElement("BlockEntityTag",tag);
        }
        return java.util.List.of(result);
    }
    @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){
        return collision;
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new MachineBlockEntity(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){
        return level.isClientSide?null:createTickerHelper(type,FoodCraft.MACHINE_ENTITY,MachineBlockEntity::tick);
    }
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(player.getItemInHand(hand).getItem() instanceof org.foodcraft.item.WrenchItem&&FoodCraft.platform.wrenchEnabled())return InteractionResult.PASS;
        if(!(level.getBlockEntity(pos) instanceof MachineBlockEntity machine))return InteractionResult.PASS;
        if(!level.isClientSide)player.openMenu(machine);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){
        if(!state.is(next.getBlock())){
            if(!level.isClientSide&&level.getBlockEntity(pos) instanceof MachineBlockEntity machine){
                Containers.dropContents(level,pos,machine);machine.clearContent();level.updateNeighbourForOutputSignal(pos,this);
            }
            super.onRemove(state,level,pos,next,moving);
        }
    }
    @Override public void entityInside(BlockState state,Level level,BlockPos pos,Entity entity){
        if(!level.isClientSide&&kind.heatedExternally()&&state.getValue(LIT)&&entity instanceof LivingEntity&&
                (!(entity instanceof Player player)||!player.getAbilities().instabuild))entity.setSecondsOnFire(3);
        super.entityInside(state,level,pos,entity);
    }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){
        super.setPlacedBy(level,pos,state,placer,stack);
        if(!level.isClientSide&&stack.hasTag()&&stack.getTag().contains("BlockEntityTag")&&level.getBlockEntity(pos) instanceof MachineBlockEntity machine){machine.load(stack.getTag().getCompound("BlockEntityTag"));machine.setChanged();}
    }
}
