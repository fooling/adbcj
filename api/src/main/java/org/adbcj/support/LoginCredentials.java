/*
	This file is part of ADBCJ.

	ADBCJ is free software: you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.

	ADBCJ is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
	GNU General Public License for more details.

	You should have received a copy of the GNU General Public License
	along with ADBCJ.  If not, see <http://www.gnu.org/licenses/>.

	Copyright 2008  Mike Heath
*/
package org.adbcj.support;

public final class LoginCredentials {

	private final String userName;
	private final String password;
	private final String database;

	public LoginCredentials(String userName, String password, String database) {
		this.userName = userName;
		this.password = password;
		this.database = database;
	}

	public String getDatabase() {
		return database;
	}

	public String getPassword() {
		return password;
	}

	public String getUserName() {
		return userName;
	}

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LoginCredentials that = (LoginCredentials) o;

        if (!database.equals(that.database)) return false;
        if (password != null ? !password.equals(that.password) : that.password != null) return false;
        return userName.equals(that.userName);
    }

    @Override
    public int hashCode() {
        int result = userName.hashCode();
        result = 31 * result + (password != null ? password.hashCode() : 0);
        result = 31 * result + database.hashCode();
        return result;
    }

    @Override
    public String toString() {
    	// Mask password for security issue sine 2017-10-15 little-pan
        return "LoginCredentials{" +
                "userName='" + userName + '\'' +
                ", password='" + "******" + '\'' +
                ", database='" + database + '\'' +
                '}';
    }
}
