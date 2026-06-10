package org.adbcj.h2.protocol;


public enum CommandCodes {
    SESSION_PREPARE(0),
    SESSION_CLOSE(1),
    COMMAND_EXECUTE_QUERY(2),
    COMMAND_EXECUTE_UPDATE(3),
    COMMAND_CLOSE(4),
    RESULT_CLOSE(7),
    SESSION_PREPARE_READ_PARAMS(11),
    SESSION_SET_ID(12),
    SESSION_SET_AUTOCOMMIT(15),
    SESSION_UNDO_LOG_POS(16);

    private final int statusValue;

    CommandCodes(int statusValue) {
        this.statusValue = statusValue;
    }

    public int getCommandValue() {
        return statusValue;
    }

    public boolean isCommand(int status) {
        return this.statusValue == status;
    }

    public static CommandCodes commandFor(int command) {
        for (CommandCodes cmd : values()) {
            if(cmd.getCommandValue()==command){
                return cmd;
            }
        }
        throw new IllegalStateException("Cannot interpret command: "+command);
    }
}
