/**
 * reader - Data Reader for the Big Data Platform, that queries the database for web-services
 *
 * Copyright © 2018 IDM Südtirol - Alto Adige (info@idm-suedtirol.com)
 * Copyright © 2019 NOI Techpark - Südtirol / Alto Adige (info@opendatahub.bz.it)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program (see LICENSES/GPL-3.0.txt). If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * SPDX-License-Identifier: GPL-3.0
 */
package it.bz.idm.bdp.ninja.config;

import java.util.Date;

import org.postgresql.util.PSQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import it.bz.idm.bdp.ninja.utils.conditionals.ConditionalMap;
import it.bz.idm.bdp.ninja.utils.simpleexception.SimpleException;

/**
 * API exception handler mapping every exception to a serializable object
 *
 * @author Peter Moser
 */
@ControllerAdvice
public class ErrorResponseConfig extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ErrorResponseConfig.class);

	@ExceptionHandler
	public ResponseEntity<Object> handleException(Exception ex) {
		log.error(ex.getMessage(), ex);
		return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, ex);
	}

	@ExceptionHandler
	public ResponseEntity<Object> handleException(SimpleException ex) {
		return buildResponse(HttpStatus.BAD_REQUEST, ex);
	}

	@ExceptionHandler
	public ResponseEntity<Object> handleException(DataAccessException ex) {
		Throwable cause = ex.getCause();
		if (cause instanceof PSQLException) {
			return buildResponse(HttpStatus.BAD_REQUEST, (PSQLException) cause);
		}
		return buildResponse(HttpStatus.BAD_REQUEST, ex);
	}

	private ResponseEntity<Object> buildResponse(final HttpStatus httpStatus, final Exception exception) {
		String message = (exception == null || exception.getMessage() == null) ? exception.getClass().getSimpleName() : exception.getMessage();
		ConditionalMap map = ConditionalMap
			.init()
			.put("message", message)
			.put("timestamp", new Date())
			.put("code", httpStatus.value())
			.put("error", httpStatus.getReasonPhrase());
		if (exception instanceof SimpleException) {
			SimpleException se = (SimpleException) exception;
			map.putIfNotNull("description", se.getDescription());
			map.putIfNotEmpty("info", se.getData());
		} else if (exception instanceof PSQLException) {
			PSQLException cause = (PSQLException) exception;
			switch(cause.getSQLState()) {
				case "57014":
					map.put("description", "Query timed out");
					map.put("hint", "Query for smaller response chunks. Use SELECT, WHERE, LIMIT with OFFSET, or a narrow time interval.");
					break;
				default:
					map.put("message", message.replace("\\n", ""));
					map.put("description", "Error from the database backend");
			}

		}

		if (log.isDebugEnabled()) {
			log.error(message, exception);
		}
		return new ResponseEntity<>(map.get(), httpStatus);
	}
}
