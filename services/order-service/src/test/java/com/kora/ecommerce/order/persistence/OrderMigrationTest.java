package com.kora.ecommerce.order.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderMigrationTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Test
    void flywayAppliesInitialOrderSchema() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("2");

        assertThat(tableNames())
                .contains("orders", "order_items", "order_status_history", "outbox_events", "processed_events");
    }

    @Test
    void schemaIncludesReadOrientedIndexesAndForeignKeys() throws SQLException {
        assertThat(indexNames("orders"))
                .contains("idx_orders_customer_created_at", "idx_orders_status_created_at");
        assertThat(indexNames("order_items"))
                .contains("idx_order_items_order_id", "idx_order_items_product_id");
        assertThat(indexNames("order_status_history"))
                .contains("idx_order_status_history_order_changed_at");
        assertThat(indexNames("outbox_events"))
                .contains("idx_outbox_events_aggregate", "idx_outbox_events_event_type_occurred_at");
        assertThat(indexNames("processed_events"))
                .contains("idx_processed_events_aggregate", "idx_processed_events_payment");

        assertThat(importedKeyNames("order_items")).contains("fk_order_items_order");
        assertThat(importedKeyNames("order_status_history")).contains("fk_order_status_history_order");
    }

    private Set<String> tableNames() {
        return new HashSet<>(jdbcTemplate.queryForList(
                "select lower(table_name) from information_schema.tables where lower(table_schema) = 'public'",
                String.class));
    }

    private Set<String> indexNames(String tableName) throws SQLException {
        Set<String> names = new HashSet<>();
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            try (ResultSet indexes = metadata.getIndexInfo(null, null, tableName, false, false)) {
                while (indexes.next()) {
                    String indexName = indexes.getString("INDEX_NAME");
                    if (indexName != null) {
                        names.add(indexName.toLowerCase());
                    }
                }
            }
        }
        return names;
    }

    private Set<String> importedKeyNames(String tableName) throws SQLException {
        Set<String> names = new HashSet<>();
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            try (ResultSet importedKeys = metadata.getImportedKeys(null, null, tableName)) {
                while (importedKeys.next()) {
                    String keyName = importedKeys.getString("FK_NAME");
                    if (keyName != null) {
                        names.add(keyName.toLowerCase());
                    }
                }
            }
        }
        return names;
    }
}
