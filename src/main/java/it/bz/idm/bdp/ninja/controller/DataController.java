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
package it.bz.idm.bdp.ninja.controller;

import static net.logstash.logback.argument.StructuredArguments.v;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jsoniter.output.JsonStream;

import org.keycloak.adapters.springsecurity.account.SimpleKeycloakAccount;
import org.keycloak.adapters.springsecurity.token.KeycloakAuthenticationToken;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import it.bz.idm.bdp.ninja.DataFetcher;
import it.bz.idm.bdp.ninja.config.SelectExpansionConfig;
import it.bz.idm.bdp.ninja.utils.FileUtils;
import it.bz.idm.bdp.ninja.utils.Representation;
import it.bz.idm.bdp.ninja.utils.Timer;
import it.bz.idm.bdp.ninja.utils.resultbuilder.ResultBuilder;
import it.bz.idm.bdp.ninja.utils.simpleexception.ErrorCodeInterface;
import it.bz.idm.bdp.ninja.utils.simpleexception.SimpleException;

/**
 * @author Peter Moser
 */
@RestController
@RequestMapping(value = "")
public class DataController {

	private static final Logger log = LoggerFactory.getLogger(DataController.class);

	/* Do not forget to update DOC_TIME, when changing this */
	private static final String DATETIME_FORMAT_PATTERN = "yyyy-MM-dd['T'[HH][:mm][:ss][.SSS]][Z][z]";
	private static final String DEFAULT_LIMIT = "200";
	private static final String DEFAULT_OFFSET = "0";
	private static final String DEFAULT_SHOWNULL = "false";
	private static final String DEFAULT_DISTINCT = "true";
	private static final String DEFAULT_TIMEZONE = "UTC";

	private static final DateTimeFormatter DATE_FORMAT = new DateTimeFormatterBuilder()
		.appendPattern(DATETIME_FORMAT_PATTERN)
		.parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
		.parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
		.parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
		.parseDefaulting(ChronoField.NANO_OF_SECOND, 0)
		.toFormatter();

	@Value("${ninja.baseurl}")
	private String ninjaBaseUrl;

	@Value("${ninja.hosturl}")
	private String ninjaHostUrl;

	@Value("${ninja.response.max-allowed-size-mb}")
	private int maxAllowedSizeInMB;

	@Autowired
	DataFetcher dataFetcher;

	private String fileRoot;
	private String fileSpec;

	public enum ErrorCode implements ErrorCodeInterface {
		DATE_PARSE_ERROR(
				"Invalid date given. Format must be %s, where [] denotes optionality. Do not forget, single digits must be leaded by 0. Error message: %s.");

		private final String msg;

		ErrorCode(final String msg) {
			this.msg = msg;
		}

		@Override
		public String getMsg() {
			return "PARSING ERROR: " + msg;
		}
	}

	@GetMapping(value = "", produces = "application/json;charset=UTF-8")
	public @ResponseBody String requestRoot() {
		if (fileRoot == null) {
			fileRoot = FileUtils.loadFile("root.json");
			fileRoot = FileUtils.replacements(fileRoot, "__URL__", ninjaBaseUrl);
		}
		return fileRoot;
	}

	@GetMapping(value = "/apispec", produces = "application/yaml;charset=UTF-8")
	public @ResponseBody String requestOpenApiSpec() {
		if (fileSpec == null) {
			fileSpec = FileUtils.loadFile("openapi3.yml");
			fileSpec = FileUtils.replacements(fileSpec, "__ODH_SERVER_URL__", ninjaHostUrl);
		}
		return fileSpec;
	}

	@GetMapping(value = "/{representation}", produces = "application/json;charset=UTF-8")
	public @ResponseBody String requestStationTypes(@PathVariable final String representation) {
		Representation rep = Representation.get(representation);
		final List<Map<String, Object>> queryResult;
		if (rep.isEdge()) {
			queryResult = new DataFetcher().fetchEdgeTypes(rep);
		} else {
			queryResult = new DataFetcher().fetchStationTypes(rep);
		}
		String url = ninjaBaseUrl + "/" + representation + "/";
		Map<String, Object> selfies;
		for (Map<String, Object> row : queryResult) {
			row.put("description", null);
			switch (rep) {
				case FLAT_NODE:
					row.put("self.stations", url + row.get("id"));
					row.put("self.stations+datatypes", url + row.get("id") + "/*");
					row.put("self.stations+datatypes+measurements", url + row.get("id") + "/*/latest");
				break;
				case TREE_NODE:
					selfies = new HashMap<>();
					selfies.put("stations", url + row.get("id"));
					selfies.put("stations+datatypes", url + row.get("id") + "/*");
					selfies.put("stations+datatypes+measurements", url + row.get("id") + "/*/latest");
					row.put("self", selfies);
				break;
				case FLAT_EDGE:
					row.put("self.edges", url + row.get("id"));
				break;
				case TREE_EDGE:
					selfies = new HashMap<>();
					selfies.put("edges", url + row.get("id"));
					row.put("self", selfies);
				break;
			}

		}
		return serializeJson(queryResult);
	}

	@GetMapping(value = "/{representation}/{stationTypes}", produces = "application/json;charset=UTF-8")
	public @ResponseBody String requestStations(@PathVariable final String representation,
			@PathVariable final String stationTypes,
			@RequestParam(value = "limit", required = false, defaultValue = DEFAULT_LIMIT) final Long limit,
			@RequestParam(value = "offset", required = false, defaultValue = DEFAULT_OFFSET) final Long offset,
			@RequestParam(value = "select", required = false) final String select,
			@RequestParam(value = "where", required = false) final String where,
			@RequestParam(value = "shownull", required = false, defaultValue = DEFAULT_SHOWNULL) final Boolean showNull,
			@RequestParam(value = "distinct", required = false, defaultValue = DEFAULT_DISTINCT) final Boolean distinct) {

		final Representation repr = Representation.get(representation);

		dataFetcher.setIgnoreNull(!showNull);
		dataFetcher.setLimit(limit);
		dataFetcher.setOffset(offset);
		dataFetcher.setWhere(where);
		dataFetcher.setSelect(select);
		dataFetcher.setDistinct(distinct);
		final List<Map<String, Object>> queryResult;
		final Map<String, Object> result;
		if (repr.isEdge()) {
			queryResult = dataFetcher.fetchEdges(stationTypes, repr);
			result = buildResult("edgetype", null, queryResult, offset, limit, repr, showNull);
		} else {
			queryResult = dataFetcher.fetchStations(stationTypes, repr);
			result = buildResult("stationtype", "station", queryResult, offset, limit, repr, showNull);
		}
		return serializeJson(result);
	}

	@GetMapping(value = "/{representation}/{stationTypes}/{dataTypes}", produces = "application/json;charset=UTF-8")
	public @ResponseBody String requestDataTypes(@PathVariable final String representation,
			@PathVariable final String stationTypes, @PathVariable final String dataTypes,
			@RequestParam(value = "limit", required = false, defaultValue = DEFAULT_LIMIT) final Long limit,
			@RequestParam(value = "offset", required = false, defaultValue = DEFAULT_OFFSET) final Long offset,
			@RequestParam(value = "select", required = false) final String select,
			@RequestParam(value = "where", required = false) final String where,
			@RequestParam(value = "shownull", required = false, defaultValue = DEFAULT_SHOWNULL) final Boolean showNull,
			@RequestParam(value = "distinct", required = false, defaultValue = DEFAULT_DISTINCT) final Boolean distinct) {

		final Representation repr = Representation.get(representation);

		final Authentication auth = SecurityContextHolder.getContext().getAuthentication();

		dataFetcher.setIgnoreNull(!showNull);
		dataFetcher.setLimit(limit);
		dataFetcher.setOffset(offset);
		dataFetcher.setWhere(where);
		dataFetcher.setSelect(select);
		dataFetcher.setRoles(getRolesFromAuthentication(auth));
		dataFetcher.setDistinct(distinct);

		final List<Map<String, Object>> queryResult = dataFetcher.fetchStationsAndTypes(stationTypes, dataTypes, repr);
		final Map<String, Object> result = buildResult("stationtype", "datatype", queryResult, offset, limit, repr, showNull);
		return serializeJson(result);
	}

	@GetMapping(value = "/{representation}/{stationTypes}/{dataTypes}/latest", produces = "application/json;charset=UTF-8")
	public @ResponseBody String requestMostRecent(@PathVariable final String representation,
			@PathVariable final String stationTypes, @PathVariable final String dataTypes,
			@RequestParam(value = "limit", required = false, defaultValue = DEFAULT_LIMIT) final Long limit,
			@RequestParam(value = "offset", required = false, defaultValue = DEFAULT_OFFSET) final Long offset,
			@RequestParam(value = "select", required = false) final String select,
			@RequestParam(value = "where", required = false) final String where,
			@RequestParam(value = "shownull", required = false, defaultValue = DEFAULT_SHOWNULL) final Boolean showNull,
			@RequestParam(value = "distinct", required = false, defaultValue = DEFAULT_DISTINCT) final Boolean distinct,
			@RequestParam(value = "timezone", required = false, defaultValue = DEFAULT_TIMEZONE) final String timeZone) {

		final Representation repr = Representation.get(representation);

		final Authentication auth = SecurityContextHolder.getContext().getAuthentication();

		dataFetcher.setIgnoreNull(!showNull);
		dataFetcher.setLimit(limit);
		dataFetcher.setOffset(offset);
		dataFetcher.setWhere(where);
		dataFetcher.setSelect(select);
		dataFetcher.setRoles(getRolesFromAuthentication(auth));
		dataFetcher.setDistinct(distinct);
		dataFetcher.setTimeZone(timeZone);

		final List<Map<String, Object>> queryResult = dataFetcher.fetchStationsTypesAndMeasurementHistory(stationTypes,
				dataTypes, null, null, repr);
		final Map<String, Object> result = buildResult("stationtype", null, queryResult, offset, limit, repr, showNull);
		return serializeJson(result);
	}

	@GetMapping(value = "/{representation}/{stationTypes}/{dataTypes}/{from}/{to}", produces = "application/json;charset=UTF-8")
	public @ResponseBody String requestHistory(@PathVariable final String representation,
			@PathVariable final String stationTypes, @PathVariable final String dataTypes,
			@PathVariable final String from, @PathVariable final String to,
			@RequestParam(value = "limit", required = false, defaultValue = DEFAULT_LIMIT) final Long limit,
			@RequestParam(value = "offset", required = false, defaultValue = DEFAULT_OFFSET) final Long offset,
			@RequestParam(value = "select", required = false) final String select,
			@RequestParam(value = "where", required = false) final String where,
			@RequestParam(value = "shownull", required = false, defaultValue = DEFAULT_SHOWNULL) final Boolean showNull,
			@RequestParam(value = "distinct", required = false, defaultValue = DEFAULT_DISTINCT) final Boolean distinct,
			@RequestParam(value = "timezone", required = false, defaultValue = DEFAULT_TIMEZONE) final String timeZone) {

		final Representation repr = Representation.get(representation);

		final Authentication auth = SecurityContextHolder.getContext().getAuthentication();

		final ZonedDateTime dateTimeFrom = getDateTime(from);
		final ZonedDateTime dateTimeTo = getDateTime(to);

		dataFetcher.setIgnoreNull(!showNull);
		dataFetcher.setLimit(limit);
		dataFetcher.setOffset(offset);
		dataFetcher.setWhere(where);
		dataFetcher.setSelect(select);
		dataFetcher.setRoles(getRolesFromAuthentication(auth));
		dataFetcher.setDistinct(distinct);
		dataFetcher.setTimeZone(timeZone);

		final List<Map<String, Object>> queryResult = dataFetcher.fetchStationsTypesAndMeasurementHistory(stationTypes,
				dataTypes, dateTimeFrom.toOffsetDateTime(), dateTimeTo.toOffsetDateTime(), repr);
		final Map<String, Object> result = buildResult("stationtype", null, queryResult, offset, limit, repr, showNull);
		return serializeJson(result);
	}

	private static ZonedDateTime getDateTime(final String dateString) {
		try {
			try {
				return ZonedDateTime.from(DATE_FORMAT.parse(dateString));
			} catch (DateTimeException e) {
				return LocalDateTime.from(DATE_FORMAT.parse(dateString)).atZone(ZoneId.of("Z"));
			}
		} catch (final DateTimeParseException e) {
			throw new SimpleException(ErrorCode.DATE_PARSE_ERROR, DATETIME_FORMAT_PATTERN.replace("'", ""),
					e.getMessage());
		}
	}

	private Map<String, Object> buildResult(String entryPoint, String exitPoint, final List<Map<String, Object>> queryResult, final long offset,
			final long limit, final Representation representation, final boolean showNull) {
		final Map<String, Object> result = new HashMap<>();
		result.put("offset", offset);
		result.put("limit", limit);
		switch(representation) {
			case FLAT_EDGE:
			case FLAT_NODE:
				result.put("data", queryResult);
				break;
			case TREE_NODE:
				result.put("data", ResultBuilder.build(entryPoint, exitPoint, showNull, queryResult,
					//FIXME use a static immutable schema everywhere
					new SelectExpansionConfig().getSelectExpansion().getSchema(), maxAllowedSizeInMB));
				break;
			case TREE_EDGE:
				result.put("data", ResultBuilder.build(entryPoint, exitPoint, showNull, queryResult,
					//FIXME use a static immutable schema everywhere
					new SelectExpansionConfig().getSelectExpansion().getSchema(), maxAllowedSizeInMB));
				break;
		}
		return result;
	}

	private static List<String> getRolesFromAuthentication(Authentication auth) {
		List<String> result = new ArrayList<>();
		if (auth instanceof KeycloakAuthenticationToken) {
			SimpleKeycloakAccount user = (SimpleKeycloakAccount) auth.getDetails();
			for (String role : user.getRoles()) {
				if (role.startsWith("BDP_")) {
					String cleanName = role.replaceFirst("BDP_", "");
					if (cleanName.equals("ADMIN")) {
						result.clear();
						result.add("ADMIN");
						return result;
					} else {
						result.add(cleanName);
					}
				}
			}
		}

		if (result.isEmpty() || !result.contains("GUEST")) {
			result.add("GUEST");
		}
		return result;
	}

	private static String serializeJson(Object whatever) {
		Map<String, Object> logging = new HashMap<>();
		Timer timer = new Timer();
		timer.start();
		String serialize = JsonStream.serialize(whatever);
		logging.put("serialization_time", Long.valueOf(timer.stop()));
		log.info("json_serialization", v("payload", logging));
		return serialize;
	}
}
