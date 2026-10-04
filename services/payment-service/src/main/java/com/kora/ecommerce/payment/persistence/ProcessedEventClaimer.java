package com.kora.ecommerce.payment.persistence;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ProcessedEventClaimer {

    private static final String POSTGRES_CLAIM_SQL = """
            INSERT INTO processed_events (
                id,
                consumer_name,
                event_id,
                event_type,
                event_version,
                aggregate_id,
                trace_id,
                correlation_id,
                processed_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (consumer_name, event_id) DO NOTHING
            """;

    private static final String H2_CLAIM_SQL = """
            MERGE INTO processed_events AS target
            USING (VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)) AS source (
                id,
                consumer_name,
                event_id,
                event_type,
                event_version,
                aggregate_id,
                trace_id,
                correlation_id,
                processed_at
            )
            ON target.consumer_name = source.consumer_name
                AND target.event_id = source.event_id
            WHEN NOT MATCHED THEN
                INSERT (
                    id,
                    consumer_name,
                    event_id,
                    event_type,
                    event_version,
                    aggregate_id,
                    trace_id,
                    correlation_id,
                    processed_at
                )
                VALUES (
                    source.id,
                    source.consumer_name,
                    source.event_id,
                    source.event_type,
                    source.event_version,
                    source.aggregate_id,
                    source.trace_id,
                    source.correlation_id,
                    source.processed_at
                )
            """;

    private final JdbcTemplate jdbcTemplate;
    private final String claimSql;
    private final boolean h2;

    public ProcessedEventClaimer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        String databaseProduct = jdbcTemplate.execute(
                (ConnectionCallback<String>) connection -> connection.getMetaData().getDatabaseProductName());
        this.h2 = databaseProduct != null && databaseProduct.toLowerCase(Locale.ROOT).contains("h2");
        this.claimSql = h2 ? H2_CLAIM_SQL : POSTGRES_CLAIM_SQL;
    }

    public boolean claimIfAbsent(
            UUID id,
            String consumerName,
            UUID eventId,
            String eventType,
            int eventVersion,
            UUID aggregateId,
            String traceId,
            String correlationId,
            Instant processedAt) {
        int changedRows;
        try {
            changedRows = jdbcTemplate.update(
                    claimSql,
                    statement -> bind(
                            statement,
                            id,
                            consumerName,
                            eventId,
                            eventType,
                            eventVersion,
                            aggregateId,
                            traceId,
                            correlationId,
                            processedAt));
        } catch (DuplicateKeyException exception) {
            if (h2) {
                return false;
            }
            throw exception;
        }
        return changedRows == 1;
    }

    private void bind(
            PreparedStatement statement,
            UUID id,
            String consumerName,
            UUID eventId,
            String eventType,
            int eventVersion,
            UUID aggregateId,
            String traceId,
            String correlationId,
            Instant processedAt) throws SQLException {
        statement.setObject(1, id);
        statement.setString(2, consumerName);
        statement.setObject(3, eventId);
        statement.setString(4, eventType);
        statement.setInt(5, eventVersion);
        statement.setObject(6, aggregateId);
        statement.setString(7, traceId);
        statement.setString(8, correlationId);
        statement.setObject(9, OffsetDateTime.ofInstant(processedAt, ZoneOffset.UTC));
    }
}
