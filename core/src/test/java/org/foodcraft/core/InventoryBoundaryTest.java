package org.foodcraft.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

final class InventoryBoundaryTest {
    @Test void everyStackCapacityAndNbtCompatibility() {
        int cases=0;
        for(int limit:new int[]{1,16,64})for(int inputCount=0;inputCount<=limit;inputCount++)
            for(int outputCount=0;outputCount<=limit;outputCount++)for(int demand=1;demand<=limit;demand++)
                for(int result:new int[]{1,limit})for(int mode=0;mode<3;mode++) {
                    var input=inputCount==0?InventoryTransaction.Stack.EMPTY:new InventoryTransaction.Stack("ingredient","tag-a",inputCount,limit);
                    var output=outputCount==0?InventoryTransaction.Stack.EMPTY:new InventoryTransaction.Stack(mode==1?"other":"product",mode==2?"tag-b":"tag-a",outputCount,limit);
                    var before=List.of(input,output);
                    var plan=InventoryTransaction.prepare(before,List.of(new InventoryTransaction.Demand(0,"ingredient","tag-a",demand)),
                        Set.of(0),List.of(new InventoryTransaction.Addition(1,new InventoryTransaction.Stack("product","tag-a",result,limit))));
                    boolean expected=inputCount>=demand&&(outputCount==0||mode==0)&&outputCount+result<=limit;
                    assertEquals(expected,plan.isPresent(),"capacity or item/NBT distinction");
                    if(expected) {
                        var after=plan.orElseThrow().after();
                        assertEquals(inputCount-demand,after.get(0).count());
                        assertEquals(outputCount+result,after.get(1).count());
                        assertTrue(plan.orElseThrow().stillValid(before));
                    }
                    assertEquals(inputCount,before.get(0).count());assertEquals(outputCount,before.get(1).count());cases++;
                }
        System.out.println("FOODCRAFT ACCEPTANCE CORE CAPACITY PASS cases="+cases+" limits=1,16,64 nbt_classes=3");
    }

    @Test void seededConservationAndStaleSnapshots() {
        var random=new Random(0x464f4f4443524146L);
        for(int iteration=0;iteration<100000;iteration++) {
            int count=random.nextInt(65),required=1+random.nextInt(64),occupied=random.nextInt(65),yield=1+random.nextInt(64);
            var input=count==0?InventoryTransaction.Stack.EMPTY:new InventoryTransaction.Stack("raw","owner="+iteration,count,64);
            var output=occupied==0?InventoryTransaction.Stack.EMPTY:new InventoryTransaction.Stack("cooked","batch",occupied,64);
            var before=List.of(input,output);
            var plan=InventoryTransaction.prepare(before,List.of(new InventoryTransaction.Demand(0,"raw","owner="+iteration,required)),
                Set.of(0),List.of(new InventoryTransaction.Addition(1,new InventoryTransaction.Stack("cooked","batch",yield,64))));
            assertEquals(count>=required&&occupied+yield<=64,plan.isPresent());
            if(plan.isPresent()) {
                var result=plan.orElseThrow();
                assertEquals(count-required,result.after().get(0).count());assertEquals(occupied+yield,result.after().get(1).count());
                var changed=List.of(input,occupied==64?InventoryTransaction.Stack.EMPTY:new InventoryTransaction.Stack("cooked","batch",occupied+1,64));
                assertFalse(result.stillValid(changed));
            }
        }
        System.out.println("FOODCRAFT ACCEPTANCE CORE CONSERVATION PASS seeded_cases=100000 stale_snapshots=true");
    }
}
