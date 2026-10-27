package app.vultrpocket.mobile;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Keeps API history separate from the UTC calendar-month totals. */
final class BandwidthSeries {
    static final int DATE_RANGE_DAYS=60;
    static final class Day {
        final LocalDate date;
        final long incoming,outgoing;
        Day(String date,long incoming,long outgoing){
            if(date==null||!date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))throw new IllegalArgumentException("invalid_date");
            this.date=LocalDate.parse(date);
            if(incoming<0||outgoing<0)throw new IllegalArgumentException("invalid_bytes");
            this.incoming=incoming;this.outgoing=outgoing;
        }
    }
    static final class Result {
        final List<Day> daily;
        final long incoming,outgoing;
        final int monthDays;
        Result(List<Day> daily,long incoming,long outgoing,int monthDays){this.daily=Collections.unmodifiableList(daily);this.incoming=incoming;this.outgoing=outgoing;this.monthDays=monthDays;}
    }
    static Result aggregate(List<Day> raw,LocalDate today){
        List<Day> daily=new ArrayList<>();Set<LocalDate> seen=new HashSet<>();
        long incoming=0,outgoing=0;int monthDays=0;YearMonth month=YearMonth.from(today);
        for(Day day:raw){
            if(!seen.add(day.date))throw new IllegalArgumentException("duplicate_date");
            if(day.date.isAfter(today))continue;
            daily.add(day);
            if(YearMonth.from(day.date).equals(month)){incoming=Math.addExact(incoming,day.incoming);outgoing=Math.addExact(outgoing,day.outgoing);monthDays++;}
        }
        daily.sort(Comparator.comparing(day->day.date));
        return new Result(daily,incoming,outgoing,monthDays);
    }
}
