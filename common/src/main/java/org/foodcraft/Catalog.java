package org.foodcraft;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

public final class Catalog {
    private Catalog() {}
    public record Entry(String legacy, String id, String kind, String legacy_class, String texture,
                        int nutrition, float saturation, boolean always_edible, int durability,
                        String crop, String effect, String[] constructor_args, String status, boolean glint,String creative_tab,
                        Float hardness,float[] collision,int lit_light,int stack_size,String sound) {
        public String path() { return id.substring(id.indexOf(':')+1); }
        public boolean block() { return constructor_args != null; }
        public String effectName() { return effect == null ? "none" : effect; }
    }
    public static final List<Entry> ENTRIES = load();
    public static final Map<String, int[][]> LAYOUTS = layouts();
    public record TreeVoxel(int[] offset,String block){}
    public static final Map<String,List<TreeVoxel>> TREES=trees();
    private static Map<String,List<TreeVoxel>> trees(){
        var stream=Catalog.class.getResourceAsStream("/assets/foodcraft/tree-geometry.json");
        if(stream==null)throw new IllegalStateException("FoodCraft tree geometry is missing");
        try(var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)){
            var gson=new Gson();var object=gson.fromJson(reader,JsonObject.class);Map<String,List<TreeVoxel>> result=new LinkedHashMap<>();
            object.entrySet().forEach(entry->result.put(entry.getKey(),List.of(gson.fromJson(entry.getValue(),TreeVoxel[].class))));return Map.copyOf(result);
        }catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
    }
    private static List<Entry> load() {
        var stream=Catalog.class.getResourceAsStream("/assets/foodcraft/catalog.json");
        if(stream==null) throw new IllegalStateException("FoodCraft catalog is missing");
        try(var reader=new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            Entry[] entries=new Gson().fromJson(reader,Entry[].class);
            return List.of(entries);
        } catch(java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
    }
    private static Map<String,int[][]> layouts() {
        var stream=Catalog.class.getResourceAsStream("/assets/foodcraft/machine-layouts.json");
        if(stream==null) throw new IllegalStateException("FoodCraft machine layouts are missing");
        try(var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
            JsonObject object=new Gson().fromJson(reader,JsonObject.class);
            Map<String,int[][]> result=new LinkedHashMap<>();
            object.entrySet().forEach(entry->{
                JsonArray array=entry.getValue().getAsJsonArray();
                int[][] values=new int[array.size()][3];
                for(int i=0;i<array.size();i++) for(int j=0;j<3;j++) values[i][j]=array.get(i).getAsJsonArray().get(j).getAsInt();
                result.put(entry.getKey(),values);
            });
            return Map.copyOf(result);
        } catch(java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
    }
}
