package com.example.sbb.service;

import com.example.sbb.domain.Assignment;
import com.example.sbb.domain.Task;
import com.example.sbb.domain.TimeSlot;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 점수 계산기
 * Hard/Soft 제약을 기반으로 스케줄의 품질을 평가합니다.
 */
@Slf4j
@Component
public class ScoreCalculator {

    private static final int BASE_SCORE = 1000;
    private static final int HARD_CONSTRAINT_VIOLATION = 0; // Hard 제약 위반 시 즉시 0점
    
    // Soft 제약 가중치
    private static final int DEADLINE_PENALTY_WEIGHT = 50;      // 마감일 임박도
    private static final int PRIORITY_PENALTY_WEIGHT = 30;     // 우선순위 반영도
    private static final int SPLIT_PENALTY_WEIGHT = 20;         // 작업 분할 횟수
    private static final int CONTINUITY_BONUS_WEIGHT = 10;     // 연속성 보너스

    /**
     * 스케줄의 점수를 계산합니다.
     * 
     * @param assignments 배치된 Assignment 목록
     * @param tasks 원본 Task 목록
     * @param availableSlots 사용 가능한 슬롯 맵 (Hard 제약 검증용)
     * @return 계산된 점수 (0 이상)
     */
    public int calculateScore(
            List<Assignment> assignments,
            List<Task> tasks,
            Map<Long, List<TimeSlot>> availableSlots) {
        
        // Hard 제약 검증
        if (!checkHardConstraints(assignments, tasks, availableSlots)) {
            log.warn("Hard 제약 위반으로 인해 점수 0점");
            return HARD_CONSTRAINT_VIOLATION;
        }
        
        // Soft 제약 기반 점수 계산
        int score = BASE_SCORE;
        
        // 1. 마감일 임박도 감점
        score -= calculateDeadlinePenalty(assignments, tasks);
        
        // 2. 우선순위 반영도 감점
        score -= calculatePriorityPenalty(assignments, tasks);
        
        // 3. 작업 분할 횟수 감점
        score -= calculateSplitPenalty(assignments, tasks);
        
        // 4. 연속성 보너스 가점
        score += calculateContinuityBonus(assignments, tasks);
        
        // 최소 점수는 0
        return Math.max(0, score);
    }

    /**
     * Hard 제약 검증
     * - 작업 마감일 준수 여부
     * - 근무시간 내 배치 여부
     * - 고정 이벤트와의 충돌 여부
     */
    private boolean checkHardConstraints(
            List<Assignment> assignments,
            List<Task> tasks,
            Map<Long, List<TimeSlot>> availableSlots) {
        
        // Task ID로 매핑
        Map<Long, Task> taskMap = tasks.stream()
            .collect(Collectors.toMap(Task::getId, task -> task));
        
        for (Assignment assignment : assignments) {
            if (assignment.getTask() == null) {
                continue; // Task가 아닌 Assignment는 건너뛰기
            }
            
            Task task = taskMap.get(assignment.getTask().getId());
            if (task == null) {
                continue;
            }
            
            // 1. 마감일 준수 여부
            if (task.getDueAt() != null) {
                if (assignment.getEndsAt().isAfter(task.getDueAt())) {
                    log.warn("Hard 제약 위반: 작업 {}이 마감일을 초과함", task.getId());
                    return false;
                }
            }
            
            // 2. 근무시간 내 배치 여부 (슬롯이 availableSlots에 있는지 확인)
            Long userId = extractUserIdFromMeta(assignment);
            if (userId != null) {
                List<TimeSlot> userSlots = availableSlots.get(userId);
                if (userSlots != null) {
                    boolean inAvailableSlot = userSlots.stream()
                        .anyMatch(slot -> slot.getStartTime().equals(assignment.getStartsAt()) &&
                                         slot.getEndTime().equals(assignment.getEndsAt()));
                    if (!inAvailableSlot) {
                        log.warn("Hard 제약 위반: 작업 {}이 사용 불가능한 슬롯에 배치됨", task.getId());
                        return false;
                    }
                }
            }
        }
        
        return true;
    }

    /**
     * 마감일 임박도 감점 계산
     * 마감일에 가까울수록 감점이 적고, 멀수록 감점이 큼
     */
    private int calculateDeadlinePenalty(List<Assignment> assignments, List<Task> tasks) {
        Map<Long, Task> taskMap = tasks.stream()
            .collect(Collectors.toMap(Task::getId, task -> task));
        
        int totalPenalty = 0;
        
        for (Assignment assignment : assignments) {
            if (assignment.getTask() == null) {
                continue;
            }
            
            Task task = taskMap.get(assignment.getTask().getId());
            if (task == null || task.getDueAt() == null) {
                continue;
            }
            
            OffsetDateTime assignmentEnd = assignment.getEndsAt();
            OffsetDateTime dueAt = task.getDueAt();
            
            // 마감일까지 남은 시간 (시간 단위)
            long hoursUntilDeadline = java.time.Duration.between(assignmentEnd, dueAt).toHours();
            
            // 마감일을 초과하지 않았지만, 여유가 적으면 감점
            if (hoursUntilDeadline >= 0 && hoursUntilDeadline < 24) {
                // 24시간 이내: 감점 없음
                continue;
            } else if (hoursUntilDeadline < 0) {
                // 마감일 초과 (이미 Hard 제약에서 걸러짐, 하지만 안전장치)
                totalPenalty += DEADLINE_PENALTY_WEIGHT * 10;
            } else {
                // 마감일까지 여유가 많으면 약간 감점 (너무 일찍 배치)
                long daysEarly = hoursUntilDeadline / 24;
                if (daysEarly > 7) {
                    totalPenalty += (int) (DEADLINE_PENALTY_WEIGHT * (daysEarly - 7) * 0.1);
                }
            }
        }
        
        return totalPenalty;
    }

    /**
     * 우선순위 반영도 감점 계산
     * 높은 우선순위 작업이 늦게 배치되면 감점
     */
    private int calculatePriorityPenalty(List<Assignment> assignments, List<Task> tasks) {
        Map<Long, Task> taskMap = tasks.stream()
            .collect(Collectors.toMap(Task::getId, task -> task));
        
        // 우선순위별로 그룹화 (1=높음, 5=낮음)
        Map<Integer, List<Assignment>> priorityGroups = assignments.stream()
            .filter(a -> a.getTask() != null && taskMap.containsKey(a.getTask().getId()))
            .collect(Collectors.groupingBy(a -> {
                Task task = taskMap.get(a.getTask().getId());
                return task.getPriority();
            }));
        
        int totalPenalty = 0;
        
        // 각 우선순위 그룹의 평균 배치 시간 계산
        for (Map.Entry<Integer, List<Assignment>> entry : priorityGroups.entrySet()) {
            int priority = entry.getKey();
            List<Assignment> groupAssignments = entry.getValue();
            
            if (groupAssignments.isEmpty()) {
                continue;
            }
            
            // 평균 배치 시간 (가장 빠른 시간 기준)
            OffsetDateTime avgStartTime = groupAssignments.stream()
                .map(Assignment::getStartsAt)
                .min(OffsetDateTime::compareTo)
                .orElse(OffsetDateTime.now());
            
            // 다른 우선순위 그룹과 비교
            for (Map.Entry<Integer, List<Assignment>> otherEntry : priorityGroups.entrySet()) {
                int otherPriority = otherEntry.getKey();
                if (otherPriority >= priority) {
                    continue; // 같은 우선순위 이상은 비교하지 않음
                }
                
                List<Assignment> otherGroup = otherEntry.getValue();
                OffsetDateTime otherAvgStartTime = otherGroup.stream()
                    .map(Assignment::getStartsAt)
                    .min(OffsetDateTime::compareTo)
                    .orElse(OffsetDateTime.now());
                
                // 높은 우선순위가 낮은 우선순위보다 늦게 배치되면 감점
                if (avgStartTime.isAfter(otherAvgStartTime)) {
                    long hoursDiff = java.time.Duration.between(otherAvgStartTime, avgStartTime).toHours();
                    totalPenalty += (int) (PRIORITY_PENALTY_WEIGHT * hoursDiff / 24.0);
                }
            }
        }
        
        return totalPenalty;
    }

    /**
     * 작업 분할 횟수 감점 계산
     * 작업이 여러 Assignment로 나뉘어지면 감점
     */
    private int calculateSplitPenalty(List<Assignment> assignments, List<Task> tasks) {
        Map<Long, Long> taskAssignmentCount = assignments.stream()
            .filter(a -> a.getTask() != null)
            .collect(Collectors.groupingBy(
                a -> a.getTask().getId(),
                Collectors.counting()
            ));
        
        int totalPenalty = 0;
        
        for (Map.Entry<Long, Long> entry : taskAssignmentCount.entrySet()) {
            long count = entry.getValue();
            if (count > 1) {
                // 분할 횟수에 비례하여 감점
                totalPenalty += (int) (SPLIT_PENALTY_WEIGHT * (count - 1));
            }
        }
        
        return totalPenalty;
    }

    /**
     * 연속성 보너스 계산
     * 같은 작업이 연속된 시간에 배치되면 가점
     */
    private int calculateContinuityBonus(List<Assignment> assignments, List<Task> tasks) {
        Map<Long, List<Assignment>> taskAssignments = assignments.stream()
            .filter(a -> a.getTask() != null)
            .collect(Collectors.groupingBy(a -> a.getTask().getId()));
        
        int totalBonus = 0;
        
        for (Map.Entry<Long, List<Assignment>> entry : taskAssignments.entrySet()) {
            List<Assignment> taskAssignmentList = entry.getValue();
            if (taskAssignmentList.size() <= 1) {
                continue; // 분할되지 않은 작업은 보너스 없음
            }
            
            // 시간순 정렬
            taskAssignmentList.sort((a1, a2) -> a1.getStartsAt().compareTo(a2.getStartsAt()));
            
            // 연속된 Assignment 그룹 찾기
            int consecutiveGroups = 1;
            for (int i = 1; i < taskAssignmentList.size(); i++) {
                Assignment prev = taskAssignmentList.get(i - 1);
                Assignment curr = taskAssignmentList.get(i);
                
                // 이전 Assignment 종료 시간과 현재 시작 시간이 연속인지 확인
                if (prev.getEndsAt().equals(curr.getStartsAt())) {
                    // 연속됨
                } else {
                    consecutiveGroups++;
                }
            }
            
            // 그룹 수가 적을수록 (연속성이 높을수록) 보너스
            if (consecutiveGroups < taskAssignmentList.size()) {
                totalBonus += CONTINUITY_BONUS_WEIGHT * (taskAssignmentList.size() - consecutiveGroups);
            }
        }
        
        return totalBonus;
    }

    /**
     * Assignment의 meta에서 userId 추출
     */
    private Long extractUserIdFromMeta(Assignment assignment) {
        if (assignment.getMeta() == null) {
            return null;
        }
        
        try {
            // 간단한 JSON 파싱 ({"slots":3,"split":false,"userId":1})
            String meta = assignment.getMeta();
            int userIdIndex = meta.indexOf("\"userId\":");
            if (userIdIndex == -1) {
                return null;
            }
            
            int start = userIdIndex + 9; // "userId": 길이
            int end = meta.indexOf("}", start);
            if (end == -1) {
                end = meta.length();
            }
            
            String userIdStr = meta.substring(start, end).trim().replace(",", "").replace("}", "");
            return Long.parseLong(userIdStr);
        } catch (Exception e) {
            log.warn("meta에서 userId 추출 실패: {}", assignment.getMeta(), e);
            return null;
        }
    }
}

