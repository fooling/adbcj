package org.adbcj.tck;


public class InitMysql extends InitDatabase {


    @Override
    protected String setupScript() {
        return "sql/setupMySQL.sql";
    }

    @Override
    protected String tearDownScript() {
        return "sql/cleanUpMySQL.sql";
    }
}
