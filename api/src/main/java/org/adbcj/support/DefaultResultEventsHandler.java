package org.adbcj.support;

import org.adbcj.Field;
import org.adbcj.ResultHandler;
import org.adbcj.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DefaultResultEventsHandler implements ResultHandler<DefaultResultSet> {
    private static final Logger logger = LoggerFactory.getLogger(DefaultResultEventsHandler.class);

    // TODO: Make this handler stateless
    private Value[] currentRow;
    private int rowIndex;

    public void startFields(DefaultResultSet accumulator) {
        logger.debug("ResultSetEventHandler: startFields");
    }

    public void field(Field field, DefaultResultSet accumulator) {
        logger.debug("ResultSetEventHandler: field");
        accumulator.addField(field);
    }

    public void endFields(DefaultResultSet accumulator) {
        logger.debug("ResultSetEventHandler: endFields");
    }

    public void startResults(DefaultResultSet accumulator) {
        logger.debug("ResultSetEventHandler: startResults");
    }

    public void startRow(DefaultResultSet accumulator) {
        logger.debug("ResultSetEventHandler: startRow");

        int columnCount = accumulator.getFields().size();
        currentRow = new Value[columnCount];
    }

    public void value(Value value, DefaultResultSet accumulator) {
        logger.debug("ResultSetEventHandler: value");

        currentRow[rowIndex%currentRow.length] = value;
        rowIndex++;
    }

    public void endRow(DefaultResultSet accumulator) {
        logger.debug("ResultSetEventHandler: endRow");
        DefaultRow row = new DefaultRow(accumulator, currentRow);
        accumulator.addResult(row);
        currentRow = null;
    }

    public void endResults(DefaultResultSet accumulator) {
        logger.debug("ResultSetEventHandler: endResults");
    }

    public void exception(Throwable t, DefaultResultSet accumulator) {
    }
}
