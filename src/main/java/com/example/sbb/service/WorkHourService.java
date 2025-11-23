package com.example.sbb.service;

import com.example.sbb.domain.Team;
import com.example.sbb.domain.User;
import com.example.sbb.domain.WorkHour;
import com.example.sbb.dto.request.WorkHourCreateRequest;
import com.example.sbb.dto.request.WorkHourUpdateRequest;
import com.example.sbb.dto.response.WorkHourResponse;
import com.example.sbb.repository.TeamRepository;
import com.example.sbb.repository.UserRepository;
import com.example.sbb.repository.WorkHourRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkHourService {

    private final WorkHourRepository workHourRepository;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;

    /**
     * 팀의 근무시간 목록 조회
     */
    @Transactional(readOnly = true)
    public List<WorkHourResponse> getTeamWorkHours(Long teamId) {
        return workHourRepository.findByTeam_Id(teamId).stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    /**
     * 개별 근무시간 조회
     */
    @Transactional(readOnly = true)
    public WorkHourResponse getWorkHour(Long id) {
        WorkHour workHour = workHourRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("근무시간을 찾을 수 없습니다: " + id));
        return toResponse(workHour);
    }

    /**
     * 근무시간 여러 개 한 번에 생성
     */
    @Transactional
    public List<WorkHourResponse> createWorkHours(Long teamId, List<WorkHourCreateRequest> requests) {
        if (CollectionUtils.isEmpty(requests)) {
            return new ArrayList<>();
        }
        
        Team team = teamRepository.findById(teamId)
            .orElseThrow(() -> new EntityNotFoundException("팀을 찾을 수 없습니다: " + teamId));
        
        List<WorkHour> toCreate = new ArrayList<>();
        
        for (WorkHourCreateRequest request : requests) {
            validateWorkHour(request.getDow(), request.getStartMin(), request.getEndMin());
            
            // DB는 1-7을 사용하므로 변환
            int dbDow = request.getDow() + 1;
            
            // 중복 확인
            if (request.getUserId() == null) {
                workHourRepository.findByTeam_IdAndUserIsNullAndDow(teamId, dbDow)
                    .ifPresent(wh -> {
                        throw new IllegalArgumentException(
                            String.format("팀 기본 근무시간이 이미 존재합니다: 팀=%d, 요일=%s", 
                                teamId, getDayName(request.getDow())));
                    });
            } else {
                workHourRepository.findByTeam_IdAndUser_IdAndDow(teamId, request.getUserId(), dbDow)
                    .ifPresent(wh -> {
                        throw new IllegalArgumentException(
                            String.format("개인 근무시간이 이미 존재합니다: 팀=%d, 사용자=%d, 요일=%s", 
                                teamId, request.getUserId(), getDayName(request.getDow())));
                    });
            }
            
            WorkHour workHour = new WorkHour();
            workHour.setTeam(team);
            workHour.setUser(resolveUser(request.getUserId()));
            workHour.setDow(dbDow);
            workHour.setStartMin(request.getStartMin());
            workHour.setEndMin(request.getEndMin());
            toCreate.add(workHour);
        }
        
        List<WorkHour> saved = workHourRepository.saveAll(toCreate);
        log.info("근무시간 일괄 생성: teamId={}, count={}", teamId, saved.size());
        
        return saved.stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    /**
     * 근무시간 생성 (단일)
     */
    @Transactional
    public WorkHourResponse createWorkHour(WorkHourCreateRequest request) {
        validateWorkHour(request.getDow(), request.getStartMin(), request.getEndMin());
        
        Team team = teamRepository.findById(request.getTeamId())
            .orElseThrow(() -> new EntityNotFoundException("팀을 찾을 수 없습니다: " + request.getTeamId()));
        
        // 중복 확인: 같은 팀, 같은 사용자(또는 null), 같은 요일
        // DB는 1-7을 사용하므로 변환
        int dbDow = request.getDow() + 1;
        if (request.getUserId() == null) {
            workHourRepository.findByTeam_IdAndUserIsNullAndDow(request.getTeamId(), dbDow)
                .ifPresent(wh -> {
                    throw new IllegalArgumentException(
                        String.format("팀 기본 근무시간이 이미 존재합니다: 팀=%d, 요일=%s", 
                            request.getTeamId(), getDayName(request.getDow())));
                });
        } else {
            workHourRepository.findByTeam_IdAndUser_IdAndDow(
                request.getTeamId(), request.getUserId(), dbDow)
                .ifPresent(wh -> {
                    throw new IllegalArgumentException(
                        String.format("개인 근무시간이 이미 존재합니다: 팀=%d, 사용자=%d, 요일=%s", 
                            request.getTeamId(), request.getUserId(), getDayName(request.getDow())));
                });
        }
        
        WorkHour workHour = new WorkHour();
        workHour.setTeam(team);
        workHour.setUser(resolveUser(request.getUserId()));
        // 프론트엔드는 0-6 (월-일)을 사용하지만, DB는 1-7 (월-일)을 요구하므로 변환
        workHour.setDow(request.getDow() + 1);
        workHour.setStartMin(request.getStartMin());
        workHour.setEndMin(request.getEndMin());
        
        WorkHour saved = workHourRepository.save(workHour);
        log.info("근무시간 생성: id={}, teamId={}, userId={}, dow={}, startMin={}, endMin={}", 
            saved.getId(), request.getTeamId(), request.getUserId(), request.getDow(), 
            request.getStartMin(), request.getEndMin());
        
        return toResponse(saved);
    }

    /**
     * 근무시간 수정
     */
    @Transactional
    public WorkHourResponse updateWorkHour(Long id, WorkHourUpdateRequest request) {
        WorkHour workHour = workHourRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("근무시간을 찾을 수 없습니다: " + id));
        
        validateWorkHour(request.getDow(), request.getStartMin(), request.getEndMin());
        
        // userId 변경 시 중복 확인
        Long newUserId = request.getUserId();
        Long oldUserId = workHour.getUser() != null ? workHour.getUser().getId() : null;
        
        if (!Objects.equals(newUserId, oldUserId) || !Objects.equals(workHour.getDow(), request.getDow())) {
            // userId나 dow가 변경되는 경우 중복 확인
        // DB는 1-7을 사용하므로 변환
        int dbDow = request.getDow() + 1;
            if (newUserId == null) {
                workHourRepository.findByTeam_IdAndUserIsNullAndDow(workHour.getTeam().getId(), dbDow)
                    .filter(wh -> !wh.getId().equals(id))
                    .ifPresent(wh -> {
                        throw new IllegalArgumentException(
                            String.format("팀 기본 근무시간이 이미 존재합니다: 팀=%d, 요일=%s", 
                                workHour.getTeam().getId(), getDayName(request.getDow())));
                    });
            } else {
                workHourRepository.findByTeam_IdAndUser_IdAndDow(
                    workHour.getTeam().getId(), newUserId, dbDow)
                    .filter(wh -> !wh.getId().equals(id))
                    .ifPresent(wh -> {
                        throw new IllegalArgumentException(
                            String.format("개인 근무시간이 이미 존재합니다: 팀=%d, 사용자=%d, 요일=%s", 
                                workHour.getTeam().getId(), newUserId, getDayName(request.getDow())));
                    });
            }
        }
        
        workHour.setUser(resolveUser(newUserId));
        // 프론트엔드는 0-6 (월-일)을 사용하지만, DB는 1-7 (월-일)을 요구하므로 변환
        workHour.setDow(request.getDow() + 1);
        workHour.setStartMin(request.getStartMin());
        workHour.setEndMin(request.getEndMin());
        
        WorkHour saved = workHourRepository.save(workHour);
        log.info("근무시간 수정: id={}, teamId={}, userId={}, dow={}, startMin={}, endMin={}", 
            saved.getId(), workHour.getTeam().getId(), newUserId, request.getDow(), 
            request.getStartMin(), request.getEndMin());
        
        return toResponse(saved);
    }

    /**
     * 근무시간 삭제
     */
    @Transactional
    public void deleteWorkHour(Long id) {
        WorkHour workHour = workHourRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("근무시간을 찾을 수 없습니다: " + id));
        
        log.info("근무시간 삭제: id={}, teamId={}, userId={}, dow={}", 
            id, workHour.getTeam().getId(), 
            workHour.getUser() != null ? workHour.getUser().getId() : null, 
            workHour.getDow());
        
        workHourRepository.delete(workHour);
    }

    @Transactional
    public List<WorkHourResponse> upsertWorkHours(Long teamId, List<WorkHourUpdateRequest> updates) {
        if (CollectionUtils.isEmpty(updates)) {
            return getTeamWorkHours(teamId);
        }

        Team team = teamRepository.findById(teamId)
            .orElseThrow(() -> new EntityNotFoundException("Team not found: " + teamId));

        // 기존 근무시간을 프론트엔드 형식(0-6)으로 변환하여 키로 사용
        Map<WorkHourKey, WorkHour> existingByKey = workHourRepository.findByTeam_Id(teamId).stream()
            .collect(Collectors.toMap(
                this::toKey,
                Function.identity(),
                (left, right) -> left,
                LinkedHashMap::new
            ));

        List<WorkHour> toPersist = new ArrayList<>();
        for (WorkHourUpdateRequest update : updates) {
            validateUpdate(update);

            // 프론트엔드는 0-6을 사용하지만, DB는 1-7을 사용하므로 변환
            int dbDow = update.getDow() + 1;
            WorkHourKey key = new WorkHourKey(update.getUserId(), update.getDow());
            WorkHour workHour = existingByKey.get(key);
            if (workHour == null) {
                workHour = new WorkHour();
                workHour.setTeam(team);
                workHour.setDow(dbDow);
                existingByKey.put(key, workHour);
            }
            workHour.setStartMin(update.getStartMin());
            workHour.setEndMin(update.getEndMin());
            if (!Objects.equals(idOrNull(workHour.getUser()), update.getUserId())) {
                workHour.setUser(resolveUser(update.getUserId()));
            }
            toPersist.add(workHour);
        }

        List<WorkHour> saved = workHourRepository.saveAll(toPersist);
        return saved.stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    /**
     * 근무시간 검증 (공통)
     */
    private void validateWorkHour(Integer dow, Integer startMin, Integer endMin) {
        if (dow == null || dow < 0 || dow > 6) {
            throw new IllegalArgumentException("요일(dow)은 0~6 범위여야 합니다. (0=월요일, 6=일요일)");
        }
        if (startMin == null || endMin == null) {
            throw new IllegalArgumentException("근무 시작/종료 시간은 필수입니다.");
        }
        if (startMin < 0 || startMin >= 1440) {
            throw new IllegalArgumentException("시작 시간은 0~1439 사이여야 합니다.");
        }
        if (endMin < 0 || endMin > 1440) {
            throw new IllegalArgumentException("종료 시간은 0~1440 사이여야 합니다.");
        }
        if (endMin <= startMin) {
            throw new IllegalArgumentException("종료 시간은 시작 시간보다 이후여야 합니다.");
        }
    }

    private void validateUpdate(WorkHourUpdateRequest update) {
        validateWorkHour(update.getDow(), update.getStartMin(), update.getEndMin());
    }

    private WorkHourKey toKey(WorkHour workHour) {
        // DB는 1-7 (월-일)을 사용하지만, 프론트엔드는 0-6 (월-일)을 사용하므로 변환
        return new WorkHourKey(idOrNull(workHour.getUser()), workHour.getDow() - 1);
    }

    private Long idOrNull(User user) {
        return user != null ? user.getId() : null;
    }

    private User resolveUser(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
    }

    private WorkHourResponse toResponse(WorkHour workHour) {
        WorkHourResponse response = new WorkHourResponse();
        response.setId(workHour.getId());
        response.setTeamId(workHour.getTeam() != null ? workHour.getTeam().getId() : null);
        response.setTeamName(workHour.getTeam() != null ? workHour.getTeam().getName() : null);
        response.setUserId(workHour.getUser() != null ? workHour.getUser().getId() : null);
        response.setUserName(workHour.getUser() != null ? workHour.getUser().getName() : null);
        // DB는 1-7 (월-일)을 사용하지만, 프론트엔드는 0-6 (월-일)을 사용하므로 변환
        response.setDow(workHour.getDow() - 1);
        response.setStartMin(workHour.getStartMin());
        response.setEndMin(workHour.getEndMin());
        return response;
    }

    /**
     * 요일 번호를 요일 이름으로 변환 (디버깅/에러 메시지용)
     */
    private String getDayName(int dow) {
        String[] days = {"월요일", "화요일", "수요일", "목요일", "금요일", "토요일", "일요일"};
        if (dow >= 0 && dow < days.length) {
            return days[dow];
        }
        return "요일 " + dow;
    }

    private record WorkHourKey(Long userId, Integer dow) {
    }
}

