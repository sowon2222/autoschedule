package com.example.sbb.service;

import com.example.sbb.domain.CalendarEvent;
import com.example.sbb.domain.TimeSlot;
import com.example.sbb.domain.WorkHour;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 시간 슬롯 생성기
 * 근무시간을 기반으로 30분 단위 슬롯을 생성하고, 캘린더 이벤트로 인한 차단을 처리합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TimeSlotGenerator {

    private static final int SLOTS_PER_DAY = 48; // 24시간 * 2 (30분 단위)

    /**
     * 날짜 범위에 대해 사용자별 사용 가능한 시간 슬롯을 생성합니다.
     * 
     * @param workHours 근무시간 설정 목록
     * @param calendarEvents 캘린더 이벤트 목록 (고정/반복)
     * @param rangeStart 시작일
     * @param rangeEnd 종료일
     * @return 사용자 ID를 키로 하는 사용 가능한 슬롯 맵
     */
    public Map<Long, List<TimeSlot>> generateAvailableSlots(
            List<WorkHour> workHours,
            List<CalendarEvent> calendarEvents,
            LocalDate rangeStart,
            LocalDate rangeEnd) {
        
        log.info("시간 슬롯 생성 시작: range={} ~ {}", rangeStart, rangeEnd);
        
        // 1. 근무시간 기반으로 기본 슬롯 생성
        Map<Long, List<TimeSlot>> userSlots = generateSlotsFromWorkHours(workHours, rangeStart, rangeEnd);
        log.info("근무시간 기반 슬롯 생성 완료: {}명의 사용자", userSlots.size());
        
        // 2. 캘린더 이벤트로 인한 슬롯 차단
        blockSlotsByEvents(userSlots, calendarEvents, rangeStart, rangeEnd);
        log.info("이벤트 기반 슬롯 차단 완료");
        
        // 3. 사용 가능한 슬롯만 필터링
        Map<Long, List<TimeSlot>> availableSlots = userSlots.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().stream()
                    .filter(TimeSlot::isAvailable)
                    .collect(Collectors.toList())
            ));
        
        int totalAvailable = availableSlots.values().stream()
            .mapToInt(List::size)
            .sum();
        log.info("사용 가능한 슬롯 수: {}", totalAvailable);
        
        return availableSlots;
    }

    /**
     * 근무시간 설정을 기반으로 슬롯 생성
     */
    private Map<Long, List<TimeSlot>> generateSlotsFromWorkHours(
            List<WorkHour> workHours,
            LocalDate rangeStart,
            LocalDate rangeEnd) {
        
        Map<Long, List<TimeSlot>> userSlots = new HashMap<>();
        
        // 날짜별로 순회
        LocalDate currentDate = rangeStart;
        while (!currentDate.isAfter(rangeEnd)) {
            int dayOfWeek = currentDate.getDayOfWeek().getValue() - 1; // 0=월요일, 6=일요일
            
            // 각 근무시간 설정에 대해
            for (WorkHour workHour : workHours) {
                // 요일이 일치하는지 확인
                if (!workHour.getDow().equals(dayOfWeek)) {
                    continue;
                }
                
                Long userId = workHour.getUser() != null ? workHour.getUser().getId() : null;
                if (userId == null) {
                    // 팀 기본 근무시간 (모든 사용자에게 적용)
                    // TODO: 팀의 모든 사용자에게 적용하는 로직 필요
                    continue;
                }
                
                // 근무시간 범위 내의 슬롯 생성
                int startSlot = minutesToSlotIndex(workHour.getStartMin());
                int endSlot = minutesToSlotIndex(workHour.getEndMin());
                
                List<TimeSlot> slots = userSlots.computeIfAbsent(userId, k -> new ArrayList<>());
                
                for (int slotIndex = startSlot; slotIndex < endSlot; slotIndex++) {
                    TimeSlot slot = TimeSlot.builder()
                        .date(currentDate)
                        .slotIndex(slotIndex)
                        .startTime(TimeSlot.calculateStartTime(currentDate, slotIndex))
                        .endTime(TimeSlot.calculateEndTime(currentDate, slotIndex))
                        .available(true)
                        .userId(userId)
                        .build();
                    slots.add(slot);
                }
            }
            
            currentDate = currentDate.plusDays(1);
        }
        
        return userSlots;
    }

    /**
     * 분(minutes)을 슬롯 인덱스로 변환
     * 예: 0분 -> 0, 30분 -> 1, 60분 -> 2, ...
     */
    private int minutesToSlotIndex(int minutes) {
        return minutes / 30;
    }

    /**
     * 캘린더 이벤트로 인한 슬롯 차단
     */
    private void blockSlotsByEvents(
            Map<Long, List<TimeSlot>> userSlots,
            List<CalendarEvent> calendarEvents,
            LocalDate rangeStart,
            LocalDate rangeEnd) {
        
        for (CalendarEvent event : calendarEvents) {
            // 고정 이벤트 처리
            if (event.getRecurrenceType() == null) {
                blockSlotsForSingleEvent(userSlots, event);
            } else {
                // 반복 이벤트 처리
                blockSlotsForRecurringEvent(userSlots, event, rangeStart, rangeEnd);
            }
        }
    }

    /**
     * 단일 이벤트로 인한 슬롯 차단
     */
    private void blockSlotsForSingleEvent(Map<Long, List<TimeSlot>> userSlots, CalendarEvent event) {
        LocalDate eventDate = event.getStartsAt().toLocalDate();
        OffsetDateTime eventStart = event.getStartsAt();
        OffsetDateTime eventEnd = event.getEndsAt();
        
        // 이벤트 참석자 확인
        List<Long> affectedUserIds = getAffectedUserIds(event);
        if (affectedUserIds.isEmpty()) {
            // 참석자가 없으면 소유자만 영향
            if (event.getOwner() != null) {
                affectedUserIds = List.of(event.getOwner().getId());
            }
        }
        
        for (Long userId : affectedUserIds) {
            List<TimeSlot> slots = userSlots.get(userId);
            if (slots == null) continue;
            
            for (TimeSlot slot : slots) {
                if (slot.getDate().equals(eventDate)) {
                    // 슬롯과 이벤트가 겹치는지 확인
                    if (slot.getStartTime().isBefore(eventEnd) && slot.getEndTime().isAfter(eventStart)) {
                        slot.setAvailable(false);
                    }
                }
            }
        }
    }

    /**
     * 반복 이벤트로 인한 슬롯 차단
     */
    private void blockSlotsForRecurringEvent(
            Map<Long, List<TimeSlot>> userSlots,
            CalendarEvent event,
            LocalDate rangeStart,
            LocalDate rangeEnd) {
        
        String recurrenceType = event.getRecurrenceType();
        OffsetDateTime recurrenceEnd = event.getRecurrenceEndDate();
        LocalDate eventStartDate = event.getStartsAt().toLocalDate();
        
        // 반복 이벤트 확장
        List<LocalDate> occurrenceDates = expandRecurringEvent(
            eventStartDate, recurrenceType, recurrenceEnd, rangeStart, rangeEnd);
        
        for (LocalDate occurrenceDate : occurrenceDates) {
            // 각 발생일에 대해 이벤트 시간대로 슬롯 차단
            int startSlot = timeToSlotIndex(event.getStartsAt().toLocalTime());
            int endSlot = timeToSlotIndex(event.getEndsAt().toLocalTime());
            
            List<Long> affectedUserIds = getAffectedUserIds(event);
            if (affectedUserIds.isEmpty() && event.getOwner() != null) {
                affectedUserIds = List.of(event.getOwner().getId());
            }
            
            for (Long userId : affectedUserIds) {
                List<TimeSlot> slots = userSlots.get(userId);
                if (slots == null) continue;
                
                for (TimeSlot slot : slots) {
                    if (slot.getDate().equals(occurrenceDate) &&
                        slot.getSlotIndex() >= startSlot &&
                        slot.getSlotIndex() < endSlot) {
                        slot.setAvailable(false);
                    }
                }
            }
        }
    }

    /**
     * 반복 이벤트를 날짜 범위 내에서 확장
     */
    private List<LocalDate> expandRecurringEvent(
            LocalDate startDate,
            String recurrenceType,
            OffsetDateTime recurrenceEnd,
            LocalDate rangeStart,
            LocalDate rangeEnd) {
        
        List<LocalDate> dates = new ArrayList<>();
        LocalDate current = startDate;
        LocalDate endDate = recurrenceEnd != null ? recurrenceEnd.toLocalDate() : rangeEnd;
        
        if (endDate.isBefore(rangeStart)) {
            return dates; // 범위 밖
        }
        
        LocalDate actualStart = current.isBefore(rangeStart) ? rangeStart : current;
        LocalDate actualEnd = endDate.isAfter(rangeEnd) ? rangeEnd : endDate;
        
        switch (recurrenceType) {
            case "DAILY" -> {
                while (!current.isAfter(actualEnd)) {
                    if (!current.isBefore(actualStart)) {
                        dates.add(current);
                    }
                    current = current.plusDays(1);
                }
            }
            case "WEEKLY" -> {
                while (!current.isAfter(actualEnd)) {
                    if (!current.isBefore(actualStart)) {
                        dates.add(current);
                    }
                    current = current.plusWeeks(1);
                }
            }
            case "MONTHLY" -> {
                while (!current.isAfter(actualEnd)) {
                    if (!current.isBefore(actualStart)) {
                        dates.add(current);
                    }
                    current = current.plusMonths(1);
                }
            }
            case "YEARLY" -> {
                while (!current.isAfter(actualEnd)) {
                    if (!current.isBefore(actualStart)) {
                        dates.add(current);
                    }
                    current = current.plusYears(1);
                }
            }
        }
        
        return dates;
    }

    /**
     * LocalTime을 슬롯 인덱스로 변환
     */
    private int timeToSlotIndex(java.time.LocalTime time) {
        return (time.getHour() * 2) + (time.getMinute() / 30);
    }

    /**
     * 이벤트의 영향받는 사용자 ID 목록 추출
     * attendees 필드가 "3,5,9" 형식으로 저장되어 있다고 가정
     */
    private List<Long> getAffectedUserIds(CalendarEvent event) {
        if (event.getAttendees() == null || event.getAttendees().trim().isEmpty()) {
            return List.of();
        }
        
        try {
            return java.util.Arrays.stream(event.getAttendees().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("이벤트 참석자 파싱 실패: {}", event.getAttendees(), e);
            return List.of();
        }
    }
}

