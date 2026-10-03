package com.kora.ecommerce.catalog.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class CatalogMigrationTest {

    @Test
    void flywayMigrationsCanBuildCatalogSchemaFromCleanDatabaseMoreThanOnce() {
        Flyway flyway = Flyway.configure()
                .dataSource(
                        "jdbc:h2:mem:catalog_migration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                        "sa",
                        "")
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();

        flyway.clean();
        flyway.migrate();

        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("3");

        flyway.clean();
        flyway.migrate();

        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("3");
    }
}
