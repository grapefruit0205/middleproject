package com.middleproject.reminder.port;

import com.middleproject.reminder.domain.PlaceCandidate;
import com.middleproject.reminder.domain.PlaceSearchRequest;
import com.middleproject.reminder.domain.ProviderOutcome;

import java.util.List;

public interface PlaceSearchProviderPort {

    ProviderOutcome<List<PlaceCandidate>> search(PlaceSearchRequest request);
}
