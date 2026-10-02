package org.foodcraft.test;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.HashMap;
import java.util.Map;

/** Opt-in standard fluid source for transport integration tests, stored with the test world. */
public final class QaWaterSources extends SavedData {
    private final Map<Long,Long> amounts=new HashMap<>();
    public static QaWaterSources get(ServerLevel level){return level.getDataStorage().computeIfAbsent(QaWaterSources::load,QaWaterSources::new,"foodcraft_qa_water");}
    private static QaWaterSources load(CompoundTag tag){var result=new QaWaterSources();for(String key:tag.getAllKeys())result.amounts.put(Long.parseLong(key),tag.getLong(key));return result;}
    public long amount(BlockPos pos){return amounts.getOrDefault(pos.asLong(),0L);}
    public void set(BlockPos pos,long amount){if(amount<0)throw new IllegalArgumentException("Negative source water");amounts.put(pos.asLong(),amount);setDirty();}
    public long extract(BlockPos pos,long maximum,boolean simulate){long amount=Math.min(Math.max(0,maximum),amount(pos));if(!simulate&&amount>0)set(pos,amount(pos)-amount);return amount;}
    @Override public CompoundTag save(CompoundTag tag){amounts.forEach((pos,amount)->tag.putLong(Long.toString(pos),amount));return tag;}
}
