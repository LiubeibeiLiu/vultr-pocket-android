package app.vultrpocket.mobile;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

public final class BandwidthSeriesTest {
    private static int passed;
    private static void check(boolean value,String label){if(!value)throw new AssertionError(label);passed++;}
    private static void rejects(Runnable action,String label){try{action.run();throw new AssertionError(label);}catch(IllegalArgumentException|ArithmeticException expected){passed++;}}
    public static void main(String[] args){
        LocalDate today=LocalDate.parse("2026-10-03");
        BandwidthSeries.Day previous=new BandwidthSeries.Day("2026-09-30",900,800);
        BandwidthSeries.Day first=new BandwidthSeries.Day("2026-10-01",100,200);
        BandwidthSeries.Day third=new BandwidthSeries.Day("2026-10-03",0,300);
        BandwidthSeries.Day future=new BandwidthSeries.Day("2026-10-04",999,999);
        BandwidthSeries.Result r=BandwidthSeries.aggregate(Arrays.asList(third,future,previous,first),today);
        check(BandwidthSeries.DATE_RANGE_DAYS==60,"explicit supported history range");
        check(r.daily.size()==3,"history kept and future excluded");
        check(r.daily.get(0).date.toString().equals("2026-09-30"),"cross-month history preserved");
        check(r.daily.get(2)==third,"ascending API dates");
        check(r.incoming==100,"previous month not in monthly incoming");
        check(r.outgoing==500,"monthly outgoing sums reported dates");
        check(r.monthDays==2,"missing day not invented");
        check(r.daily.get(2).incoming==0,"real zero retained");
        BandwidthSeries.Result old=BandwidthSeries.aggregate(Collections.singletonList(previous),today);
        check(old.daily.size()==1&&old.monthDays==0,"history without current month retained");
        check(BandwidthSeries.aggregate(Collections.emptyList(),today).daily.isEmpty(),"empty API history");
        rejects(()->new BandwidthSeries.Day("2026-10-01",-1,0),"negative incoming");
        rejects(()->new BandwidthSeries.Day("2026-10-01",0,-1),"negative outgoing");
        rejects(()->new BandwidthSeries.Day("2026-1-01",0,0),"noncanonical date");
        rejects(()->new BandwidthSeries.Day(null,0,0),"null date");
        try{new BandwidthSeries.Day("2026-02-29",0,0);throw new AssertionError("invalid leap date");}catch(java.time.DateTimeException expected){passed++;}
        check(new BandwidthSeries.Day("2024-02-29",1,2).date.getDayOfMonth()==29,"valid leap date");
        rejects(()->BandwidthSeries.aggregate(Arrays.asList(first,first),today),"duplicate API date");
        rejects(()->BandwidthSeries.aggregate(Arrays.asList(new BandwidthSeries.Day("2026-10-01",Long.MAX_VALUE,0),new BandwidthSeries.Day("2026-10-02",1,0)),today),"overflow rejected");
        check(BandwidthSeries.aggregate(Collections.singletonList(future),today).daily.isEmpty(),"future-only data omitted");
        try{r.daily.clear();throw new AssertionError("mutable result");}catch(UnsupportedOperationException expected){passed++;}
        System.out.println("Bandwidth series: "+passed+"/"+passed+" assertions passed");
    }
}
