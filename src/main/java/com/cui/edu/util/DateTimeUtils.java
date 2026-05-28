package com.cui.edu.util;

import cn.hutool.core.date.DateUtil;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 时间工具类
 *
 * @author Cuicui
 */
public class DateTimeUtils extends DateUtil {

    public static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public static final DateTimeFormatter DATE_FORMATTER_OTHER = DateTimeFormatter.ofPattern("yyyyMMdd");
    public static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final DateTimeFormatter DATETIME_FORMATTER_OTHER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    public static final String START_TIME = " 00:00:00 000";
    public static final String END_TIME = " 23:59:59 999";
    /**
     * 定义可能的日期格式
     */
    private static final List<DateTimeFormatter> FORMATTERS = new ArrayList<>();

    static {
        FORMATTERS.add(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        FORMATTERS.add(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        FORMATTERS.add(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }


    /**
     * 获取当前系统日期
     */
    public static LocalDate getLocalDate() {
        return LocalDate.now();
    }

    /**
     * 获取当前系统日期时间
     */
    public static LocalDateTime getLocalDateTime() {
        return LocalDateTime.now();
    }


    /**
     * 获取当前系统日期字符串
     */
    public static String getLocalDateString() {
        return LocalDate.now().format(DATE_FORMATTER);
    }

    /**
     * 获取当前系统日期时间字符串
     */
    public static String getLocalDateTimeString() {
        return LocalDateTime.now().format(DATETIME_FORMATTER);
    }

    /**
     * 字符串转LocalTime
     */
    public static LocalTime string2LocalTime(String time) {
        return LocalTime.parse(time, TIME_FORMATTER);
    }

    /**
     * 字符串转LocalDate
     */
    public static LocalDate string2LocalDate(String date) {
        for (DateTimeFormatter formatter : FORMATTERS) {
            try {
                // 尝试用每种格式解析日期
                return LocalDate.parse(date, formatter);
            } catch (DateTimeParseException e) {
                // 捕获异常，继续尝试下一种格式
            }
        }
        // 如果所有格式都无法解析，抛出异常
        throw new IllegalArgumentException("无法解析日期: " + date);
    }

    public static LocalDate string2LocalDateOther(String date) {
        return LocalDate.parse(date, DATE_FORMATTER_OTHER);
    }

    /**
     * LocalDate转字符串
     */
    public static String localDate2String(LocalDate data) {
        return data.format(DATE_FORMATTER);
    }

    /**
     * LocalDateTime转字符串
     */
    public static String localDateTime2String(LocalDateTime data) {
        return data.format(DATE_FORMATTER);
    }

    /**
     * 字符串转LocalDateTime
     */
    public static LocalDateTime string2LocalDateTime(String dateTime) {
        return LocalDateTime.parse(dateTime, DATETIME_FORMATTER);
    }

    public static LocalDateTime string2LocalDateTimeOther(String dateTime) {
        return LocalDateTime.parse(dateTime, DATETIME_FORMATTER_OTHER);
    }

    /**
     * Date转LocalDateTime
     */
    public static LocalDateTime date2LocalDateTime(Date date) {
        Instant instant = date.toInstant();
        ZoneId zoneId = ZoneId.systemDefault();
        LocalDateTime localDateTime = instant.atZone(zoneId).toLocalDateTime();
        return localDateTime;
    }

    /**
     * Date转LocalDate
     */
    public static LocalDate date2LocalDate(Date date) {
        Instant instant = date.toInstant();
        ZoneId zoneId = ZoneId.systemDefault();
        LocalDate localDate = instant.atZone(zoneId).toLocalDate();
        return localDate;
    }

    /**
     * Date转LocalDate
     */
    public static LocalTime date2LocalTime(Date date) {
        Instant instant = date.toInstant();
        ZoneId zoneId = ZoneId.systemDefault();
        LocalTime localTime = instant.atZone(zoneId).toLocalTime();
        return localTime;
    }

    /**
     * LocalDateTime转换为Date
     */
    public static Date localDateTime2Date(LocalDateTime localDateTime) {
        ZoneId zoneId = ZoneId.systemDefault();
        ZonedDateTime zdt = localDateTime.atZone(zoneId);
        Date date = Date.from(zdt.toInstant());
        return date;
    }

    /**
     * @Description 获取指定日期的开始时间
     */
    public static LocalDateTime getStartDateTime(LocalDate localDate) {
        return LocalDateTime.of(localDate, LocalTime.MIN);
    }

    public static LocalDateTime getStartDateTime(String localDate) {
        return LocalDateTime.of(string2LocalDate(localDate), LocalTime.MIN);
    }

    public static String getStartDateTimeString(String localDate) {
        return localDate + START_TIME;
    }

    /**
     * @Description 获取指定日期的结束时间
     */
    public static LocalDateTime getEndDateTime(LocalDate localDate) {
        return LocalDateTime.of(localDate, LocalTime.MAX);
    }

    public static LocalDateTime getEndDateTime(String localDate) {
        return LocalDateTime.of(string2LocalDate(localDate), LocalTime.MAX);
    }
    public static String getEndDateTimeString(String localDate) {
        return localDate + END_TIME;
    }

    /**
     * 指定月份的下月月初时间
     *
     * @return
     */
    public static String getFirstDayOfMonth(String inputDate) {
        LocalDate date = LocalDate.parse(inputDate, DATE_FORMATTER);
        // 获取下个月的第一天
        LocalDate firstDayOfNextMonth = date.plusMonths(1).withDayOfMonth(1);
        return DATE_FORMATTER.format(firstDayOfNextMonth);
    }

    /**
     * 当月最后一天
     *
     * @return
     */
    public static String getLastDayOfMonth(String inputDate) {
        LocalDate date = LocalDate.parse(inputDate, DATE_FORMATTER);
        // 获取下个月的最后一天
        LocalDate lastDayOfNextMonth = date.plusMonths(1).withDayOfMonth(date.plusMonths(1).lengthOfMonth());
        return DATE_FORMATTER.format(lastDayOfNextMonth);
    }

    /**
     * 指定月份的上月月初时间
     *
     * @return
     */
    public static String getFirstDayOfBeforeMonth(String inputDate) {
        LocalDate date = LocalDate.parse(inputDate, DATE_FORMATTER);
        // 获取上个月的第一天
        LocalDate firstDayOfPreviousMonth = date.minusMonths(1).withDayOfMonth(1);
        return DATE_FORMATTER.format(firstDayOfPreviousMonth);
    }

    /**
     * 指定月份的上月月末时间
     *
     * @return
     */
    public static String getLastDayOfBeforeMonth(String localDate) {
        LocalDate date = LocalDate.parse(localDate, DATE_FORMATTER);
        // 获取上个月的最后一天
        LocalDate lastDayOfPreviousMonth = date.minusMonths(1).withDayOfMonth(date.minusMonths(1).lengthOfMonth());
        return DATE_FORMATTER.format(lastDayOfPreviousMonth);
    }

    public static LocalDateTime getStartOfCurrentMonth() {
        // 获取当前日期的 YearMonth 对象
        YearMonth currentMonth = YearMonth.now();
        // 获取当前月的第一天，并设置为当天的最开始时间 (00:00:00)
        return currentMonth.atDay(1).atStartOfDay();
    }

    public static LocalDateTime getEndOfCurrentMonth() {
        // 获取当前日期的 YearMonth 对象
        YearMonth currentMonth = YearMonth.now();
        // 获取当前月的最后一天，并设置为当天的最后时间 (23:59:59.999999999)
        return currentMonth.atEndOfMonth().atTime(LocalTime.MAX);
    }

    public static boolean isWithinBusinessHours(LocalTime start, LocalTime end, LocalTime now) {
        // 判断当前时间是否在start到end之间
        return !now.isBefore(start) && !now.isAfter(end);
    }
    /**
     * 时间戳转LocalDateTime
     *
     * @param timestamp
     * @return
     */
    public static LocalDateTime timestamp2LocalDateTime(Long timestamp) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
    }

    public static List<String> getDateRange(String startDateStr, String endDateStr) {
        LocalDate startDate = LocalDate.parse(startDateStr, DATE_FORMATTER);
        LocalDate endDate = LocalDate.parse(endDateStr, DATE_FORMATTER);

        List<String> dateRangeList = new ArrayList<>();
        while (!startDate.isAfter(endDate)) {
            dateRangeList.add(startDate.format(DATE_FORMATTER));
            startDate = startDate.plusDays(1);
        }

        return dateRangeList;
    }

    public static void main(String[] args) {
        List<String> dateRange = getDateRange("2022-01-01", "2022-01-10");
        for (String date : dateRange) {
            System.out.println(date);
        }
    }
}