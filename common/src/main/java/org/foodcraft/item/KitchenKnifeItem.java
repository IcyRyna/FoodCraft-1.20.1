package org.foodcraft.item;
import net.minecraft.world.item.Item;
public final class KitchenKnifeItem extends Item {
    public final boolean extraYield;
    public KitchenKnifeItem(boolean extraYield,Properties properties){super(properties);this.extraYield=extraYield;}
}
