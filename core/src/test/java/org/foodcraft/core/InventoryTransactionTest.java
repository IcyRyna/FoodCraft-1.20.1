package org.foodcraft.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import java.util.Set;
import static org.foodcraft.core.InventoryTransaction.*;
import static org.junit.jupiter.api.Assertions.*;

class InventoryTransactionTest {
    private Stack food(int count) { return new Stack("chicken", "", count, 64); }
    private Stack result(int count) { return new Stack("wings", "", count, 64); }
    @Test void oneChickenProducesTwoWingsAndConsumesExactlyOne() {
        var input = List.of(food(8), Stack.EMPTY);
        var plan = prepare(input, List.of(new Demand(0,"chicken","",1)), Set.of(0),
                List.of(new Addition(1,result(2)))).orElseThrow();
        assertEquals(List.of(food(7),result(2)),plan.after());
        assertEquals(List.of(food(8),Stack.EMPTY),input);
    }
    @ParameterizedTest @ValueSource(ints={63,64}) void blockedOutputDoesNotConsumeInputs(int outputCount) {
        var input = List.of(food(1),result(outputCount));
        assertTrue(prepare(input,List.of(new Demand(0,"chicken","",1)),Set.of(0),
                List.of(new Addition(1,result(2)))).isEmpty());
        assertEquals(food(1),input.get(0));
    }
    @Test void wrongOutputNbtNeverMergesOrConsumes() {
        var input = List.of(food(1),new Stack("wings","custom:1",1,64));
        assertTrue(prepare(input,List.of(new Demand(0,"chicken","",1)),Set.of(0),
                List.of(new Addition(1,result(2)))).isEmpty());
    }
    @Test void unmentionedIngredientSlotPreventsThreeChickenExploit() {
        var input = List.of(food(1),food(1),food(1),Stack.EMPTY);
        assertTrue(prepare(input,List.of(new Demand(0,"chicken","",1)),Set.of(0,1,2),
                List.of(new Addition(3,result(2)))).isEmpty());
    }
    @Test void transactionRejectsChangedInventoryAfterPreparation() {
        var input=List.of(food(1),Stack.EMPTY);
        var plan=prepare(input,List.of(new Demand(0,"chicken","",1)),Set.of(0),
                List.of(new Addition(1,result(2)))).orElseThrow();
        assertFalse(plan.stillValid(List.of(Stack.EMPTY,Stack.EMPTY)));
    }
    @Test void goldenBonusAndContainerBothMustFitBeforeCommit() {
        var input=List.of(food(2),result(61),new Stack("bucket","",16,16));
        assertTrue(prepare(input,List.of(new Demand(0,"chicken","",1)),Set.of(0),
                List.of(new Addition(1,result(3)),new Addition(2,new Stack("bucket","",1,16)))).isEmpty());
    }
    @Test void itemMaximumIsRespected() {
        assertTrue(prepare(List.of(food(1),new Stack("wings","",1,1)),
                List.of(new Demand(0,"chicken","",1)),Set.of(0),List.of(new Addition(1,result(2)))).isEmpty());
    }
    @Test void consumedContainerReturnsToVacatedInputSlot() {
        var milk=new Stack("milk_bucket","",1,1);
        var bucket=new Stack("bucket","",1,16);
        var plan=prepare(List.of(milk,Stack.EMPTY),List.of(new Demand(0,"milk_bucket","",1)),Set.of(0),
                List.of(new Addition(0,bucket),new Addition(1,result(1)))).orElseThrow();
        assertEquals(List.of(bucket,result(1)),plan.after());
    }
    @Test void containerRemainderCannotOverwriteUnconsumedInput() {
        var bottles=new Stack("water_bottle","",2,64);
        assertTrue(prepare(List.of(bottles,Stack.EMPTY),List.of(new Demand(0,"water_bottle","",1)),Set.of(0),
                List.of(new Addition(0,new Stack("glass_bottle","",1,64)),new Addition(1,result(1)))).isEmpty());
    }
    @Test void invalidHeatAndBoundaryValuesAreHandled() {
        assertFalse(correctHeat(Double.NaN,250,400,0));
        assertFalse(correctHeat(249.9,250,400,0));
        assertTrue(correctHeat(250,250,400,0));
        assertTrue(correctHeat(400,250,400,0));
        assertFalse(correctHeat(400.1,250,400,0));
    }
}
