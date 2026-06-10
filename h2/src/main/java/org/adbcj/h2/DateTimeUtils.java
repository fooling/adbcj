package org.adbcj.h2;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;


public class DateTimeUtils {
    private static final Calendar cachedCalendar = Calendar.getInstance();
    private static final int SHIFT_YEAR = 9;
    private static final int SHIFT_MONTH = 5;


    /**
     * Convert a date value to a date, using the default timezone.
     *
     * @param dateValue the date value
     * @return the date
     */
    public static Date convertDateValueToDate(long dateValue) {
        long millis = getMillis(TimeZone.getDefault(),
                yearFromDateValue(dateValue),
                monthFromDateValue(dateValue),
                dayFromDateValue(dateValue), 0, 0, 0, 0);
        return new Date(millis);
    }
    /**
     * Convert a time value to a time, using the default
     * timezone.
     *
     * @param nanos the nanoseconds since midnight
     * @return the time
     */
    public static Time convertNanoToTime(long nanos) {
        long millis = nanos / 1000000;
        long s = millis / 1000;
        millis -= s * 1000;
        long m = s / 60;
        s -= m * 60;
        long h = m / 60;
        m -= h * 60;
        long ms = getMillis(TimeZone.getDefault(),
                1970, 1, 1, (int) (h % 24), (int) m, (int) s, (int) millis);
        return new Time(ms);
    }

    /**
     * Calculate the date value (in the default timezone) from a given time in
     * milliseconds in UTC.
     *
     * @param ms the milliseconds
     * @return the date value
     */
    public static long dateValueFromDate(long ms) {
        synchronized (cachedCalendar) {
            cachedCalendar.clear();
            cachedCalendar.setTimeInMillis(ms);
            return dateValueFromCalendar(cachedCalendar);
        }
    }

    /**
     * Calculate the date value from a given calendar.
     *
     * @param cal the calendar
     * @return the date value
     */
    private static long dateValueFromCalendar(Calendar cal) {
        int year, month, day;
        year = getYear(cal);
        month = cal.get(Calendar.MONTH) + 1;
        day = cal.get(Calendar.DAY_OF_MONTH);
        return ((long) year << SHIFT_YEAR) | (month << SHIFT_MONTH) | day;
    }

    /**
     * Get the year (positive or negative) from a calendar.
     *
     * @param calendar the calendar
     * @return the year
     */
    private static int getYear(Calendar calendar) {
        int year = calendar.get(Calendar.YEAR);
        if (calendar.get(Calendar.ERA) == GregorianCalendar.BC) {
            year = 1 - year;
        }
        return year;
    }

    public static Timestamp convertDateValueToTimestamp(long dateValue, long nanos) {
        long millis = nanos / 1000000;
        nanos -= millis * 1000000;
        long s = millis / 1000;
        millis -= s * 1000;
        long m = s / 60;
        s -= m * 60;
        long h = m / 60;
        m -= h * 60;
        long ms = DateTimeUtils.getMillis(TimeZone.getDefault(),
                DateTimeUtils.yearFromDateValue(dateValue),
                DateTimeUtils.monthFromDateValue(dateValue),
                DateTimeUtils.dayFromDateValue(dateValue),
                (int) h, (int) m, (int) s, 0);
        Timestamp ts = new Timestamp(ms);
        ts.setNanos((int) (nanos + millis * 1000000));
        return ts;
    }

    /**
     * Calculate the milliseconds since 1970-01-01 (UTC) for the given date and
     * time (in the specified timezone).
     *
     * @param tz the timezone of the parameters
     * @param year the absolute year (positive or negative)
     * @param month the month (1-12)
     * @param day the day (1-31)
     * @param hour the hour (0-23)
     * @param minute the minutes (0-59)
     * @param second the number of seconds (0-59)
     * @param millis the number of milliseconds
     * @return the number of milliseconds (UTC)
     */
    public static long getMillis(TimeZone tz, int year, int month, int day, int hour, int minute, int second, int millis) {
        try {
            return getTimeTry(false, tz, year, month, day, hour, minute, second, millis);
        } catch (IllegalArgumentException e) {
            // special case: if the time simply doesn't exist because of
            // daylight saving time changes, use the lenient version
            String message = e.toString();
            if (message.indexOf("HOUR_OF_DAY") > 0) {
                if (hour < 0 || hour > 23) {
                    throw e;
                }
                return getTimeTry(true, tz, year, month, day, hour, minute, second, millis);
            } else if (message.indexOf("DAY_OF_MONTH") > 0) {
                int maxDay;
                if (month == 2) {
                    maxDay = new GregorianCalendar().isLeapYear(year) ? 29 : 28;
                } else {
                    maxDay = 30 + ((month + (month > 7 ? 1 : 0)) & 1);
                }
                if (day < 1 || day > maxDay) {
                    throw e;
                }
                // DAY_OF_MONTH is thrown for years > 2037
                // using the timezone Brasilia and others,
                // for example for 2042-10-12 00:00:00.
                hour += 6;
                return getTimeTry(true, tz, year, month, day, hour, minute, second, millis);
            } else {
                return getTimeTry(true, tz, year, month, day, hour, minute, second, millis);
            }
        }
    }
    /**
     * Get the year from a date value.
     *
     * @param x the date value
     * @return the year
     */
    public static int yearFromDateValue(long x) {
        return (int) (x >>> SHIFT_YEAR);
    }
    /**
     * Get the month from a date value.
     *
     * @param x the date value
     * @return the month (1..12)
     */
    public static int monthFromDateValue(long x) {
        return (int) (x >>> SHIFT_MONTH) & 15;
    }
    /**
     * Get the day of month from a date value.
     *
     * @param x the date value
     * @return the day (1..31)
     */
    public static int dayFromDateValue(long x) {
        return (int) (x & 31);
    }


    private static long getTimeTry(boolean lenient,
                                   TimeZone tz,
                                   int year,
                                   int month,
                                   int day,
                                   int hour,
                                   int minute,
                                   int second,
                                   int millis) {
        if (tz == null) {
            synchronized (cachedCalendar) {
                return getTimeTry(cachedCalendar, lenient, year, month, day, hour, minute, second, millis);
            }
        } else {
            Calendar c = Calendar.getInstance(tz);
            return getTimeTry(c, lenient, year, month, day, hour, minute, second, millis);
        }
    }

    private static long getTimeTry(Calendar c, boolean lenient, int year, int month, int day, int hour, int minute, int second, int millis) {
        c.clear();
        c.setLenient(lenient);
        setCalendarFields(c, year, month, day, hour, minute, second, millis);
        return c.getTime().getTime();
    }

    private static void setCalendarFields(Calendar cal, int year, int month, int day,
                                            int hour, int minute, int second, int millis) {
        if (year <= 0) {
            cal.set(Calendar.ERA, GregorianCalendar.BC);
            cal.set(Calendar.YEAR, 1 - year);
        } else {
            cal.set(Calendar.ERA, GregorianCalendar.AD);
            cal.set(Calendar.YEAR, year);
        }
        // january is 0
        cal.set(Calendar.MONTH, month - 1);
        cal.set(Calendar.DAY_OF_MONTH, day);
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, second);
        cal.set(Calendar.MILLISECOND, millis);
    }
}
