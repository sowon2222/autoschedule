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
        
        // 1. 작업 정렬: 마감일 오름차순 → 우선순위 내림차순
        List<Task> sortedTasks = tasks.stream()
            .sorted(Comparator
                .comparing((Task t) -> t.getDueAt() != null ? t.getDueAt() : OffsetDateTime.MAX)
                .thenComparing(Comparator.comparing(Task::getPriority).reversed()))
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
        
        for (Task task : sortedTasks) {
            Assignment assignment = tryAssignTask(task, sortedSlots, usedSlots, schedule);
            if (assignment != null) {
                assignments.add(assignment);
                log.debug("작업 배치 성공: taskId={}, title={}", task.getId(), task.getTitle());
            } else {
                log.warn("작업 배치 실패: taskId={}, title={}", task.getId(), task.getTitle());
            }
        }
        
        log.info("그리디 배치 완료: 성공={}, 실패={}", assignments.size(), sortedTasks.size() - assignments.size());
        
        return assignments;
    }

    /**
     * 단일 작업을 배치 시도
     */
    private Assignment tryAssignTask(
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
        
        // 사용자별로 배치 시도
        List<Long> candidateUserIds = targetUserId != null 
            ? List.of(targetUserId)
            : new ArrayList<>(availableSlots.keySet());
        
        for (Long userId : candidateUserIds) {
            List<TimeSlot> userSlots = availableSlots.get(userId);
            if (userSlots == null || userSlots.isEmpty()) {
                continue;
            }
            
            // 마감일을 고려하여 사용 가능한 연속 슬롯 찾기
            List<TimeSlot> consecutiveSlots = findConsecutiveSlots(
                userSlots, 
                usedSlots.getOrDefault(userId, new ArrayList<>()), 
                requiredSlots, 
                task.isSplittable(),
                dueAt);
            
            if (consecutiveSlots != null && !consecutiveSlots.isEmpty()) {
                // 배치 성공
                Assignment assignment = createAssignment(task, consecutiveSlots, schedule);
                
                // 사용된 슬롯 추적
                usedSlots.computeIfAbsent(userId, k -> new ArrayList<>()).addAll(consecutiveSlots);
                
                return assignment;
            }
        }
        
        return null; // 배치 실패
    }

    /**
     * 연속된 슬롯 찾기
     * 
     * @param availableSlots 사용 가능한 슬롯 목록
     * @param usedSlots 이미 사용된 슬롯 목록
     * @param requiredSlots 필요한 슬롯 수
     * @param splittable 분할 가능 여부
     * @param dueAt 작업 마감일 (null이면 제한 없음)
     * @return 연속된 슬롯 목록 (분할 가능하면 여러 그룹)
     */
    private List<TimeSlot> findConsecutiveSlots(
            List<TimeSlot> availableSlots,
            List<TimeSlot> usedSlots,
            int requiredSlots,
            boolean splittable,
            OffsetDateTime dueAt) {
        
        // 사용 가능한 슬롯에서 이미 사용된 슬롯 제외
        List<TimeSlot> freeSlots = availableSlots.stream()
            .filter(slot -> !usedSlots.contains(slot))
            .collect(Collectors.toList());
        
        if (freeSlots.isEmpty()) {
            return null;
        }
        
        // 마감일이 있으면 마감일 이전의 슬롯만 필터링
        if (dueAt != null) {
            freeSlots = freeSlots.stream()
                .filter(slot -> slot.getEndTime().isBefore(dueAt) || slot.getEndTime().equals(dueAt))
                .collect(Collectors.toList());
        }
        
        if (freeSlots.isEmpty()) {
            return null;
        }
        
        // 분할 불가능한 경우: 연속된 슬롯만 찾기
        if (!splittable) {
            return findContinuousSlots(freeSlots, requiredSlots, dueAt);
        }
        
        // 분할 가능한 경우: 여러 그룹으로 나눠서 찾기
        return findSplitSlots(freeSlots, requiredSlots, dueAt);
    }

    /**
     * 연속된 슬롯 찾기 (분할 불가능)
     * 마감일에 가까운 슬롯을 우선적으로 선택
     */
    private List<TimeSlot> findContinuousSlots(List<TimeSlot> freeSlots, int requiredSlots, OffsetDateTime dueAt) {
        if (freeSlots.size() < requiredSlots) {
            return null;
        }
        
        // 마감일이 있으면 마감일에 가까운 순서로 정렬 (마감일 직전부터 역순)
        if (dueAt != null) {
            freeSlots = freeSlots.stream()
                .sorted((s1, s2) -> {
                    // 마감일에 더 가까운 슬롯을 우선
                    long diff1 = Math.abs(java.time.Duration.between(s1.getEndTime(), dueAt).toMinutes());
                    long diff2 = Math.abs(java.time.Duration.between(s2.getEndTime(), dueAt).toMinutes());
                    return Long.compare(diff1, diff2);
                })
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
     * 마감일에 가까운 슬롯을 우선적으로 선택
     */
    private List<TimeSlot> findSplitSlots(List<TimeSlot> freeSlots, int requiredSlots, OffsetDateTime dueAt) {
        if (freeSlots.size() < requiredSlots) {
            return null;
        }
        
        // 마감일이 있으면 마감일에 가까운 순서로 정렬
        if (dueAt != null) {
            freeSlots = freeSlots.stream()
                .sorted((s1, s2) -> {
                    // 마감일에 더 가까운 슬롯을 우선
                    long diff1 = Math.abs(java.time.Duration.between(s1.getEndTime(), dueAt).toMinutes());
                    long diff2 = Math.abs(java.time.Duration.between(s2.getEndTime(), dueAt).toMinutes());
                    return Long.compare(diff1, diff2);
                })
                .collect(Collectors.toList());
        }
        
        // 마감일에 가까운 슬롯부터 필요한 만큼 가져오기
        return freeSlots.stream()
            .limit(requiredSlots)
            .collect(Collectors.toList());
    }

    /**
     * Assignment 생성
     */
    private Assignment createAssignment(Task task, List<TimeSlot> slots, Schedule schedule) {
        if (slots.isEmpty()) {
            return null;
        }
        
        TimeSlot firstSlot = slots.get(0);
        TimeSlot lastSlot = slots.get(slots.size() - 1);
        
        Assignment assignment = new Assignment();
        assignment.setSchedule(schedule);
        assignment.setTask(task);
        assignment.setTitle(task.getTitle());
        assignment.setStartsAt(firstSlot.getStartTime());
        assignment.setEndsAt(lastSlot.getEndTime());
        assignment.setSource(AssignmentSource.TASK);
        assignment.setSlotIndex(firstSlot.getSlotIndex());
        
        // 메타 정보 (JSON 형식으로 저장)
        // 예: {"slots": 3, "split": false, "userId": 1}
        String meta = String.format(
            "{\"slots\":%d,\"split\":%s,\"userId\":%d}",
            slots.size(),
            slots.size() > 1 && !isConsecutive(slots),
            firstSlot.getUserId()
        );
        assignment.setMeta(meta);
        
        return assignment;
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

