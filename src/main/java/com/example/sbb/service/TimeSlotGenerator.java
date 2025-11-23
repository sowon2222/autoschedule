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
     * @param targetUserIds 대상 사용자 ID 목록 (팀 기본 근무시간 적용용, null이면 무시)
     * @return 사용자 ID를 키로 하는 사용 가능한 슬롯 맵
     */
    public Map<Long, List<TimeSlot>> generateAvailableSlots(
            List<WorkHour> workHours,
            List<CalendarEvent> calendarEvents,
            LocalDate rangeStart,
            LocalDate rangeEnd) {
        return generateAvailableSlots(workHours, calendarEvents, rangeStart, rangeEnd, null);
    }
    
    /**
     * 날짜 범위에 대해 사용자별 사용 가능한 시간 슬롯을 생성합니다.
     * 
     * @param workHours 근무시간 설정 목록
     * @param calendarEvents 캘린더 이벤트 목록 (고정/반복)
     * @param rangeStart 시작일
     * @param rangeEnd 종료일
     * @param targetUserIds 대상 사용자 ID 목록 (팀 기본 근무시간 적용용)
     * @return 사용자 ID를 키로 하는 사용 가능한 슬롯 맵
     */
    public Map<Long, List<TimeSlot>> generateAvailableSlots(
            List<WorkHour> workHours,
            List<CalendarEvent> calendarEvents,
            LocalDate rangeStart,
            LocalDate rangeEnd,
            List<Long> targetUserIds) {
        
        log.info("시간 슬롯 생성 시작: range={} ~ {}", rangeStart, rangeEnd);
        
        // 1. 근무시간 기반으로 기본 슬롯 생성
        Map<Long, List<TimeSlot>> userSlots = generateSlotsFromWorkHours(workHours, rangeStart, rangeEnd, targetUserIds);
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
            LocalDate rangeEnd,
            List<Long> targetUserIds) {
        
        Map<Long, List<TimeSlot>> userSlots = new HashMap<>();
        
        // 날짜별로 순회
        LocalDate currentDate = rangeStart;
        while (!currentDate.isAfter(rangeEnd)) {
            int dayOfWeek = currentDate.getDayOfWeek().getValue(); // 1=월요일, 7=일요일
            int dowIndex = dayOfWeek - 1; // 0=월요일, 6=일요일
            
            // 주말 처리: 기본적으로 불가능한 시간으로 설정 (필요 시 옵션으로 변경 가능)
            boolean isWeekend = dayOfWeek == 6 || dayOfWeek == 7; // 토요일 또는 일요일
            
            // 각 근무시간 설정에 대해
            for (WorkHour workHour : workHours) {
                // 요일이 일치하는지 확인
                // DB는 1-7 (월-일)을 사용하지만, dowIndex는 0-6 (월-일)이므로 변환
                int workHourDow = workHour.getDow() - 1; // DB의 1-7을 0-6으로 변환
                if (workHourDow != dowIndex) {
                    continue;
                }
                
                Long userId = workHour.getUser() != null ? workHour.getUser().getId() : null;
                
                // 근무시간 범위 내의 슬롯 생성 (7시-22시 제한 제거, 모든 시간대 허용)
                int startSlot = minutesToSlotIndex(workHour.getStartMin());
                int endSlot = minutesToSlotIndex(workHour.getEndMin());
                
                // 디버깅: 근무시간 로그
                if (log.isDebugEnabled()) {
                    log.debug("근무시간 설정: userId={}, dow={}, startMin={} (slot {}), endMin={} (slot {})", 
                        userId, workHour.getDow(), workHour.getStartMin(), startSlot, workHour.getEndMin(), endSlot);
                }
                
                if (userId == null) {
                    // 팀 기본 근무시간 (targetUserIds의 모든 사용자에게 적용)
                    if (targetUserIds != null && !targetUserIds.isEmpty()) {
                        for (Long targetUserId : targetUserIds) {
                            List<TimeSlot> slots = userSlots.computeIfAbsent(targetUserId, k -> new ArrayList<>());
                            
                            for (int slotIndex = startSlot; slotIndex < endSlot; slotIndex++) {
                                OffsetDateTime startTime = TimeSlot.calculateStartTime(currentDate, slotIndex);
                                
                                // 선호도 계산 (근무시간 내, 주말 여부 고려)
                                double preferenceScore = calculatePreference(startTime, true, isWeekend);
                                
                                TimeSlot slot = TimeSlot.builder()
                                    .date(currentDate)
                                    .slotIndex(slotIndex)
                                    .startTime(startTime)
                                    .endTime(TimeSlot.calculateEndTime(currentDate, slotIndex))
                                    .available(true)
                                    .userId(targetUserId)
                                    .preferenceScore(preferenceScore)
                                    .build();
                                slots.add(slot);
                            }
                        }
                    }
                    // targetUserIds가 null이면 건너뛰기 (기존 동작 유지)
                    continue;
                }
                
                // 개인 근무시간
                List<TimeSlot> slots = userSlots.computeIfAbsent(userId, k -> new ArrayList<>());
                
                for (int slotIndex = startSlot; slotIndex < endSlot; slotIndex++) {
                    OffsetDateTime startTime = TimeSlot.calculateStartTime(currentDate, slotIndex);
                    
                    // 선호도 계산 (근무시간 내, 주말 여부 고려)
                    double preferenceScore = calculatePreference(startTime, true, isWeekend);
                    
                    TimeSlot slot = TimeSlot.builder()
                        .date(currentDate)
                        .slotIndex(slotIndex)
                        .startTime(startTime)
                        .endTime(TimeSlot.calculateEndTime(currentDate, slotIndex))
                        .available(true)
                        .userId(userId)
                        .preferenceScore(preferenceScore)
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
     * 시간대별 선호도 점수 계산
     * - 근무시간: 높은 점수 (1.0)
     * - 나머지 시간: 중간 점수 (0.2-0.5)
     * - 주말: 낮은 점수 (0.3)
     * - 새벽(22:00-07:00): 매우 낮은 점수 (0.05, 거의 hard 제약)
     */
    private double calculatePreference(OffsetDateTime startTime, boolean isWorkHour, boolean isWeekend) {
        int hour = startTime.getHour();
        
        // 새벽 시간 (22:00-07:00): 매우 낮은 점수 (거의 hard 제약)
        if (hour >= 22 || hour < 7) {
            return 0.05;
        }
        
        // 근무시간 밖이면 중간 점수
        if (!isWorkHour) {
            // 점심시간 (12:00-13:00)
            if (hour >= 12 && hour < 13) {
                return 0.3;
            }
            // 저녁시간 (18:00-19:00)
            if (hour >= 18 && hour < 19) {
                return 0.4;
            }
            // 나머지 시간
            return 0.2;
        }
        
        // 근무시간 내
        double baseScore = 1.0;
        
        // 점심시간 (12:00-13:00): 약간 낮은 점수
        if (hour >= 12 && hour < 13) {
            baseScore = 0.8;
        }
        // 저녁시간 (18:00-19:00): 약간 낮은 점수
        else if (hour >= 18 && hour < 19) {
            baseScore = 0.9;
        }
        
        // 주말이면 선호도 낮게 (0.3으로 제한)
        if (isWeekend) {
            return Math.min(baseScore, 0.3);
        }
        
        return baseScore;
    }
    
    /**
     * 오버로드: 주말 여부를 모를 때 (기본값: 평일)
     */
    private double calculatePreference(OffsetDateTime startTime, boolean isWorkHour) {
        return calculatePreference(startTime, isWorkHour, false);
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
     * 이벤트와 겹치는 모든 슬롯을 차단
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
                    // 슬롯과 이벤트가 겹치는지 확인 (완전히 겹치거나 부분적으로 겹치면 차단)
                    // slot.startTime < eventEnd && slot.endTime > eventStart
                    if (slot.getStartTime().isBefore(eventEnd) && slot.getEndTime().isAfter(eventStart)) {
                        slot.setAvailable(false);
                        log.debug("슬롯 차단: userId={}, date={}, slotIndex={}, event={}", 
                            userId, eventDate, slot.getSlotIndex(), event.getTitle());
                    }
                }
            }
        }
    }

    /**
     * 반복 이벤트로 인한 슬롯 차단
     * 각 발생일에 대해 이벤트 시간대로 슬롯 차단
     */
    private void blockSlotsForRecurringEvent(
            Map<Long, List<TimeSlot>> userSlots,
            CalendarEvent event,
            LocalDate rangeStart,
            LocalDate rangeEnd) {
        
        String recurrenceType = event.getRecurrenceType();
        OffsetDateTime recurrenceEnd = event.getRecurrenceEndDate();
        LocalDate eventStartDate = event.getStartsAt().toLocalDate();
        OffsetDateTime eventStart = event.getStartsAt();
        OffsetDateTime eventEnd = event.getEndsAt();
        
        // 반복 이벤트 확장
        List<LocalDate> occurrenceDates = expandRecurringEvent(
            eventStartDate, recurrenceType, recurrenceEnd, rangeStart, rangeEnd);
        
        List<Long> affectedUserIds = getAffectedUserIds(event);
        if (affectedUserIds.isEmpty() && event.getOwner() != null) {
            affectedUserIds = List.of(event.getOwner().getId());
        }
        
        for (LocalDate occurrenceDate : occurrenceDates) {
            // 각 발생일에 대해 이벤트 시간대로 슬롯 차단
            // 시간 부분은 원본 이벤트의 시간을 사용
            OffsetDateTime occurrenceStart = occurrenceDate.atTime(eventStart.toLocalTime())
                .atOffset(eventStart.getOffset());
            OffsetDateTime occurrenceEnd = occurrenceDate.atTime(eventEnd.toLocalTime())
                .atOffset(eventEnd.getOffset());
            
            for (Long userId : affectedUserIds) {
                List<TimeSlot> slots = userSlots.get(userId);
                if (slots == null) continue;
                
                for (TimeSlot slot : slots) {
                    if (slot.getDate().equals(occurrenceDate)) {
                        // 슬롯과 이벤트가 겹치는지 확인
                        if (slot.getStartTime().isBefore(occurrenceEnd) && 
                            slot.getEndTime().isAfter(occurrenceStart)) {
                            slot.setAvailable(false);
                            log.debug("반복 이벤트 슬롯 차단: userId={}, date={}, slotIndex={}, event={}", 
                                userId, occurrenceDate, slot.getSlotIndex(), event.getTitle());
                        }
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

