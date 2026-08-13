package com.middleproject.reminder.domain;
import java.util.UUID;
public record Reminder(UUID id, UUID eventId, UUID policyId, ReminderStatus status, long version) {
 public Reminder transitionTo(ReminderStatus target) { if (!status.canTransitionTo(target)) throw new IllegalStateException("Invalid reminder transition: " + status + " -> " + target); return new Reminder(id,eventId,policyId,target,version+1); }
}
