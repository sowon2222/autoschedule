package com.example.sbb.service;

import com.example.sbb.domain.Team;
import com.example.sbb.domain.User;
import com.example.sbb.domain.WorkHour;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

@Service
@RequiredArgsConstructor
public class WorkHourService {

    private final WorkHourRepository workHourRepository;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<WorkHourResponse> getTeamWorkHours(Long teamId) {
        return workHourRepository.findByTeam_Id(teamId).stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    @Transactional
    public List<WorkHourResponse> upsertWorkHours(Long teamId, List<WorkHourUpdateRequest> updates) {
        if (CollectionUtils.isEmpty(updates)) {
            return getTeamWorkHours(teamId);
        }

        Team team = teamRepository.findById(teamId)
            .orElseThrow(() -> new EntityNotFoundException("Team not found: " + teamId));

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

            WorkHourKey key = new WorkHourKey(update.getUserId(), update.getDow());
            WorkHour workHour = existingByKey.get(key);
            if (workHour == null) {
                workHour = new WorkHour();
                workHour.setTeam(team);
                workHour.setDow(update.getDow());
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

    private void validateUpdate(WorkHourUpdateRequest update) {
        if (update.getDow() == null || update.getDow() < 1 || update.getDow() > 7) {
            throw new IllegalArgumentException("dow는 1~7 범위여야 합니다.");
        }
        if (update.getStartMin() == null || update.getEndMin() == null) {
            throw new IllegalArgumentException("근무 시작/종료 시간은 필수입니다.");
        }
        if (update.getStartMin() < 0 || update.getEndMin() > 24 * 60) {
            throw new IllegalArgumentException("근무 시간은 0~1440 사이여야 합니다.");
        }
        if (update.getEndMin() <= update.getStartMin()) {
            throw new IllegalArgumentException("종료 시간은 시작 시간보다 이후여야 합니다.");
        }
    }

    private WorkHourKey toKey(WorkHour workHour) {
        return new WorkHourKey(idOrNull(workHour.getUser()), workHour.getDow());
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
        response.setDow(workHour.getDow());
        response.setStartMin(workHour.getStartMin());
        response.setEndMin(workHour.getEndMin());
        return response;
    }

    private record WorkHourKey(Long userId, Integer dow) {
    }
}

