package com.middleproject.reminder.domain;
import java.util.UUID;
public record NotificationPolicy(UUID id, String channel, int leadMinutes, long version) {}
