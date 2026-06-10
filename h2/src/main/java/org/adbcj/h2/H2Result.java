package org.adbcj.h2;

import org.adbcj.ResultSet;
import org.adbcj.support.DefaultResult;

import java.util.List;


public class H2Result extends DefaultResult {
    private final ResultSet result;

    public H2Result(ResultSet result,Long affectedRows, List<String> warnings) {
        super(affectedRows, warnings);
        this.result = result;
    }

    @Override
    public ResultSet getGeneratedKeys() {
        return result;
    }
}
