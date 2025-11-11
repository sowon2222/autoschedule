package com.example.sbb.service;

import com.example.sbb.dto.event.CollaborationNotificationMessage;
import com.example.sbb.dto.event.ScheduleProgressMessage;
import com.example.sbb.dto.response.ScheduleResponse;
import org.springframework.stereotype.Service;

@Service
public class ScheduleOptimizationService {

    private final CollaborationEventPublisher eventPublisher;

    public ScheduleOptimizationService(CollaborationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void publishProgress(Long teamId, int progress, String message) {
        ScheduleProgressMessage progressMessage = ScheduleProgressMessage.progress(teamId, progress, message);
        eventPublisher.publishScheduleProgress(progressMessage);
    }

    public void publishCompletion(Long teamId, ScheduleResponse schedule) {
        ScheduleProgressMessage completion = ScheduleProgressMessage.completed(teamId, schedule);
        eventPublisher.publishScheduleProgress(completion);
        if (schedule != null) {
            eventPublisher.publishScheduleBroadcast(schedule);
            eventPublisher.publishNotification(
                CollaborationNotificationMessage.team(
                    teamId,
                    "SCHEDULE_COMPLETED",
                    "스케줄 최적화 완료",
                    "팀 스케줄 최적화가 완료되었습니다.")
            );
        }
    }

    public void publishFailure(Long teamId, String reason) {
        ScheduleProgressMessage failure = ScheduleProgressMessage.failed(teamId, reason);
        eventPublisher.publishScheduleProgress(failure);
        eventPublisher.publishNotification(
            CollaborationNotificationMessage.team(
                teamId,
                "SCHEDULE_FAILED",
                "스케줄 최적화 실패",
                reason)
        );
    }
}

