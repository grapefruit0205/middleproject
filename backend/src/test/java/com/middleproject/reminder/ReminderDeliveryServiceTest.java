package com.middleproject.reminder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.middleproject.reminder.application.ReminderDeliveryService;
import com.middleproject.reminder.port.ReminderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ReminderDeliveryServiceTest {
    private final JdbcTemplate db = mock(JdbcTemplate.class);
    private final ReminderDeliveryService service = new ReminderDeliveryService(db, mock(ReminderRepository.class), new ObjectMapper());
    private final UUID id = UUID.randomUUID();

    @BeforeEach void databaseProduct() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(db.getDataSource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("PostgreSQL");
    }

    private String body(long version) { return "{\"reminderId\":\"" + id + "\",\"schedulerVersion\":" + version + ",\"idempotencyKey\":\"" + id + ":" + version + "\"}"; }

    @Test void validDeliveryDispatchesOnceAndConflictFreeDuplicateIsIgnored() {
        when(db.update(startsWith("insert into reminder_delivery_receipt"), any(), any(), any())).thenReturn(1, 0);
        when(db.update(startsWith("update reminders"), any(), any(), any())).thenReturn(1);
        assertEquals(ReminderDeliveryService.AcceptResult.ACCEPTED, service.acceptResult(body(1)));
        assertEquals(ReminderDeliveryService.AcceptResult.IGNORED, service.acceptResult(body(1)));
        verify(db, times(1)).update(startsWith("update reminders"), any(), any(), any());
    }

    @Test void malformedAndWrongVersionAreIgnored() {
        assertEquals(ReminderDeliveryService.AcceptResult.IGNORED, service.acceptResult("not-json"));
        assertEquals(ReminderDeliveryService.AcceptResult.IGNORED, service.acceptResult(body(2)));
    }

    @Test void databaseFailurePropagatesAsTransient() {
        when(db.update(startsWith("insert into reminder_delivery_receipt"), any(), any(), any()))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));
        assertThrows(DataAccessResourceFailureException.class, () -> service.accept(body(1)));
    }
}
