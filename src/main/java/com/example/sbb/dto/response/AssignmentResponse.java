package com.example.sbb.dto.response;

import com.example.sbb.domain.AssignmentSource;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentResponse {
    private Long id;
    private Long scheduleId;
    private Long taskId;
    private String taskTitle;  // 간단한 작업 정보
    private String title;
    private OffsetDateTime startsAt;
    private OffsetDateTime endsAt;
    private AssignmentSource source;
    private Integer slotIndex;
    private String meta;
}

