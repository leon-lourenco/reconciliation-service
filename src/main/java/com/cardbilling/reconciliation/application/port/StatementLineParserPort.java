package com.cardbilling.reconciliation.application.port;

import com.cardbilling.reconciliation.domain.ExternalStatementLine;
import java.io.InputStream;
import java.util.function.Consumer;

/**
 * Turns an uploaded statement file into statement lines, one at a time.
 *
 * <p>The callback shape is the contract, not a style choice: it is what stops an implementation
 * from reading the whole file into a list first, the way the legacy's {@code CSVReader.readAll()}
 * did. Whatever the file's size, an implementation of this hands over one line and moves on.
 */
public interface StatementLineParserPort {

    /**
     * @return how many data rows were read (the header row does not count)
     * @throws com.cardbilling.reconciliation.domain.MalformedStatementLineException on the first
     *         row that is not a statement line
     */
    long parse(InputStream csv, Consumer<ExternalStatementLine> onLine);
}
