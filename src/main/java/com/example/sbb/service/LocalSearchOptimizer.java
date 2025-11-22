package com.example.sbb.service;

import com.example.sbb.domain.Assignment;
import com.example.sbb.domain.Schedule;
import com.example.sbb.domain.Task;
import com.example.sbb.domain.TimeSlot;
import com.example.sbb.repository.AssignmentRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로컬서치 최적화기
 * 그리디 알고리즘의 결과를 swap/move 연산으로 개선합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalSearchOptimizer {

    private final ScoreCalculator scoreCalculator;
    private final AssignmentRepository assignmentRepository;
    private final Random random = new Random();

    private static final int MAX_ITERATIONS = 100;        // 최대 반복 횟수
    private static final int MAX_NO_IMPROVEMENT = 20;     // 개선 없이 연속된 반복 횟수
    private static final double TEMPERATURE_INITIAL = 100.0; // 시뮬레이티드 어닐링 초기 온도
    private static final double TEMPERATURE_COOLING = 0.95;  // 냉각률

    /**
     * 로컬서치로 스케줄을 최적화합니다.
     * 
     * @param schedule 스케줄
     * @param assignments 현재 배치된 Assignment 목록
     * @param tasks 원본 Task 목록
     * @param availableSlots 사용 가능한 슬롯 맵
     * @return 최적화된 Assignment 목록
     */
    @Transactional
    public List<Assignment> optimize(
            Schedule schedule,
            List<Assignment> assignments,
            List<Task> tasks,
            Map<Long, List<TimeSlot>> availableSlots) {
        
        log.info("로컬서치 최적화 시작: 초기 Assignment 수={}", assignments.size());
        
        // 현재 점수 계산
        int currentScore = scoreCalculator.calculateScore(assignments, tasks, availableSlots);
        log.info("초기 점수: {}", currentScore);
        
        List<Assignment> bestAssignments = new ArrayList<>(assignments);
        int bestScore = currentScore;
        
        int noImprovementCount = 0;
        double temperature = TEMPERATURE_INITIAL;
        
        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            // Swap 또는 Move 연산 선택
            boolean useSwap = random.nextBoolean();
            
            List<Assignment> candidateAssignments = useSwap
                ? trySwap(bestAssignments, availableSlots)
                : tryMove(bestAssignments, availableSlots);
            
            if (candidateAssignments == null) {
                noImprovementCount++;
                if (noImprovementCount >= MAX_NO_IMPROVEMENT) {
                    log.info("개선 없음으로 인한 조기 종료: iteration={}", iteration);
                    break;
                }
                continue;
            }
            
            // 점수 계산
            int candidateScore = scoreCalculator.calculateScore(candidateAssignments, tasks, availableSlots);
            
            // 개선 여부 확인
            boolean accept = false;
            if (candidateScore > currentScore) {
                // 개선됨
                accept = true;
            } else {
                // 시뮬레이티드 어닐링: 나쁜 해도 확률적으로 수용
                double probability = Math.exp((candidateScore - currentScore) / temperature);
                if (random.nextDouble() < probability) {
                    accept = true;
                }
            }
            
            if (accept) {
                bestAssignments = candidateAssignments;
                currentScore = candidateScore;
                noImprovementCount = 0;
                
                if (currentScore > bestScore) {
                    bestScore = currentScore;
                    log.debug("최적 해 업데이트: iteration={}, score={}", iteration, bestScore);
                }
            } else {
                noImprovementCount++;
            }
            
            // 온도 냉각
            temperature *= TEMPERATURE_COOLING;
            
            if (noImprovementCount >= MAX_NO_IMPROVEMENT) {
                log.info("개선 없음으로 인한 조기 종료: iteration={}", iteration);
                break;
            }
        }
        
        log.info("로컬서치 최적화 완료: 최종 점수={} (초기: {})", bestScore, scoreCalculator.calculateScore(assignments, tasks, availableSlots));
        
        // 최적화된 Assignment 저장
        if (bestScore > scoreCalculator.calculateScore(assignments, tasks, availableSlots)) {
            // 기존 Assignment 삭제
            assignmentRepository.deleteAll(assignments);
            // 새로운 Assignment 저장
            bestAssignments.forEach(a -> a.setSchedule(schedule));
            return assignmentRepository.saveAll(bestAssignments);
        }
        
        return assignments; // 개선되지 않았으면 원본 반환
    }

    /**
     * Swap 연산: 두 Assignment의 시간대를 교환
     */
    private List<Assignment> trySwap(List<Assignment> assignments, Map<Long, List<TimeSlot>> availableSlots) {
        if (assignments.size() < 2) {
            return null;
        }
        
        // Task Assignment만 필터링
        List<Assignment> taskAssignments = assignments.stream()
            .filter(a -> a.getTask() != null)
            .collect(java.util.stream.Collectors.toList());
        
        if (taskAssignments.size() < 2) {
            return null;
        }
        
        // 랜덤하게 두 Assignment 선택
        int idx1 = random.nextInt(taskAssignments.size());
        int idx2 = random.nextInt(taskAssignments.size());
        while (idx1 == idx2) {
            idx2 = random.nextInt(taskAssignments.size());
        }
        
        Assignment a1 = taskAssignments.get(idx1);
        Assignment a2 = taskAssignments.get(idx2);
        
        // 시간대 교환
        OffsetDateTime tempStart = a1.getStartsAt();
        OffsetDateTime tempEnd = a1.getEndsAt();
        Integer tempSlotIndex = a1.getSlotIndex();
        
        // 새로운 Assignment 생성 (깊은 복사)
        List<Assignment> newAssignments = new ArrayList<>();
        for (Assignment a : assignments) {
            Assignment newA = cloneAssignment(a);
            if (a == a1) {
                newA.setStartsAt(a2.getStartsAt());
                newA.setEndsAt(a2.getEndsAt());
                newA.setSlotIndex(a2.getSlotIndex());
            } else if (a == a2) {
                newA.setStartsAt(tempStart);
                newA.setEndsAt(tempEnd);
                newA.setSlotIndex(tempSlotIndex);
            }
            newAssignments.add(newA);
        }
        
        // 제약 조건 검증
        if (isValidSwap(newAssignments, a1, a2, availableSlots)) {
            return newAssignments;
        }
        
        return null;
    }

    /**
     * Move 연산: Assignment를 다른 시간대로 이동
     */
    private List<Assignment> tryMove(List<Assignment> assignments, Map<Long, List<TimeSlot>> availableSlots) {
        // Task Assignment만 필터링
        List<Assignment> taskAssignments = assignments.stream()
            .filter(a -> a.getTask() != null)
            .collect(java.util.stream.Collectors.toList());
        
        if (taskAssignments.isEmpty()) {
            return null;
        }
        
        // 랜덤하게 Assignment 선택
        Assignment selected = taskAssignments.get(random.nextInt(taskAssignments.size()));
        
        // 사용자 ID 추출
        Long userId = extractUserIdFromMeta(selected);
        if (userId == null) {
            return null;
        }
        
        // 사용 가능한 슬롯 찾기
        List<TimeSlot> userSlots = availableSlots.get(userId);
        if (userSlots == null || userSlots.isEmpty()) {
            return null;
        }
        
        // 랜덤하게 새 슬롯 선택
        TimeSlot newSlot = userSlots.get(random.nextInt(userSlots.size()));
        
        // 필요한 슬롯 수 계산
        long durationMinutes = java.time.Duration.between(selected.getStartsAt(), selected.getEndsAt()).toMinutes();
        int requiredSlots = (int) Math.ceil(durationMinutes / 30.0);
        
        // 연속된 슬롯 찾기
        List<TimeSlot> consecutiveSlots = findConsecutiveSlotsFromStart(userSlots, newSlot, requiredSlots);
        if (consecutiveSlots == null || consecutiveSlots.size() < requiredSlots) {
            return null;
        }
        
        // 새로운 Assignment 생성
        List<Assignment> newAssignments = new ArrayList<>();
        for (Assignment a : assignments) {
            if (a == selected) {
                Assignment newA = cloneAssignment(a);
                newA.setStartsAt(consecutiveSlots.get(0).getStartTime());
                newA.setEndsAt(consecutiveSlots.get(consecutiveSlots.size() - 1).getEndTime());
                newA.setSlotIndex(consecutiveSlots.get(0).getSlotIndex());
                newAssignments.add(newA);
            } else {
                newAssignments.add(cloneAssignment(a));
            }
        }
        
        return newAssignments;
    }

    /**
     * Assignment 복제
     */
    private Assignment cloneAssignment(Assignment original) {
        Assignment clone = new Assignment();
        clone.setSchedule(original.getSchedule());
        clone.setTask(original.getTask());
        clone.setTitle(original.getTitle());
        clone.setStartsAt(original.getStartsAt());
        clone.setEndsAt(original.getEndsAt());
        clone.setSource(original.getSource());
        clone.setSlotIndex(original.getSlotIndex());
        clone.setMeta(original.getMeta());
        return clone;
    }

    /**
     * Swap 연산의 유효성 검증
     */
    private boolean isValidSwap(
            List<Assignment> assignments,
            Assignment a1,
            Assignment a2,
            Map<Long, List<TimeSlot>> availableSlots) {
        
        // 간단한 검증: 시간대가 겹치지 않는지 확인
        for (Assignment a : assignments) {
            if (a == a1 || a == a2) {
                continue;
            }
            
            // 시간 겹침 확인
            if (isOverlapping(a, a1) || isOverlapping(a, a2)) {
                return false;
            }
        }
        
        return true;
    }

    /**
     * 두 Assignment가 시간적으로 겹치는지 확인
     */
    private boolean isOverlapping(Assignment a1, Assignment a2) {
        return a1.getStartsAt().isBefore(a2.getEndsAt()) && a1.getEndsAt().isAfter(a2.getStartsAt());
    }

    /**
     * 시작 슬롯부터 연속된 슬롯 찾기
     */
    private List<TimeSlot> findConsecutiveSlotsFromStart(List<TimeSlot> availableSlots, TimeSlot startSlot, int count) {
        List<TimeSlot> result = new ArrayList<>();
        result.add(startSlot);
        
        TimeSlot current = startSlot;
        for (int i = 1; i < count; i++) {
            TimeSlot next = findNextConsecutiveSlot(availableSlots, current);
            if (next == null) {
                return null;
            }
            result.add(next);
            current = next;
        }
        
        return result;
    }

    /**
     * 다음 연속 슬롯 찾기
     */
    private TimeSlot findNextConsecutiveSlot(List<TimeSlot> availableSlots, TimeSlot current) {
        return availableSlots.stream()
            .filter(slot -> current.isConsecutive(slot))
            .findFirst()
            .orElse(null);
    }

    /**
     * Assignment의 meta에서 userId 추출
     */
    private Long extractUserIdFromMeta(Assignment assignment) {
        if (assignment.getMeta() == null) {
            return null;
        }
        
        try {
            String meta = assignment.getMeta();
            int userIdIndex = meta.indexOf("\"userId\":");
            if (userIdIndex == -1) {
                return null;
            }
            
            int start = userIdIndex + 9;
            int end = meta.indexOf("}", start);
            if (end == -1) {
                end = meta.length();
            }
            
            String userIdStr = meta.substring(start, end).trim().replace(",", "").replace("}", "");
            return Long.parseLong(userIdStr);
        } catch (Exception e) {
            return null;
        }
    }
}

