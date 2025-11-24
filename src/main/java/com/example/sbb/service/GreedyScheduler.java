package com.example.sbb.service;

import com.example.sbb.domain.Assignment;
import com.example.sbb.domain.AssignmentSource;
import com.example.sbb.domain.Schedule;
import com.example.sbb.domain.Task;
import com.example.sbb.domain.TimeSlot;
import com.example.sbb.repository.AssignmentRepository;
import com.example.sbb.repository.ScheduleRepository;
import com.example.sbb.repository.TaskRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 그리디 스케줄러
 * 마감일 임박 순, 우선순위 순으로 작업을 배치합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GreedyScheduler {

    private final TaskRepository taskRepository;
    private final ScheduleRepository scheduleRepository;
    private final AssignmentRepository assignmentRepository;

    /**
     * 전역 usedSlots (모든 팀에서 공유)
     * 사용자 ID를 키로 하는 슬롯 키 Set 맵
     */
    private final Map<Long, Set<String>> globalUsedSlots = new ConcurrentHashMap<>();
    
    /**
     * 그리디 알고리즘으로 작업을 배치합니다.
     * 
     * @param tasks 배치할 작업 목록
     * @param availableSlots 사용자별 사용 가능한 슬롯 맵
     * @param schedule 스케줄 엔티티
     * @param partialAssignments 부분 배치 정보를 수집할 맵 (null 가능)
     * @return 생성된 Assignment 목록
     */
    @Transactional
    public List<Assignment> scheduleTasks(
            List<Task> tasks,
            Map<Long, List<TimeSlot>> availableSlots,
            Schedule schedule,
            Map<Long, PartialAssignmentInfo> partialAssignments) {
        
        log.info("그리디 배치 시작: 작업 수={}, 사용자 수={}", tasks.size(), availableSlots.size());
        
        // 1. 작업 정렬: 마감일이 있는 작업 우선, 그 다음 정렬 규칙 적용
        // 마감일이 있는 작업끼리는:
        //   - 마감일이 더 빠른 작업 우선
        //   - 남은 durationMin이 큰 작업 우선
        //   - priority가 높은 작업 우선
        List<Task> sortedTasks = tasks.stream()
            .sorted(Comparator
                .comparing((Task t) -> t.getDueAt() == null)  // 마감일 없는 작업이 뒤로
                .thenComparing((Task t) -> {
                    if (t.getDueAt() == null) {
                        return OffsetDateTime.MAX; // 마감일 없으면 가장 나중
                    }
                    return t.getDueAt(); // 마감일이 빠른 순
                })
                .thenComparing((Task t) -> {
                    // 남은 durationMin이 큰 작업 우선 (같은 마감일이면 긴 작업부터)
                    return -t.getDurationMin(); // 음수로 내림차순
                })
                .thenComparing(Comparator.comparing(Task::getPriority).reversed())) // priority 높은 순
            .collect(Collectors.toList());
        
        log.info("작업 정렬 완료");
        
        // 2. 사용자별 슬롯을 날짜와 인덱스 순으로 정렬
        Map<Long, List<TimeSlot>> sortedSlots = availableSlots.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().stream()
                    .sorted(Comparator
                        .comparing(TimeSlot::getDate)
                        .thenComparing(TimeSlot::getSlotIndex))
                    .collect(Collectors.toList()),
                (e1, e2) -> e1,
                LinkedHashMap::new
            ));
        
        // 3. 각 작업을 순회하며 배치
        List<Assignment> assignments = new ArrayList<>();
        // 전역 usedSlots 사용 (모든 팀에서 같은 사용자의 시간 공유)
        // 각 사용자별로 globalUsedSlots에서 가져오거나 새로 생성
        Map<Long, Set<String>> usedSlots = new LinkedHashMap<>();
        for (Long userId : availableSlots.keySet()) {
            // 전역 usedSlots에서 가져오거나 새로 생성 (동시성 안전)
            // ConcurrentHashMap의 keySet()을 사용하여 thread-safe Set 생성
            usedSlots.put(userId, globalUsedSlots.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()));
        }
        int successCount = 0;
        int failCount = 0;
        
        for (Task task : sortedTasks) {
            List<Assignment> taskAssignments = tryAssignTask(task, sortedSlots, usedSlots, schedule, partialAssignments);
            if (taskAssignments != null && !taskAssignments.isEmpty()) {
                assignments.addAll(taskAssignments);
                
                // 부분 배치 여부 확인
                if (partialAssignments != null) {
                    // Assignment의 실제 시간 차이를 계산 (슬롯 수 기반이 더 정확하지만, 
                    // Assignment가 이미 생성되었으므로 시간 차이로 계산)
                    int assignedMinutes = taskAssignments.stream()
                        .mapToInt(a -> {
                            long minutes = java.time.Duration.between(a.getStartsAt(), a.getEndsAt()).toMinutes();
                            // 음수나 0이면 슬롯 수로 추정 (메타 정보에서 가져올 수도 있지만, 시간 차이로 계산)
                            if (minutes <= 0) {
                                // 메타 정보에서 슬롯 수 추출 시도
                                String meta = a.getMeta();
                                if (meta != null && meta.contains("\"slots\"")) {
                                    try {
                                        // 간단한 파싱: "slots":6 형식
                                        int startIdx = meta.indexOf("\"slots\":");
                                        if (startIdx >= 0) {
                                            int endIdx = meta.indexOf(",", startIdx);
                                            if (endIdx < 0) endIdx = meta.indexOf("}", startIdx);
                                            if (endIdx > startIdx) {
                                                int slots = Integer.parseInt(meta.substring(startIdx + 8, endIdx).trim());
                                                return slots * 30;
                                            }
                                        }
                                    } catch (Exception e) {
                                        // 파싱 실패 시 시간 차이 사용
                                    }
                                }
                            }
                            return (int) minutes;
                        })
                        .sum();
                    int requiredMinutes = task.getDurationMin();
                    
                    if (assignedMinutes < requiredMinutes) {
                        PartialAssignmentInfo info = new PartialAssignmentInfo();
                        info.taskId = task.getId();
                        info.requiredMinutes = requiredMinutes;
                        info.assignedMinutes = assignedMinutes;
                        info.reason = "마감일까지 가능한 시간이 부족하여 부분 배치됨";
                        partialAssignments.put(task.getId(), info);
                        log.warn("부분 배치: taskId={}, assigned={}분 / required={}분, assignments={}", 
                            task.getId(), assignedMinutes, requiredMinutes, taskAssignments.size());
                    }
                }
                
                successCount++;
                log.debug("작업 배치 성공: taskId={}, title={}, assignments={}", 
                    task.getId(), task.getTitle(), taskAssignments.size());
            } else {
                failCount++;
                // 배치 실패 시 부분 배치 정보에 추가
                if (partialAssignments != null) {
                    PartialAssignmentInfo info = new PartialAssignmentInfo();
                    info.taskId = task.getId();
                    info.requiredMinutes = task.getDurationMin();
                    info.assignedMinutes = 0;
                    if (task.getDueAt() == null) {
                        info.reason = "사용 가능한 시간 슬롯 부족";
                    } else if (task.getDueAt().isBefore(OffsetDateTime.now(java.time.ZoneOffset.UTC))) {
                        info.reason = "마감일이 이미 지났습니다";
                    } else {
                        info.reason = "마감일까지 연속 시간 부족으로 배치 실패";
                    }
                    partialAssignments.put(task.getId(), info);
                }
                log.warn("작업 배치 실패: taskId={}, title={}", task.getId(), task.getTitle());
            }
        }
        
        log.info("그리디 배치 완료: 성공 작업={}, 실패 작업={}, 총 Assignment={}", 
            successCount, failCount, assignments.size());
        
        return assignments;
    }

    /**
     * 단일 작업을 배치 시도 (분할 가능한 경우 여러 Assignment 반환)
     */
    private List<Assignment> tryAssignTask(
            Task task,
            Map<Long, List<TimeSlot>> availableSlots,
            Map<Long, Set<String>> usedSlots,
            Schedule schedule,
            Map<Long, PartialAssignmentInfo> partialAssignments) {
        
        // 작업에 할당된 사용자 확인
        Long targetUserId = task.getAssignee() != null ? task.getAssignee().getId() : null;
        
        // 필요한 슬롯 수 계산 (30분 단위)
        int requiredSlots = (int) Math.ceil(task.getDurationMin() / 30.0);
        
        // 마감일 확인
        OffsetDateTime dueAt = task.getDueAt();
        
        // 사용자별로 배치 시도 (마감일 검증은 findConsecutiveSlots 내부에서 처리)
        List<Long> candidateUserIds = targetUserId != null 
            ? List.of(targetUserId)
            : new ArrayList<>(availableSlots.keySet());
        
        log.info("작업 배치 시작: taskId={}, title={}, dueAt={}, durationMin={}, requiredSlots={}, splittable={}, candidateUsers={}", 
            task.getId(), task.getTitle(), dueAt, task.getDurationMin(), requiredSlots, task.isSplittable(), candidateUserIds.size());
        
        for (Long userId : candidateUserIds) {
            List<TimeSlot> userSlots = availableSlots.get(userId);
            if (userSlots == null || userSlots.isEmpty()) {
                log.debug("사용자 슬롯 없음: taskId={}, userId={}", task.getId(), userId);
                continue;
            }
            
            log.debug("사용자별 배치 시도: taskId={}, userId={}, userSlots={}, usedSlots={}", 
                task.getId(), userId, userSlots.size(), 
                usedSlots.getOrDefault(userId, new HashSet<>()).size());
            
            // 마감일을 고려하여 사용 가능한 연속 슬롯 찾기
            // findConsecutiveSlots 내부에서 usedSlots를 업데이트하므로 별도 처리 불필요
            List<Assignment> assignments = findConsecutiveSlots(
                userSlots, 
                usedSlots.computeIfAbsent(userId, k -> new HashSet<>()), 
                requiredSlots, 
                task.isSplittable(),
                dueAt,
                task,
                schedule,
                partialAssignments);
            
            if (assignments != null && !assignments.isEmpty()) {
                // 배치 성공
                log.info("작업 배치 성공: taskId={}, userId={}, assignments={}", 
                    task.getId(), userId, assignments.size());
                return assignments;
            } else {
                log.warn("사용자별 배치 실패: taskId={}, userId={}, reason=findConsecutiveSlots returned null", 
                    task.getId(), userId);
            }
        }
        
        log.error("작업 배치 최종 실패: taskId={}, title={}, dueAt={}, durationMin={}, requiredSlots={}, splittable={}, 시도한 사용자 수={}", 
            task.getId(), task.getTitle(), dueAt, task.getDurationMin(), requiredSlots, task.isSplittable(), candidateUserIds.size());
        
        return null; // 배치 실패
    }

    /**
     * 연속된 슬롯 찾기
     * 
     * @param availableSlots 사용 가능한 슬롯 목록
     * @param usedSlots 이미 사용된 슬롯 목록 (이 메서드에서 업데이트됨)
     * @param requiredSlots 필요한 슬롯 수
     * @param splittable 분할 가능 여부
     * @param dueAt 작업 마감일 (null이면 제한 없음)
     * @param task 작업 (마감일 임박도 확인용)
     * @param schedule 스케줄 엔티티
     * @param partialAssignments 부분 배치 정보 수집 맵 (null 가능)
     * @return 생성된 Assignment 목록
     */
    private List<Assignment> findConsecutiveSlots(
            List<TimeSlot> availableSlots,
            Set<String> usedSlots,
            int requiredSlots,
            boolean splittable,
            OffsetDateTime dueAt,
            Task task,
            Schedule schedule,
            Map<Long, PartialAssignmentInfo> partialAssignments) {
        
        // 마감일 임박 여부 확인 (24시간 이내면 긴급 작업)
        // UTC 기준으로 현재 시간과 마감일 비교 (일관성 유지) -> 시간대 차이 방지.......... 
        final OffsetDateTime now = OffsetDateTime.now(java.time.ZoneOffset.UTC);
        final boolean isUrgent = dueAt != null 
            ? java.time.Duration.between(now, dueAt).toHours() <= 24
            : false;
        
        // 사용 가능한 슬롯에서 이미 사용된 슬롯 제외 (slotKey로 비교)
        // 중요: usedSlots를 먼저 확인하여 이미 사용된 슬롯은 제외
        // 현재 시각 이전 슬롯 제외 (과거 시간에는 배치 불가)
        // 선호도 기반 필터링: 최소 선호도(0.1) 이하는 제외
        // 단, 긴급 작업(마감일 24시간 이내)의 경우 선호도 필터링 완화
        List<TimeSlot> freeSlots = availableSlots.stream()
            .filter(slot -> {
                // 먼저 usedSlots 확인 (가장 중요!)
                boolean isUsed = usedSlots.contains(slot.getSlotKey());
                if (isUsed) {
                    log.debug("슬롯 필터링됨 (이미 사용됨): taskId={}, slotKey={}, slot={}", 
                        task.getId(), slot.getSlotKey(), slot.getStartTime());
                    return false;
                }
                return true;
            })
            .filter(slot -> {
                // 현재 시각 이전 슬롯 제외 (과거 시간에는 배치 불가)
                // 슬롯의 시작 시간이 현재 시각 이후여야 함
                boolean isPast = slot.getStartTime().isBefore(now);
                if (isPast) {
                    log.debug("슬롯 필터링됨 (과거 시간): taskId={}, slot={}, now={}", 
                        task.getId(), slot.getStartTime(), now);
                    return false;
                }
                return true;
            })
            .filter(slot -> {
                double score = slot.getPreferenceScore();
                // 긴급 작업의 경우 선호도 필터링 완화 (최소 0.05 이상이면 허용)
                // 일반 작업의 경우 최소 선호도(0.1) 이하는 제외
                double minScore = isUrgent ? 0.05 : 0.1;
                boolean isValid = score > minScore;
                if (!isValid) {
                    log.debug("슬롯 필터링됨 (낮은 선호도): taskId={}, preferenceScore={}, slot={}, isUrgent={}", 
                        task.getId(), score, slot.getStartTime(), isUrgent);
                }
                return isValid;
            })
            .collect(Collectors.toList());
        
        log.info("작업 배치 시도: taskId={}, title={}, requiredSlots={}, 사용 가능한 슬롯 수={}, isUrgent={}, dueAt={}", 
            task.getId(), task.getTitle(), requiredSlots, freeSlots.size(), isUrgent, dueAt);
        
        if (freeSlots.isEmpty()) {
            log.warn("작업 배치 실패: 사용 가능한 슬롯이 없음. taskId={}, title={}, requiredSlots={}, isUrgent={}", 
                task.getId(), task.getTitle(), requiredSlots, isUrgent);
            return null;
        }
        
        // 마감일이 있으면 마감일 이전에 작업이 완료될 수 있는 슬롯만 필터링
        // 작업의 종료 시간(마지막 슬롯의 종료 시간)이 마감일시 이전이어야 함
        // 주의: 분할 가능한 작업의 경우, 마감일 전에 일부라도 배치할 수 있으면 허용
        if (dueAt != null) {
            int beforeFilterCount = freeSlots.size();
            
            // 분할 가능한 작업의 경우: 마감일 전에 시작할 수 있는 슬롯만 필터링 (완료 여부는 나중에 확인)
            // 분할 불가능한 작업의 경우: 마감일 전에 완료할 수 있는 슬롯만 필터링
            if (splittable) {
                // 분할 가능: 슬롯의 시작 시간이 마감일 이전이거나 같으면 OK (일부라도 배치 가능)
                // 마감일과 같은 시간도 허용 (!isAfter 사용)
                freeSlots = freeSlots.stream()
                    .filter(slot -> !slot.getStartTime().isAfter(dueAt))
                    .collect(Collectors.toList());
            } else {
                // 분할 불가능: 슬롯의 시작 시간 + requiredSlots * 30분이 마감일 이전이어야 함
                // 긴급 작업의 경우 마감일과 같은 시간까지 허용 (isBefore -> !isAfter)
                freeSlots = freeSlots.stream()
                    .filter(slot -> {
                        OffsetDateTime taskEndTime = slot.getStartTime().plusMinutes(requiredSlots * 30L);
                        // 긴급 작업은 마감일과 같은 시간까지 허용, 일반 작업은 마감일 이전만 허용
                        boolean isValid = isUrgent ? !taskEndTime.isAfter(dueAt) : taskEndTime.isBefore(dueAt);
                        if (!isValid) {
                            log.debug("마감일 필터링 (분할 불가): taskId={}, slotStart={}, taskEndTime={}, dueAt={}, requiredSlots={}, isUrgent={}", 
                                task.getId(), slot.getStartTime(), taskEndTime, dueAt, requiredSlots, isUrgent);
                        }
                        return isValid;
                    })
                    .collect(Collectors.toList());
            }
            
            log.info("마감일 필터링 후: taskId={}, 필터링 전={}, 필터링 후={}, requiredSlots={}, dueAt={}, splittable={}", 
                task.getId(), beforeFilterCount, freeSlots.size(), requiredSlots, dueAt, splittable);
        }
        
        if (freeSlots.isEmpty()) {
            log.warn("마감일 필터링 후 사용 가능한 슬롯이 없음: taskId={}, title={}, requiredSlots={}, dueAt={}", 
                task.getId(), task.getTitle(), requiredSlots, dueAt);
            return null;
        }
        
        // 분할 불가능한 경우: 연속된 슬롯만 찾기
        List<TimeSlot> selectedSlots;
        if (!splittable) {
            selectedSlots = findContinuousSlotsInternal(freeSlots, usedSlots, requiredSlots, dueAt, task);
        } else {
            // 분할 가능한 경우: 여러 그룹으로 나눠서 찾기
            selectedSlots = findSplitSlots(freeSlots, usedSlots, requiredSlots, dueAt, task);
        }
        
        if (selectedSlots == null || selectedSlots.isEmpty()) {
            log.warn("작업 배치 실패: 적절한 슬롯을 찾지 못함. taskId={}, title={}, requiredSlots={}, splittable={}", 
                task.getId(), task.getTitle(), requiredSlots, splittable);
            return null;
        }
        
        // 분할 가능한 작업의 경우, 부분 배치 감지
        // 실제로 마감일 전에 충분한 슬롯이 있는지 확인
        if (splittable && partialAssignments != null && selectedSlots.size() < requiredSlots) {
            int assignedMinutes = selectedSlots.size() * 30;
            int requiredMinutes = task.getDurationMin();
            if (assignedMinutes < requiredMinutes) {
                // 마감일 전에 사용 가능한 슬롯 수 확인
                // findConsecutiveSlots에서 이미 마감일 필터링이 완료되었으므로,
                // selectedSlots.size() < requiredSlots인 경우는 실제로 마감일 전에 슬롯이 부족한 경우
                PartialAssignmentInfo info = new PartialAssignmentInfo();
                info.taskId = task.getId();
                info.requiredMinutes = requiredMinutes;
                info.assignedMinutes = assignedMinutes;
                // 마감일이 있고, 마감일 전에 충분한 시간이 있는지 확인
                if (dueAt != null) {
                    long hoursUntilDeadline = java.time.Duration.between(OffsetDateTime.now(java.time.ZoneOffset.UTC), dueAt).toHours();
                    if (hoursUntilDeadline > 24) {
                        // 마감일까지 충분한 시간이 있는 경우, 다른 이유로 부분 배치됨
                        info.reason = "사용 가능한 시간 슬롯 부족으로 부분 배치됨";
                    } else {
                        info.reason = "마감일까지 가능한 시간이 부족하여 부분 배치됨";
                    }
                } else {
                    info.reason = "사용 가능한 시간 슬롯 부족으로 부분 배치됨";
                }
                partialAssignments.put(task.getId(), info);
                log.warn("부분 배치 감지: taskId={}, assigned={}분 / required={}분, reason={}", 
                    task.getId(), assignedMinutes, requiredMinutes, info.reason);
            }
        }
        
        // 선택된 슬롯을 시간 순서로 정렬 (안전장치 - 역순 방지)
        selectedSlots = selectedSlots.stream()
            .sorted(Comparator
                .comparing(TimeSlot::getDate)
                .thenComparing(TimeSlot::getSlotIndex))
            .collect(Collectors.toList());
        
        // 선택된 슬롯 로깅
        TimeSlot firstSlot = selectedSlots.get(0);
        TimeSlot lastSlot = selectedSlots.get(selectedSlots.size() - 1);
        // 로그용 종료 시간 계산: 슬롯 수 기반 (실제 Assignment 생성과 동일한 방식)
        OffsetDateTime calculatedEndTime = firstSlot.getStartTime().plusMinutes(selectedSlots.size() * 30L);
        log.info("작업 배치 성공: taskId={}, title={}, selectedSlots={}, startsAt={}, endsAt={} (계산됨: {}분)", 
            task.getId(), task.getTitle(), selectedSlots.size(),
            firstSlot.getStartTime(),
            calculatedEndTime,
            selectedSlots.size() * 30);
        
        // startsAt > endsAt 체크 (안전장치)
        if (firstSlot.getStartTime().isAfter(lastSlot.getEndTime()) || 
            firstSlot.getStartTime().equals(lastSlot.getEndTime())) {
            log.error("선택된 슬롯 오류: startsAt >= endsAt. taskId={}, firstSlot={}, lastSlot={}, selectedSlots={}", 
                task.getId(), firstSlot.getStartTime(), lastSlot.getEndTime(), 
                selectedSlots.stream().map(s -> s.getStartTime().toString()).collect(Collectors.joining(", ")));
            return null; // 잘못된 슬롯이면 null 반환
        }
        
        // 선택된 슬롯의 상세 정보 로깅
        if (log.isDebugEnabled()) {
            for (int i = 0; i < selectedSlots.size(); i++) {
                TimeSlot slot = selectedSlots.get(i);
                log.debug("선택된 슬롯 [{}]: date={}, slotIndex={}, startTime={}, endTime={}, preferenceScore={}", 
                    i + 1, slot.getDate(), slot.getSlotIndex(), slot.getStartTime(), slot.getEndTime(), 
                    slot.getPreferenceScore());
            }
        }
        
        // 중요: findContinuousSlotsInternal과 findSplitSlots에서 이미 usedSlots에 추가했으므로
        // 여기서는 중복 추가하지 않음 (중복 추가 시 에러 발생)
        // 단, 안전장치로 확인만 수행
        for (TimeSlot slot : selectedSlots) {
            String slotKey = slot.getSlotKey();
            if (!usedSlots.contains(slotKey)) {
                log.warn("경고: 슬롯이 usedSlots에 등록되지 않음! taskId={}, slotKey={}, slot={}", 
                    task.getId(), slotKey, slot.getStartTime());
                // 안전장치: 등록되지 않았으면 여기서 추가
                usedSlots.add(slotKey);
            }
        }
        log.debug("슬롯 사용 확인 완료: taskId={}, slotCount={}, slotKeys={}", 
            task.getId(), selectedSlots.size(),
            selectedSlots.stream().map(TimeSlot::getSlotKey).collect(Collectors.joining(", ")));
        
        // Assignment 생성
        List<Assignment> assignments = createAssignments(task, selectedSlots, schedule);
        log.info("Assignment 생성 완료: taskId={}, assignmentCount={}", task.getId(), assignments.size());
    
        return assignments;
    }

    /**
     * 연속된 슬롯 찾기 (분할 불가능) - 내부 메서드
     * 마감일과 선호도를 고려하여 슬롯 선택
     * 
     * @param freeSlots 사용 가능한 슬롯 목록 (이미 usedSlots에서 필터링됨)
     * @param usedSlots 이미 사용된 슬롯 목록 (슬롯 선택 시 다시 확인하여 중복 방지)
     * @param requiredSlots 필요한 슬롯 수
     * @param dueAt 작업 마감일
     * @param task 작업 엔티티
     */
    private List<TimeSlot> findContinuousSlotsInternal(List<TimeSlot> freeSlots, Set<String> usedSlots, int requiredSlots, OffsetDateTime dueAt, Task task) {
        if (freeSlots.size() < requiredSlots) {
            return null;
        }
        
        // 선호도 기반 필터링은 이미 findConsecutiveSlots에서 완료됨
        // 마감일이 24시간 이하로 임박한 작업은 preferenceScore 무시하고 가장 빠른 시간대 강제 배치
        boolean isUrgent = false;
        if (dueAt != null) {
            long hoursUntilDeadline = java.time.Duration.between(OffsetDateTime.now(), dueAt).toHours();
            isUrgent = hoursUntilDeadline <= 24;
        }
        
        if (freeSlots.isEmpty()) {
            log.warn("사용 가능한 슬롯이 없습니다. requiredSlots={}, isUrgent={}", 
                requiredSlots, isUrgent);
            return null;
        }
        
        // 마감일이 있으면 무조건 오늘부터 가장 빠른 시간대부터 배치
        if (dueAt != null) {
            if (isUrgent) {
                // 24시간 이하 임박: preferenceScore 완전 무시, 날짜와 시간만 고려
                freeSlots = freeSlots.stream()
                    .sorted(Comparator
                        .comparing(TimeSlot::getDate)  // 날짜 우선 (오늘부터)
                        .thenComparing(TimeSlot::getSlotIndex))  // 같은 날이면 빠른 시간대
                    .collect(Collectors.toList());
            } else {
                // 24시간 이상 여유: 날짜 → 선호도 → 시간 (선호도 우선)
                freeSlots = freeSlots.stream()
                    .sorted(Comparator
                        .comparing(TimeSlot::getDate)  // 날짜 우선 (오늘부터)
                        .thenComparingDouble(TimeSlot::getPreferenceScore).reversed()  // 선호도 높은 순
                        .thenComparing(TimeSlot::getSlotIndex))  // 같은 선호도면 빠른 시간대
                    .collect(Collectors.toList());
            }
        } else {
            // 마감일이 없으면 날짜는 빠른 순, 같은 날이면 선호도 높은 순
            freeSlots = freeSlots.stream()
                .sorted(Comparator
                    .comparing(TimeSlot::getDate)  // 날짜는 빠른 순
                    .thenComparingDouble(TimeSlot::getPreferenceScore).reversed())  // 선호도 높은 순
                .collect(Collectors.toList());
        }
        
        for (int i = 0; i <= freeSlots.size() - requiredSlots; i++) {
            List<TimeSlot> candidate = new ArrayList<>();
            TimeSlot first = freeSlots.get(i);
            
            // 첫 번째 슬롯이 이미 사용되었는지 확인 (중복 방지)
            if (usedSlots.contains(first.getSlotKey())) {
                continue;
            }
            
            candidate.add(first);
            
            for (int j = i + 1; j < freeSlots.size(); j++) {
                TimeSlot next = freeSlots.get(j);
                
                // 다음 슬롯이 이미 사용되었는지 확인 (중복 방지)
                if (usedSlots.contains(next.getSlotKey())) {
                    break; // 이미 사용된 슬롯이면 이 연속 구간은 불가능
                }
                
                if (first.isConsecutive(next)) {
                    candidate.add(next);
                    if (candidate.size() == requiredSlots) {
                        // 마감일 확인: 마지막 슬롯의 종료 시간이 마감일 이전이어야 함
                        // 긴급 작업은 마감일과 같은 시간까지 허용
                        if (dueAt != null) {
                            TimeSlot lastSlot = candidate.get(candidate.size() - 1);
                            boolean isValidDeadline = isUrgent 
                                ? !lastSlot.getEndTime().isAfter(dueAt) 
                                : lastSlot.getEndTime().isBefore(dueAt);
                            if (!isValidDeadline) {
                                // 마감일을 초과하므로 이 후보는 사용 불가
                                break;
                            }
                        }
                        // 중요: 슬롯을 선택한 직후 즉시 usedSlots에 추가하여 다른 작업이 같은 슬롯을 선택하지 않도록 방지
                        candidate.forEach(slot -> usedSlots.add(slot.getSlotKey()));
                        log.debug("연속 슬롯 선택 및 사용 등록: slotCount={}, slotKeys={}", 
                            candidate.size(),
                            candidate.stream().map(TimeSlot::getSlotKey).collect(Collectors.joining(", ")));
                        return candidate;
                    }
                    first = next;
                } else {
                    break;
                }
            }
        }
        
        return null;
    }

    /**
     * 분할된 슬롯 찾기 (분할 가능)
     * 마감일과 선호도를 고려하여 슬롯 선택
     * 연속 슬롯을 우선 찾고, 부족하면 분할하여 배치
     * 
     * 중요: 이미 사용된 슬롯을 제외하고 선택하여 겹침 방지
     */
    private List<TimeSlot> findSplitSlots(List<TimeSlot> freeSlots, Set<String> usedSlots, int requiredSlots, OffsetDateTime dueAt, Task task) {
        // freeSlots는 항상 "날짜 + slotIndex" 기준으로 정렬하기
        freeSlots = freeSlots.stream()
            .sorted(Comparator
                .comparing(TimeSlot::getDate)
                .thenComparing(TimeSlot::getSlotIndex))
            .collect(Collectors.toList());
        
        // 이미 사용된 슬롯을 제외 (중복 방지)
        // findConsecutiveSlots에서 이미 필터링했지만, 다른 작업이 중간에 슬롯을 사용했을 수 있으므로 다시 확인
        List<TimeSlot> availableSlots = freeSlots.stream()
            .filter(slot -> !usedSlots.contains(slot.getSlotKey()))
            .collect(Collectors.toList());
        
        if (availableSlots.size() < requiredSlots) {
            log.warn("사용 가능한 슬롯이 부족합니다: requiredSlots={}, availableSlots={}", 
                requiredSlots, availableSlots.size());
            return null;
        }
        
        // 선호도 기반 필터링은 이미 findConsecutiveSlots에서 완료됨
        // 먼저 연속 슬롯을 찾아보기
        List<TimeSlot> consecutive = findContinuousSlotsInternal(availableSlots, usedSlots, requiredSlots, dueAt, task);
        if (consecutive != null && consecutive.size() == requiredSlots) {
            return consecutive;
        }
        
        // 연속 슬롯을 찾지 못하면 분할 배치
        // 시간 순서대로 필요한 만큼 가져오기 (이미 시간 순서로 정렬되어 있고, usedSlots 제외됨)
        // 중요: 한 번에 하나씩 선택하면서 usedSlots에 즉시 추가하여 중복 방지
        // 마감일 전에 충분한 슬롯이 있으면 모두 배치하도록 수정
        List<TimeSlot> selectedSlots = new ArrayList<>();
        for (TimeSlot slot : availableSlots) {
            // 이미 사용된 슬롯이면 건너뛰기 (이중 체크)
            if (usedSlots.contains(slot.getSlotKey())) {
                continue;
            }
            
            // 마감일 확인: 슬롯의 종료 시간이 마감일 이전이거나 같아야 함
            if (dueAt != null) {
                // 긴급 작업은 마감일과 같은 시간까지 허용, 일반 작업도 마감일과 같은 시간까지 허용 (isBefore -> !isAfter)
                boolean isValidDeadline = !slot.getEndTime().isAfter(dueAt);
                if (!isValidDeadline) {
                    // 마감일을 초과하는 슬롯은 건너뛰기
                    continue;
                }
            }
            
            // 슬롯을 선택하면 즉시 usedSlots에 추가하여 동시성 문제 방지
            selectedSlots.add(slot);
            usedSlots.add(slot.getSlotKey());
            
            if (selectedSlots.size() >= requiredSlots) {
                break;
            }
        }
        
        // 마감일 전에 충분한 슬롯이 있으면 모두 배치
        if (selectedSlots.size() < requiredSlots) {
            // 마감일 전에 사용 가능한 슬롯이 부족한 경우에만 경고
            log.warn("분할 슬롯 선택: 필요한 슬롯 수={}, 선택된 슬롯 수={}, 마감일 전 사용 가능한 슬롯 부족", 
                requiredSlots, selectedSlots.size());
            // 부분 배치는 허용하므로 null 반환하지 않고 선택된 슬롯 반환
            if (selectedSlots.isEmpty()) {
                return null;
            }
        }
        
        // startsAt > endsAt 체크 (안전장치)
        TimeSlot firstSlot = selectedSlots.get(0);
        TimeSlot lastSlot = selectedSlots.get(selectedSlots.size() - 1);
        if (firstSlot.getStartTime().isAfter(lastSlot.getEndTime()) || 
            firstSlot.getStartTime().equals(lastSlot.getEndTime())) {
            log.error("분할 슬롯 선택 오류: startsAt >= endsAt. taskId={}, firstSlot={}, lastSlot={}, selectedSlots={}", 
                task.getId(), firstSlot.getStartTime(), lastSlot.getEndTime(), 
                selectedSlots.stream().map(s -> s.getStartTime().toString()).collect(Collectors.joining(", ")));
            return null; // 잘못된 슬롯이면 null 반환
        }
        
        log.debug("분할 슬롯 찾기 성공: {}개 슬롯 선택, startTime={}, endTime={}", 
            selectedSlots.size(), firstSlot.getStartTime(), lastSlot.getEndTime());
        
        return selectedSlots;
    }

    /**
     * Assignment 생성 (분할된 경우 여러 개 반환)
     */
    private List<Assignment> createAssignments(Task task, List<TimeSlot> slots, Schedule schedule) {
        if (slots.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 슬롯을 시간 순서로 정렬 (안전장치)
        List<TimeSlot> sortedSlots = slots.stream()
            .sorted(Comparator
                .comparing(TimeSlot::getDate)
                .thenComparing(TimeSlot::getSlotIndex))
            .collect(Collectors.toList());
        
        // 연속 슬롯인지 확인
        boolean isConsecutive = isConsecutive(sortedSlots);
        
        if (isConsecutive || !task.isSplittable()) {
            // 연속 슬롯이거나 분할 불가능한 경우 단일 Assignment
            TimeSlot firstSlot = sortedSlots.get(0);
            TimeSlot lastSlot = sortedSlots.get(sortedSlots.size() - 1);
            
            OffsetDateTime startsAt = firstSlot.getStartTime();
            // 슬롯 수를 기반으로 정확한 종료 시간 계산 (슬롯 수 × 30분)
            // lastSlot.getEndTime()은 마지막 슬롯의 종료 시간만 반환하므로,
            // 슬롯 수를 기반으로 계산하는 것이 더 정확함
            OffsetDateTime endsAt = startsAt.plusMinutes(sortedSlots.size() * 30L);
            
            // startsAt이 endsAt보다 나중인지 확인 (안전장치)
            if (startsAt.isAfter(endsAt) || startsAt.equals(endsAt)) {
                log.error("Assignment 생성 오류: startsAt >= endsAt. taskId={}, startsAt={}, endsAt={}, firstSlot={}, lastSlot={}, slotCount={}", 
                    task.getId(), startsAt, endsAt, firstSlot.getStartTime(), lastSlot.getEndTime(), sortedSlots.size());
                // 역순으로 교정
                OffsetDateTime temp = startsAt;
                startsAt = endsAt;
                endsAt = temp;
                log.warn("Assignment 시간 교정: taskId={}, 교정된 startsAt={}, endsAt={}", 
                    task.getId(), startsAt, endsAt);
            }
            
            // 디버깅: 계산된 시간과 lastSlot의 시간이 일치하는지 확인
            OffsetDateTime expectedEndTime = lastSlot.getEndTime();
            if (!endsAt.equals(expectedEndTime)) {
                log.warn("Assignment 종료 시간 불일치: taskId={}, 계산된 endsAt={}, lastSlot.getEndTime()={}, slotCount={}", 
                    task.getId(), endsAt, expectedEndTime, sortedSlots.size());
            }
            
            Assignment assignment = new Assignment();
            assignment.setSchedule(schedule);
            assignment.setTask(task);
            assignment.setTitle(task.getTitle());
            assignment.setStartsAt(startsAt);
            assignment.setEndsAt(endsAt);
            assignment.setSource(AssignmentSource.TASK);
            assignment.setSlotIndex(firstSlot.getSlotIndex());
            
            // 메타 정보 (JSON 형식으로 저장)
            String meta = String.format(
                "{\"slots\":%d,\"split\":false,\"userId\":%d,\"splitIndex\":0}",
                sortedSlots.size(),
                firstSlot.getUserId()
            );
            assignment.setMeta(meta);
            
            log.debug("Assignment 생성: taskId={}, startsAt={}, endsAt={}, duration={}분", 
                task.getId(), assignment.getStartsAt(), assignment.getEndsAt(),
                java.time.Duration.between(assignment.getStartsAt(), assignment.getEndsAt()).toMinutes());
            
            return List.of(assignment);
        } else {
            // 분할된 경우: 연속 그룹별로 Assignment 생성
            List<Assignment> assignments = new ArrayList<>();
            List<List<TimeSlot>> groups = groupConsecutiveSlots(sortedSlots);
            
            for (int i = 0; i < groups.size(); i++) {
                List<TimeSlot> group = groups.get(i);
                // 그룹 내에서도 정렬 (안전장치)
                group = group.stream()
                    .sorted(Comparator
                        .comparing(TimeSlot::getDate)
                        .thenComparing(TimeSlot::getSlotIndex))
                    .collect(Collectors.toList());
                
                TimeSlot firstSlot = group.get(0);
                TimeSlot lastSlot = group.get(group.size() - 1);
                
                OffsetDateTime startsAt = firstSlot.getStartTime();
                // 슬롯 수를 기반으로 정확한 종료 시간 계산 (그룹 내 슬롯 수 × 30분)
                OffsetDateTime endsAt = startsAt.plusMinutes(group.size() * 30L);
                
                // startsAt이 endsAt보다 나중인지 확인 (안전장치)
                if (startsAt.isAfter(endsAt) || startsAt.equals(endsAt)) {
                    log.error("Assignment 생성 오류 (분할): startsAt >= endsAt. taskId={}, group={}, startsAt={}, endsAt={}, firstSlot={}, lastSlot={}, slotCount={}", 
                        task.getId(), i, startsAt, endsAt, firstSlot.getStartTime(), lastSlot.getEndTime(), group.size());
                    // 역순으로 교정
                    OffsetDateTime temp = startsAt;
                    startsAt = endsAt;
                    endsAt = temp;
                    log.warn("Assignment 시간 교정 (분할): taskId={}, group={}, 교정된 startsAt={}, endsAt={}", 
                        task.getId(), i, startsAt, endsAt);
                }
                
                Assignment assignment = new Assignment();
                assignment.setSchedule(schedule);
                assignment.setTask(task);
                assignment.setTitle(task.getTitle() + " (부분 " + (i + 1) + ")");
                assignment.setStartsAt(startsAt);
                assignment.setEndsAt(endsAt);
                assignment.setSource(AssignmentSource.TASK);
                assignment.setSlotIndex(firstSlot.getSlotIndex());
                
                // 메타 정보
                String meta = String.format(
                    "{\"slots\":%d,\"split\":true,\"userId\":%d,\"splitIndex\":%d,\"totalGroups\":%d}",
                    group.size(),
                    firstSlot.getUserId(),
                    i,
                    groups.size()
                );
                assignment.setMeta(meta);
                
                log.debug("Assignment 생성 (분할): taskId={}, group={}, startsAt={}, endsAt={}, duration={}분", 
                    task.getId(), i + 1, assignment.getStartsAt(), assignment.getEndsAt(),
                    java.time.Duration.between(assignment.getStartsAt(), assignment.getEndsAt()).toMinutes());
                
                assignments.add(assignment);
            }
            
            return assignments;
        }
    }
    
    /**
     * 슬롯들이 겹치는지 확인
     */
    private boolean isOverlapping(TimeSlot a, TimeSlot b) {
        return !(a.getEndTime().isBefore(b.getStartTime()) ||
                 a.getStartTime().isAfter(b.getEndTime()));
    }
    
    /**
     * 슬롯들을 연속 그룹으로 분할
     * 슬롯들이 시간 순서로 정렬되어 있어야 함
     */
    private List<List<TimeSlot>> groupConsecutiveSlots(List<TimeSlot> slots) {
        if (slots.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 먼저 시간 순서로 정렬 (날짜 → 시간)
        List<TimeSlot> sortedSlots = slots.stream()
            .sorted(Comparator
                .comparing(TimeSlot::getDate)
                .thenComparing(TimeSlot::getSlotIndex))
            .collect(Collectors.toList());
        
        List<List<TimeSlot>> groups = new ArrayList<>();
        List<TimeSlot> currentGroup = new ArrayList<>();
        currentGroup.add(sortedSlots.get(0));
        
        for (int i = 1; i < sortedSlots.size(); i++) {
            TimeSlot prev = sortedSlots.get(i - 1);
            TimeSlot curr = sortedSlots.get(i);
            
            // 수정 3: createAssignments에서 겹치는지도 검사
            if (prev.isConsecutive(curr) || isOverlapping(prev, curr)) {
                currentGroup.add(curr);
            } else {
                groups.add(new ArrayList<>(currentGroup));
                currentGroup.clear();
                currentGroup.add(curr);
            }
        }
        
        if (!currentGroup.isEmpty()) {
            groups.add(currentGroup);
        }
        
        return groups;
    }

    /**
     * 슬롯들이 연속인지 확인
     */
    private boolean isConsecutive(List<TimeSlot> slots) {
        if (slots.size() <= 1) {
            return true;
        }
        
        for (int i = 0; i < slots.size() - 1; i++) {
            if (!slots.get(i).isConsecutive(slots.get(i + 1))) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 부분 배치 정보를 담는 내부 클래스
     */
    public static class PartialAssignmentInfo {
        public Long taskId;
        public Integer requiredMinutes;
        public Integer assignedMinutes;
        public String reason;
    }
}

