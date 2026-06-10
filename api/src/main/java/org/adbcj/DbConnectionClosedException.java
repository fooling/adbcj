package org.adbcj;

/**
 * The connection to the database was closed
 */
public class DbConnectionClosedException extends DbException {

	private static final long serialVersionUID = 1L;

	public DbConnectionClosedException() {
		super( "This database connection has been closed");
	}

	public DbConnectionClosedException(String message) {
		super(message);
	}

	public DbConnectionClosedException(String message, Throwable cause, StackTraceElement[] entry) {
		super(message, cause, entry);
	}

}
