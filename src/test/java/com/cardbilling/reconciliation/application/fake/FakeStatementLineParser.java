package com.cardbilling.reconciliation.application.fake;

import com.cardbilling.reconciliation.application.port.StatementLineParserPort;
import com.cardbilling.reconciliation.domain.ExternalStatementLine;
import java.io.InputStream;
import java.util.List;
import java.util.function.Consumer;

/**
 * Hands the use case a fixed set of lines, one at a time, the way a real parser reading a file
 * would. The {@link InputStream} is ignored - what the CSV library does with a real file is the
 * adapter's own test.
 */
public class FakeStatementLineParser implements StatementLineParserPort {

    private final List<ExternalStatementLine> lines;

    public FakeStatementLineParser(List<ExternalStatementLine> lines) {
        this.lines = lines;
    }

    @Override
    public long parse(InputStream csv, Consumer<ExternalStatementLine> onLine) {
        lines.forEach(onLine);
        return lines.size();
    }
}
