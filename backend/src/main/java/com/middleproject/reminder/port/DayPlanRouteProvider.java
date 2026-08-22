package com.middleproject.reminder.port;

import com.middleproject.reminder.domain.DayPlanRouteEstimate;
import com.middleproject.reminder.domain.DayPlanRouteRequest;
import com.middleproject.reminder.domain.ProviderOutcome;

/** Read-only route-estimate port used by the day-plan preview. */
public interface DayPlanRouteProvider {
    ProviderOutcome<DayPlanRouteEstimate> estimate(DayPlanRouteRequest request);
}
