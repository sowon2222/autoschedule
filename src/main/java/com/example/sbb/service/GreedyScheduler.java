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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
     * 그리디 알고리즘으로 작업을 배치합니다.
     * 
     * @param tasks 배치할 작업 목록
     * @param availableSlots 사용자별 사용 가능한 슬롯 맵
     * @param schedule 스케줄 엔티티
     * @return 생성된 Assignment 목록
     */
    @Transactional
    public List<Assignment> scheduleTasks(
            List<Task> tasks,
            Map<Long, List<TimeSlot>> availableSlots,
            Schedule schedule) {
        
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
        Map<Long, List<TimeSlot>> usedSlots = new LinkedHashMap<>(); // 사용된 슬롯 추적
        int successCount = 0;
        int failCount = 0;
        
        for (Task task : sortedTasks) {
            List<Assignment> taskAssignments = tryAssignTask(task, sortedSlots, usedSlots, schedule);
            if (taskAssignments != null && !taskAssignments.isEmpty()) {
                assignments.addAll(taskAssignments);
                successCount++;
                log.debug("작업 배치 성공: taskId={}, title={}, assignments={}", 
                    task.getId(), task.getTitle(), taskAssignments.size());
            } else {
                failCount++;
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
            Map<Long, List<TimeSlot>> usedSlots,
            Schedule schedule) {
        
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
                usedSlots.getOrDefault(userId, new ArrayList<>()).size());
            
            // 마감일을 고려하여 사용 가능한 연속 슬롯 찾기
            // findConsecutiveSlots 내부에서 usedSlots를 업데이트하므로 별도 처리 불필요
            List<Assignment> assignments = findConsecutiveSlots(
                userSlots, 
                usedSlots.computeIfAbsent(userId, k -> new ArrayList<>()), 
                requiredSlots, 
                task.isSplittable(),
                dueAt,
                task,
                schedule);
            
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
     * @return 생성된 Assignment 목록
     */
    private List<Assignment> findConsecutiveSlots(
            List<TimeSlot> availableSlots,
            List<TimeSlot> usedSlots,
            int requiredSlots,
            boolean splittable,
            OffsetDateTime dueAt,
            Task task,
            Schedule schedule) {
        
        // 사용 가능한 슬롯에서 이미 사용된 슬롯 제외
        // 선호도 기반 필터링: 새벽 시간(0.05)만 제외, 나머지는 선호도 점수로 정렬
        List<TimeSlot> freeSlots = availableSlots.stream()
            .filter(slot -> !usedSlots.contains(slot))
            .filter(slot -> {
                double score = slot.getPreferenceScore();
                // 새벽 시간(0.05)만 완전히 제외 (거의 hard 제약)
                // 나머지는 선호도 점수로 정렬하여 배치
                boolean isValid = score > 0.05;
                if (!isValid) {
                    log.debug("슬롯 필터링됨 (새벽 시간): taskId={}, preferenceScore={}, slot={}", 
                        task.getId(), score, slot.getStartTime());
                }
                return isValid;
            })
            .collect(Collectors.toList());
        
        log.info("작업 배치 시도: taskId={}, title={}, requiredSlots={}, 사용 가능한 슬롯 수={}", 
            task.getId(), task.getTitle(), requiredSlots, freeSlots.size());
        
        if (freeSlots.isEmpty()) {
            log.warn("작업 배치 실패: 사용 가능한 슬롯이 없음. taskId={}, title={}, requiredSlots={}", 
                task.getId(), task.getTitle(), requiredSlots);
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
                // 분할 가능: 슬롯의 시작 시간이 마감일 이전이면 OK (일부라도 배치 가능)
                freeSlots = freeSlots.stream()
                    .filter(slot -> slot.getStartTime().isBefore(dueAt))
                    .collect(Collectors.toList());
            } else {
                // 분할 불가능: 슬롯의 시작 시간 + requiredSlots * 30분이 마감일 이전이어야 함
                freeSlots = freeSlots.stream()
                    .filter(slot -> {
                        OffsetDateTime taskEndTime = slot.getStartTime().plusMinutes(requiredSlots * 30L);
                        boolean isValid = taskEndTime.isBefore(dueAt);
                        if (!isValid) {
                            log.debug("마감일 필터링 (분할 불가): taskId={}, slotStart={}, taskEndTime={}, dueAt={}, requiredSlots={}", 
                                task.getId(), slot.getStartTime(), taskEndTime, dueAt, requiredSlots);
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
            selectedSlots = findContinuousSlotsInternal(freeSlots, requiredSlots, dueAt, task);
        } else {
            // 분할 가능한 경우: 여러 그룹으로 나눠서 찾기
            selectedSlots = findSplitSlots(freeSlots, requiredSlots, dueAt, task);
        }
        
        if (selectedSlots == null || selectedSlots.isEmpty()) {
            log.warn("작업 배치 실패: 적절한 슬롯을 찾지 못함. taskId={}, title={}, requiredSlots={}, splittable={}", 
                task.getId(), task.getTitle(), requiredSlots, splittable);
            return null;
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
        log.info("작업 배치 성공: taskId={}, title={}, selectedSlots={}, startsAt={}, endsAt={}", 
            task.getId(), task.getTitle(), selectedSlots.size(),
            firstSlot.getStartTime(),
            lastSlot.getEndTime());
        
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
        
        // Assignment 생성 및 사용된 슬롯 추적
        List<Assignment> assignments = createAssignments(task, selectedSlots, schedule);
        usedSlots.addAll(selectedSlots);
        
        log.info("Assignment 생성 완료: taskId={}, assignmentCount={}", task.getId(), assignments.size());
        
        return assignments;
    }

    /**
     * 연속된 슬롯 찾기 (분할 불가능) - 내부 메서드
     * 마감일과 선호도를 고려하여 슬롯 선택
     */
    private List<TimeSlot> findContinuousSlotsInternal(List<TimeSlot> freeSlots, int requiredSlots, OffsetDateTime dueAt, Task task) {
        if (freeSlots.size() < requiredSlots) {
            return null;
        }
        
        // 선호도 기반 필터링: 새벽 시간(0.05)만 제외, 나머지는 선호도 점수로 정렬
        // 이미 findConsecutiveSlots에서 필터링되었으므로 여기서는 추가 필터링 불필요
        // 하지만 안전장치로 새벽 시간만 한 번 더 체크
        int beforeFilterCount = freeSlots.size();
        freeSlots = freeSlots.stream()
            .filter(slot -> slot.getPreferenceScore() > 0.05)
            .collect(Collectors.toList());
        
        log.debug("연속 슬롯 찾기: 필터링 전={}, 필터링 후={}, requiredSlots={}", 
            beforeFilterCount, freeSlots.size(), requiredSlots);
        
        if (freeSlots.isEmpty()) {
            log.warn("사용 가능한 슬롯이 없습니다. requiredSlots={}, 필터링 전 슬롯 수={}", 
                requiredSlots, beforeFilterCount);
            return null;
        }
        
        // 마감일이 24시간 이하로 임박한 작업은 preferenceScore 무시하고 가장 빠른 시간대 강제 배치
        boolean isUrgent = false;
        if (dueAt != null) {
            long hoursUntilDeadline = java.time.Duration.between(OffsetDateTime.now(), dueAt).toHours();
            isUrgent = hoursUntilDeadline <= 24;
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
            candidate.add(first);
            
            for (int j = i + 1; j < freeSlots.size(); j++) {
                TimeSlot next = freeSlots.get(j);
                if (first.isConsecutive(next)) {
                    candidate.add(next);
                    if (candidate.size() == requiredSlots) {
                        // 마감일 확인: 마지막 슬롯의 종료 시간이 마감일 이전이어야 함
                        if (dueAt != null) {
                            TimeSlot lastSlot = candidate.get(candidate.size() - 1);
                            // 마감일시 이전에 완료되어야 함 (equals 제외)
                            if (!lastSlot.getEndTime().isBefore(dueAt)) {
                                // 마감일을 초과하거나 같으므로 이 후보는 사용 불가
                                break;
                            }
                        }
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
     */
    private List<TimeSlot> findSplitSlots(List<TimeSlot> freeSlots, int requiredSlots, OffsetDateTime dueAt, Task task) {
        if (freeSlots.size() < requiredSlots) {
            return null;
        }
        
        // 선호도 기반 필터링: 새벽 시간(0.05)만 제외, 나머지는 선호도 점수로 정렬
        // 이미 findConsecutiveSlots에서 필터링되었으므로 여기서는 추가 필터링 불필요
        // 하지만 안전장치로 새벽 시간만 한 번 더 체크
        int beforeFilterCount = freeSlots.size();
        freeSlots = freeSlots.stream()
            .filter(slot -> slot.getPreferenceScore() > 0.05)
            .collect(Collectors.toList());
        
        log.debug("분할 슬롯 찾기: 필터링 전={}, 필터링 후={}, requiredSlots={}", 
            beforeFilterCount, freeSlots.size(), requiredSlots);
        
        if (freeSlots.size() < requiredSlots) {
            log.warn("사용 가능한 슬롯이 부족합니다. requiredSlots={}, availableSlots={}, 필터링 전={}", 
                requiredSlots, freeSlots.size(), beforeFilterCount);
            return null;
        }
        
        // 먼저 연속 슬롯을 찾아보기
        List<TimeSlot> consecutive = findContinuousSlotsInternal(freeSlots, requiredSlots, dueAt, task);
        if (consecutive != null && consecutive.size() == requiredSlots) {
            return consecutive;
        }
        
        // 연속 슬롯을 찾지 못하면 분할 배치
        // 마감일이 24시간 이하로 임박한 작업은 preferenceScore 무시하고 가장 빠른 시간대 강제 배치
        boolean isUrgent = false;
        if (dueAt != null) {
            long hoursUntilDeadline = java.time.Duration.between(OffsetDateTime.now(), dueAt).toHours();
            isUrgent = hoursUntilDeadline <= 24;
        }
        
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
        
        // 선호도가 높은 슬롯부터 필요한 만큼 가져오기
        List<TimeSlot> selectedSlots = freeSlots.stream()
            .limit(requiredSlots)
            .collect(Collectors.toList());
        
        // 시간 순서로 정렬 (날짜 → 시간)
        selectedSlots = selectedSlots.stream()
            .sorted(Comparator
                .comparing(TimeSlot::getDate)
                .thenComparing(TimeSlot::getSlotIndex))
            .collect(Collectors.toList());
        
        // 선택된 슬롯을 시간 순서로 정렬 (안전장치 - 역순 방지)
        selectedSlots = selectedSlots.stream()
            .sorted(Comparator
                .comparing(TimeSlot::getDate)
                .thenComparing(TimeSlot::getSlotIndex))
            .collect(Collectors.toList());
        
        // 마감일 확인: 마지막 슬롯의 종료 시간이 마감일 이전이어야 함
        if (dueAt != null && !selectedSlots.isEmpty()) {
            TimeSlot lastSlot = selectedSlots.get(selectedSlots.size() - 1);
            // 마감일시 이전에 완료되어야 함 (equals 제외)
            if (!lastSlot.getEndTime().isBefore(dueAt)) {
                // 마감일을 초과하거나 같으므로 null 반환
                log.warn("분할 슬롯 찾기 실패: 마감일 초과. lastSlotEnd={}, dueAt={}", 
                    lastSlot.getEndTime(), dueAt);
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
            OffsetDateTime endsAt = lastSlot.getEndTime();
            
            // startsAt이 endsAt보다 나중인지 확인 (안전장치)
            if (startsAt.isAfter(endsAt) || startsAt.equals(endsAt)) {
                log.error("Assignment 생성 오류: startsAt >= endsAt. taskId={}, startsAt={}, endsAt={}, firstSlot={}, lastSlot={}", 
                    task.getId(), startsAt, endsAt, firstSlot.getStartTime(), lastSlot.getEndTime());
                // 역순으로 교정
                OffsetDateTime temp = startsAt;
                startsAt = endsAt;
                endsAt = temp;
                log.warn("Assignment 시간 교정: taskId={}, 교정된 startsAt={}, endsAt={}", 
                    task.getId(), startsAt, endsAt);
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
                OffsetDateTime endsAt = lastSlot.getEndTime();
                
                // startsAt이 endsAt보다 나중인지 확인 (안전장치)
                if (startsAt.isAfter(endsAt) || startsAt.equals(endsAt)) {
                    log.error("Assignment 생성 오류 (분할): startsAt >= endsAt. taskId={}, group={}, startsAt={}, endsAt={}, firstSlot={}, lastSlot={}", 
                        task.getId(), i, startsAt, endsAt, firstSlot.getStartTime(), lastSlot.getEndTime());
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
            
            if (prev.isConsecutive(curr)) {
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
}

