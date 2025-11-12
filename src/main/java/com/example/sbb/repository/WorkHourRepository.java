package com.example.sbb.repository;

import com.example.sbb.domain.WorkHour;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkHourRepository extends JpaRepository<WorkHour, Long> {

    List<WorkHour> findByTeam_Id(Long teamId);

    Optional<WorkHour> findByTeam_IdAndUser_IdAndDow(Long teamId, Long userId, Integer dow);

    Optional<WorkHour> findByTeam_IdAndUserIsNullAndDow(Long teamId, Integer dow);
}

