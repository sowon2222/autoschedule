package com.example.sbb.service;

import com.example.sbb.domain.CalendarEvent;
import com.example.sbb.domain.Team;
import com.example.sbb.domain.TeamMember;
import com.example.sbb.domain.TimeSlot;
import com.example.sbb.domain.User;
import com.example.sbb.domain.WorkHour;
import com.example.sbb.dto.request.MeetingSuggestionRequest;
import com.example.sbb.dto.response.MeetingSuggestionResponse;
import com.example.sbb.repository.AssignmentRepository;
import com.example.sbb.repository.CalendarEventRepository;
import com.example.sbb.repository.TeamMemberRepository;
import com.example.sbb.repository.TeamRepository;
import com.example.sbb.repository.WorkHourRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 미팅 시간 추천 서비스
 * 팀원들의 스케줄을 고려하여 최적의 미팅 시간을 추천합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingSuggestionService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final WorkHourRepository workHourRepository;
    private final CalendarEventRepository calendarEventRepository;
    private final AssignmentRepository assignmentRepository;
    private final TimeSlotGenerator timeSlotGenerator;

    /**
     * 미팅 시간 추천
     * 
     * @param request 미팅 추천 요청
     * @return 추천된 시간대 목록
     */
    @Transactional(readOnly = true)
    public MeetingSuggestionResponse suggestMeetingTimes(MeetingSuggestionRequest request) {
        log.info("미팅 시간 추천 시작: teamId={}, durationMin={}, participants={}", 
            request.getTeamId(), request.getDurationMin(), request.getParticipantIds());
        
        // 1. 팀 조회
        Team team = teamRepository.findById(request.getTeamId())
            .orElseThrow(() -> new IllegalArgumentException("팀을 찾을 수 없습니다: " + request.getTeamId()));
        
        // 2. 참석자 목록 결정
        List<Long> participantIds = determineParticipants(request.getTeamId(), request.getParticipantIds());
        log.info("참석자 수: {}", participantIds.size());
        
        // 3. 검색 기간 결정
        LocalDate startDate = request.getPreferredStartDate() != null 
            ? request.getPreferredStartDate() 
            : LocalDate.now();
        int searchDays = request.getSearchDays() != null ? request.getSearchDays() : 14;
        LocalDate endDate = startDate.plusDays(searchDays - 1);
        
        log.info("검색 기간: {} ~ {}", startDate, endDate);
        
        // 4. 참석자들의 근무시간 수집
        List<WorkHour> allWorkHours = workHourRepository.findByTeam_Id(request.getTeamId());
        log.info("팀의 전체 근무시간 설정 수: {}", allWorkHours.size());
        
        // 참석자별 근무시간 + 팀 기본 근무시간(user_id가 null인 것)
        List<WorkHour> workHours = allWorkHours.stream()
            .filter(wh -> {
                if (wh.getUser() == null) {
                    // 팀 기본 근무시간은 모든 참석자에게 적용
                    return true;
                }
                // 개인 근무시간은 참석자 목록에 있는 경우만
                return participantIds.contains(wh.getUser().getId());
            })
            .collect(Collectors.toList());
        
        log.info("필터링된 근무시간 설정 수: {}", workHours.size());
        
        // 근무시간이 없으면 기본 근무시간 생성 (9시-18시, 월-일)
        if (workHours.isEmpty() && !participantIds.isEmpty()) {
            log.warn("근무시간 설정이 없어 기본 근무시간(9시-18시, 월-일)을 사용합니다.");
            // 기본 근무시간: 9시(540분) ~ 18시(1080분), 월요일(1) ~ 일요일(7)
            // team 변수는 이미 58번째 줄에서 선언되어 있음
            
            // 주말 포함 모든 요일에 기본 근무시간 생성 (주말은 선호도가 낮게 설정됨)
            for (int dow = 1; dow <= 7; dow++) { // 월요일(1) ~ 일요일(7) - DB 형식
                WorkHour defaultWorkHour = new WorkHour();
                defaultWorkHour.setTeam(team);
                defaultWorkHour.setUser(null); // 팀 기본 근무시간
                defaultWorkHour.setDow(dow); // DB 형식: 1=월요일, 7=일요일
                defaultWorkHour.setStartMin(540); // 9시
                defaultWorkHour.setEndMin(1080);  // 18시
                workHours.add(defaultWorkHour);
            }
            log.info("기본 근무시간 생성 완료: {}개 (월-일, 9시-18시, 주말은 선호도 낮음)", workHours.size());
        }
        
        // 5. 참석자들의 기존 일정 수집 (CalendarEvent, Assignment)
        OffsetDateTime startDateTime = startDate.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime endDateTime = endDate.atTime(23, 59, 59).atOffset(ZoneOffset.UTC);
        
        List<CalendarEvent> calendarEvents = calendarEventRepository.findAll().stream()
            .filter(e -> e.getTeam().getId().equals(request.getTeamId()))
            .filter(e -> isEventInRange(e, startDateTime, endDateTime))
            .filter(e -> isEventRelevantToParticipants(e, participantIds))
            .collect(Collectors.toList());
        
        // 6. 가능한 시간 슬롯 생성 (참석자 목록 전달하여 팀 기본 근무시간 적용)
        Map<Long, List<TimeSlot>> availableSlots = timeSlotGenerator.generateAvailableSlots(
            workHours, calendarEvents, startDate, endDate, participantIds);
        
        // 7. 모든 참석자가 동시에 가능한 시간대 찾기
        int requiredSlots = (int) Math.ceil(request.getDurationMin() / 30.0);
        List<MeetingSuggestionResponse.SuggestedTimeSlot> suggestions = 
            findCommonAvailableSlots(availableSlots, participantIds, requiredSlots);
        
        // 8. 중복 제거 및 정렬
        // 같은 시작 시간의 중복 제거
        Map<String, MeetingSuggestionResponse.SuggestedTimeSlot> uniqueSuggestions = new java.util.LinkedHashMap<>();
        for (MeetingSuggestionResponse.SuggestedTimeSlot suggestion : suggestions) {
            String key = suggestion.getStartsAt().toString();
            // 이미 있으면 선호도가 높은 것으로 유지
            if (!uniqueSuggestions.containsKey(key) || 
                uniqueSuggestions.get(key).getPreferenceScore() < suggestion.getPreferenceScore()) {
                uniqueSuggestions.put(key, suggestion);
            }
        }
        
        // 선호도 점수 내림차순 → 시간순으로 정렬
        List<MeetingSuggestionResponse.SuggestedTimeSlot> finalSuggestions = 
            new ArrayList<>(uniqueSuggestions.values());
        finalSuggestions.sort(Comparator
            .comparing(MeetingSuggestionResponse.SuggestedTimeSlot::getPreferenceScore).reversed()
            .thenComparing(MeetingSuggestionResponse.SuggestedTimeSlot::getStartsAt));
        
        log.info("추천된 시간대 수: {}", finalSuggestions.size());
        
        return new MeetingSuggestionResponse(finalSuggestions);
    }

    /**
     * 참석자 목록 결정 (없으면 팀 전체)
     */
    private List<Long> determineParticipants(Long teamId, List<Long> requestedParticipantIds) {
        if (requestedParticipantIds != null && !requestedParticipantIds.isEmpty()) {
            return requestedParticipantIds;
        }
        
        // 팀 전체 멤버
        return teamMemberRepository.findByTeamId(teamId).stream()
            .map(TeamMember::getUser)
            .map(User::getId)
            .collect(Collectors.toList());
    }

    /**
     * 이벤트가 날짜 범위와 겹치는지 확인
     */
    private boolean isEventInRange(CalendarEvent event, OffsetDateTime start, OffsetDateTime end) {
        if (event.getRecurrenceType() == null) {
            return !event.getEndsAt().isBefore(start) && !event.getStartsAt().isAfter(end);
        }
        // 반복 이벤트는 원본 이벤트만 확인 (TimeSlotGenerator에서 처리)
        return !event.getEndsAt().isBefore(start) && !event.getStartsAt().isAfter(end);
    }

    /**
     * 이벤트가 참석자들과 관련이 있는지 확인
     */
    private boolean isEventRelevantToParticipants(CalendarEvent event, List<Long> participantIds) {
        // 소유자가 참석자 목록에 있으면 관련 있음
        if (event.getOwner() != null && participantIds.contains(event.getOwner().getId())) {
            return true;
        }
        
        // 참석자 목록에 참석자가 있으면 관련 있음
        if (event.getAttendees() != null && !event.getAttendees().trim().isEmpty()) {
            try {
                Set<Long> eventAttendees = java.util.Arrays.stream(event.getAttendees().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Long::parseLong)
                    .collect(Collectors.toSet());
                
                return participantIds.stream().anyMatch(eventAttendees::contains);
            } catch (Exception e) {
                log.warn("이벤트 참석자 파싱 실패: {}", event.getAttendees(), e);
            }
        }
        
        return false;
    }

    /**
     * 모든 참석자가 동시에 가능한 시간대 찾기
     */
    private List<MeetingSuggestionResponse.SuggestedTimeSlot> findCommonAvailableSlots(
            Map<Long, List<TimeSlot>> availableSlots,
            List<Long> participantIds,
            int requiredSlots) {
        
        List<MeetingSuggestionResponse.SuggestedTimeSlot> suggestions = new ArrayList<>();
        
        if (participantIds.isEmpty() || availableSlots.isEmpty()) {
            return suggestions;
        }
        
        // 각 참석자의 가능한 슬롯을 날짜별로 그룹화
        Map<LocalDate, Map<Integer, Set<Long>>> slotsByDateAndTime = new java.util.HashMap<>();
        
        for (Long userId : participantIds) {
            List<TimeSlot> userSlots = availableSlots.get(userId);
            if (userSlots == null) {
                continue;
            }
            
            for (TimeSlot slot : userSlots) {
                if (!slot.isAvailable()) {
                    continue;
                }
                
                slotsByDateAndTime
                    .computeIfAbsent(slot.getDate(), k -> new java.util.HashMap<>())
                    .computeIfAbsent(slot.getSlotIndex(), k -> new java.util.HashSet<>())
                    .add(userId);
            }
        }
        
        // 각 날짜에 대해 연속 슬롯 찾기
        for (Map.Entry<LocalDate, Map<Integer, Set<Long>>> dateEntry : slotsByDateAndTime.entrySet()) {
            LocalDate date = dateEntry.getKey();
            Map<Integer, Set<Long>> slotsByTime = dateEntry.getValue();
            
            // 연속 슬롯 찾기
            for (int startSlotIndex = 0; startSlotIndex <= 48 - requiredSlots; startSlotIndex++) {
                // 람다 표현식에서 사용하기 위해 final 변수로 복사
                final int finalStartSlotIndex = startSlotIndex;
                
                // requiredSlots만큼 연속 슬롯이 있는지 확인
                boolean allConsecutiveAvailable = true;
                List<Integer> slotIndices = new ArrayList<>();
                
                for (int i = 0; i < requiredSlots; i++) {
                    int slotIndex = finalStartSlotIndex + i;
                    Set<Long> availableUsers = slotsByTime.get(slotIndex);
                    
                    // 모든 참석자가 이 슬롯에 가능한지 확인
                    if (availableUsers == null || !availableUsers.containsAll(participantIds)) {
                        allConsecutiveAvailable = false;
                        break;
                    }
                    
                    slotIndices.add(slotIndex);
                }
                
                if (allConsecutiveAvailable) {
                    // 첫 번째 참석자의 슬롯을 가져와서 시간 정보 추출
                    TimeSlot firstSlot = availableSlots.get(participantIds.get(0)).stream()
                        .filter(slot -> slot.getDate().equals(date) 
                            && slot.getSlotIndex() == finalStartSlotIndex
                            && slot.isAvailable())
                        .findFirst()
                        .orElse(null);
                    
                    if (firstSlot != null) {
                        // 마지막 슬롯 시간 계산
                        OffsetDateTime startsAt = firstSlot.getStartTime();
                        OffsetDateTime endsAt = startsAt.plusMinutes(requiredSlots * 30L);
                        
                        // 평균 선호도 점수 계산
                        double avgPreference = slotIndices.stream()
                            .mapToDouble(slotIndex -> {
                                // 각 참석자의 선호도 점수 평균
                                return participantIds.stream()
                                    .mapToDouble(userId -> {
                                        TimeSlot slot = availableSlots.get(userId).stream()
                                            .filter(s -> s.getDate().equals(date) 
                                                && s.getSlotIndex() == slotIndex
                                                && s.isAvailable())
                                            .findFirst()
                                            .orElse(null);
                                        return slot != null ? slot.getPreferenceScore() : 0.0;
                                    })
                                    .average()
                                    .orElse(0.0);
                            })
                            .average()
                            .orElse(0.0);
                        
                        MeetingSuggestionResponse.SuggestedTimeSlot suggestion = 
                            new MeetingSuggestionResponse.SuggestedTimeSlot();
                        suggestion.setStartsAt(startsAt);
                        suggestion.setEndsAt(endsAt);
                        suggestion.setAvailableParticipants(participantIds.size());
                        suggestion.setTotalParticipants(participantIds.size());
                        suggestion.setPreferenceScore(avgPreference);
                        
                        suggestions.add(suggestion);
                    }
                }
            }
        }
        
        return suggestions;
    }
}

