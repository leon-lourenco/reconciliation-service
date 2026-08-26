package com.cardbilling.reconciliation.infrastructure.config;

import com.cardbilling.reconciliation.application.IngestStatementUseCase;
import com.cardbilling.reconciliation.application.MatchStatementUseCase;
import com.cardbilling.reconciliation.application.port.BillingServicePort;
import com.cardbilling.reconciliation.application.port.ReconciliationMatchRepositoryPort;
import com.cardbilling.reconciliation.application.port.ReconciliationRunRepositoryPort;
import com.cardbilling.reconciliation.application.port.StatementLineParserPort;
import com.cardbilling.reconciliation.application.port.StatementLineRepositoryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the use cases from their ports.
 *
 * <p>They are declared here rather than annotated with {@code @Service} so that
 * {@code application} has no Spring import at all - which is what makes the ArchUnit rule about it
 * mean something, and what lets every use-case test run against in-memory fakes with no container.
 */
@Configuration(proxyBeanMethods = false)
public class UseCaseConfiguration {

    @Bean
    IngestStatementUseCase ingestStatementUseCase(StatementLineParserPort parser,
            StatementLineRepositoryPort statementLines) {
        return new IngestStatementUseCase(parser, statementLines);
    }

    @Bean
    MatchStatementUseCase matchStatementUseCase(StatementLineRepositoryPort statementLines,
            ReconciliationRunRepositoryPort runs, ReconciliationMatchRepositoryPort matches,
            BillingServicePort billingService) {
        return new MatchStatementUseCase(statementLines, runs, matches, billingService);
    }
}
