/*
 *   Copyright (c) 2007 Mike Heath.  All rights reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */
package org.adbcj;

/**
 * Receives notification of the logical results of a database query.
 * 
 * <p>Each method accepts an accumulator that may be used for holding the parsing state.
 * 
 * @author Mike Heath
 *
 * @param <T>  The accumulator type.
 */
public interface ResultHandler<T> {

	/**
	 * Invoked when field definitions are about to be received.
	 */
	void startFields(T accumulator);
	
	/**
	 * Invoked for each field definition.
	 */
	void field(Field field, T accumulator);
	
	/**
	 * Invoked when all field definitions have been received.
	 */
	void endFields(T accumulator);

	/**
	 * Invoked when rest rows are about to be received.
	 */
	void startResults(T accumulator);
	
	/**
	 * Invoked at the beginning of a data row.
	 */
	void startRow(T accumulator);
	
	/**
	 * Invoked for each column in a data row.
	 */
	void value(Value value, T accumulator);
	
	/**
	 * Invoked at the end of a data row.
	 */
	void endRow(T accumulator);
	
	/**
	 * Invoked after all the data rows have been processed.
	 */
	void endResults(T accumulator);

}
