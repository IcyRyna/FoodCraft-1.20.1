package org.foodcraft.item;

import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.InteractionResult;
import net.minecraft.resources.ResourceLocation;
import org.foodcraft.Catalog;
import org.foodcraft.FoodCraft;

public final class FoodCakeItem extends FoodItem {
    public FoodCakeItem(Catalog.Entry entry,Properties props){super(entry,props);}
    @Override public InteractionResult useOn(UseOnContext context){
        return ((BlockItem)FoodCraft.item(entry.crop())).useOn(context);
    }
}
