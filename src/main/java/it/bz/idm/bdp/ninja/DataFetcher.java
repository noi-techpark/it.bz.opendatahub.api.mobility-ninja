package it.bz.idm.bdp.ninja;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import it.bz.idm.bdp.ninja.config.SelectExpansionConfig;
import it.bz.idm.bdp.ninja.utils.FileUtils;
import it.bz.idm.bdp.ninja.utils.Representation;
import it.bz.idm.bdp.ninja.utils.Timer;
import it.bz.idm.bdp.ninja.utils.miniparser.Token;
import it.bz.idm.bdp.ninja.utils.querybuilder.QueryBuilder;
import it.bz.idm.bdp.ninja.utils.querybuilder.SelectExpansion;
import it.bz.idm.bdp.ninja.utils.querybuilder.WhereClauseTarget;
import it.bz.idm.bdp.ninja.utils.queryexecutor.QueryExecutor;
import it.bz.idm.bdp.ninja.utils.simpleexception.ErrorCodeInterface;
import it.bz.idm.bdp.ninja.utils.simpleexception.SimpleException;

import static net.logstash.logback.argument.StructuredArguments.v;

@Component
public class DataFetcher {

	private static final Logger log = LoggerFactory.getLogger(DataFetcher.class);

	public enum ErrorCode implements ErrorCodeInterface {
		FUNCTIONS_AND_JSON_MIX_NOT_ALLOWED ("You have used both functions and json selectors in SELECT and/or WHERE. That is not supported yet!"),
		WRONG_TIMEZONE ("'%s' is not a valid time zone understandable by java.time.ZoneId."),
		WHERE_WRONG_DATA_TYPE ("'%s' can only be used with NULL, NUMBERS or STRINGS: '%s' given."),
		METHOD_NOT_ALLOWED_FOR_NODE_REPR ("Method '%s' not allowed with NODE representation."),
		METHOD_NOT_ALLOWED_FOR_EDGE_REPR ("Method '%s' not allowed with EDGE representation.");

		private final String msg;

		ErrorCode(String msg) {
			this.msg = msg;
		}

		@Override
		public String getMsg() {
			return "DATA FETCHING ERROR: " + msg;
		}
	}

	private long limit;
	private long offset;
	private List<String> roles;
	private boolean ignoreNull;
	private String select;
	private String where;
	private boolean distinct;
	private Map<String, String> aclWhereClauses = new HashMap<>();
	private String timeZone = "UTC";

	public List<Map<String, Object>> fetchStations(String stationTypeList, final Representation representation) {
		if (representation.isEdge()) {
			throw new SimpleException(ErrorCode.METHOD_NOT_ALLOWED_FOR_EDGE_REPR, "fetchStations");
		}
		Set<String> stationTypeSet = QueryBuilder.csvToSet(stationTypeList);
		Timer timer = new Timer();

		timer.start();
		SelectExpansion se = new SelectExpansionConfig().getSelectExpansion();
		QueryBuilder query = QueryBuilder
				.init(se, select, where, distinct, "station", "parent")
				.addSql("select")
				.addSqlIf("distinct", distinct)
				.addSqlIf("s.stationtype as _stationtype, s.stationcode as _stationcode", !representation.isFlat())
				.expandSelectPrefix(", ", !representation.isFlat())
				.addSql("from station s")
				.addSqlIfAlias("left join metadata m on m.id = s.meta_data_id", "smetadata")
				.addSqlIfDefinition("left join station p on s.parent_id = p.id", "parent")
				.addSqlIfAlias("left join metadata pm on pm.id = p.meta_data_id", "pmetadata")
				.addSql("where s.available = true")
				.addSqlIfDefinition("and (p.id is null or p.available = true)", "parent")
				.setParameterIfNotEmptyAnd("stationtypes", stationTypeSet, "AND s.stationtype in (:stationtypes)", !stationTypeSet.contains("*"))
				.expandWhere()
				.expandGroupByIf("_stationtype, _stationcode", !representation.isFlat())
				.addSqlIf("order by _stationtype, _stationcode", !representation.isFlat())
				.addLimit(limit)
				.addOffset(offset);
		long timeBuild = timer.stop();

		// We need null values while tree building. We remove them during the output generation
		timer.start();
		List<Map<String, Object>> queryResult = QueryExecutor
				.init()
				.addParameters(query.getParameters())
				.build(query.getSql(), ignoreNull && representation.isFlat(), timeZone);
		long timeExec = timer.stop();

		log.debug(queryResult.toString());

		Map<String, Object> logData = new HashMap<>();
		logData.put("stationTypes", stationTypeSet);
		logStats("fetchStations", representation, queryResult.size(), timeBuild, timeExec, query.getSql(), logData);

		return queryResult;
	}

	public List<Map<String, Object>> fetchStationsTypesAndMeasurementHistory(String stationTypeList, String dataTypeList, OffsetDateTime from, OffsetDateTime to, final Representation representation) {

		if (representation.isEdge()) {
			throw new SimpleException(ErrorCode.METHOD_NOT_ALLOWED_FOR_EDGE_REPR, "fetchStationsTypesAndMeasurement(History)");
		}

		Set<String> stationTypeSet = QueryBuilder.csvToSet(stationTypeList);
		Set<String> dataTypeSet = QueryBuilder.csvToSet(dataTypeList);

		Timer timer = new Timer();

		timer.start();
		SelectExpansion se = new SelectExpansionConfig().getSelectExpansion();
		QueryBuilder query = QueryBuilder
				.init(se, select, where, distinct, "station", "parent", "measurementdouble", "measurement", "datatype");

		List<WhereClauseTarget> mvalueTokens = query.getSelectExpansion().getUsedAliasesInWhere().get("mvalue");
		WhereClauseTarget mvalueTarget = mvalueTokens == null ? null : mvalueTokens.get(0);
		Token mvalueToken = mvalueTarget == null ? null : mvalueTarget.getValue(0); // FIXME we ignore lists here, that might have more than 1 value

		/* We support functions only for double-typed measurements, so do not append a measurement-string query if any
		 */
		boolean hasFunctions = query.getSelectExpansion().hasFunctions();
		boolean hasJsonSel = mvalueTarget != null && mvalueTarget.hasJson();
		boolean useMeasurementDouble = (mvalueToken == null || Token.is(mvalueToken, "number") || Token.is(mvalueToken, "null")) && !hasJsonSel;
		boolean useMeasurementString = (mvalueToken == null || Token.is(mvalueToken, "string") || Token.is(mvalueToken, "null")) && !hasFunctions && !hasJsonSel;
		boolean useMeasurementJson = (mvalueToken == null || Token.is(mvalueToken, "string") || Token.is(mvalueToken, "null")) && !hasFunctions;

		if (!useMeasurementDouble && !useMeasurementString && !useMeasurementJson) {
			throw new SimpleException(ErrorCode.FUNCTIONS_AND_JSON_MIX_NOT_ALLOWED);
		}

		String aclWhereclause = getAclWhereClause(roles);

		if (useMeasurementDouble) {
			query.addSql("select")
				 .addSqlIf("distinct", distinct)
				 .addSqlIf("s.stationtype as _stationtype, s.stationcode as _stationcode, t.cname as _datatypename", !representation.isFlat())
				 .expandSelectPrefix(", ", !representation.isFlat())
				 .addSqlIf("from measurementhistory me", from != null || to != null)
				 .addSqlIf("from measurement me", from == null && to == null)
				 .addSql("join station s on me.station_id = s.id")
				 .addSqlIfAlias("left join metadata m on m.id = s.meta_data_id", "smetadata")
				 .addSqlIfDefinition("left join station p on s.parent_id = p.id", "parent")
				 .addSqlIfAlias("left join metadata pm on pm.id = p.meta_data_id", "pmetadata")
				 .addSql("join type t on me.type_id = t.id")
				 .addSqlIfAlias("left join type_metadata tm on tm.id = t.meta_data_id", "tmetadata")
				 .addSql("where s.available = true")
				 .addSqlIfNotNull("and", aclWhereclause)
				 .addSqlIfNotNull(aclWhereclause, aclWhereclause)
				 .addSqlIfDefinition("and (p.id is null or p.available = true)", "parent")
				 .setParameterIfNotEmptyAnd("stationtypes", stationTypeSet, "and s.stationtype in (:stationtypes)", !stationTypeSet.contains("*"))
				 .setParameterIfNotEmptyAnd("datatypes", dataTypeSet, "and t.cname in (:datatypes)", !dataTypeSet.contains("*"))
				 .setParameterIfNotNull("from", from, "and timestamp >= :from::timestamptz")
				 .setParameterIfNotNull("to", to, "and timestamp < :to::timestamptz")
				 .setParameter("roles", roles)
				 .expandWhere()
				 .expandGroupByIf("_stationtype, _stationcode, _datatypename", !representation.isFlat());
		}

		if (useMeasurementDouble && useMeasurementString) {
			query.addSql("union all");
		}

		if (useMeasurementString) {
			query.reset(select, where, distinct, "station", "parent", "measurementstring", "measurement", "datatype")
				 .addSql("select")
				 .addSqlIf("distinct", distinct)
				 .addSqlIf("s.stationtype as _stationtype, s.stationcode as _stationcode, t.cname as _datatypename", !representation.isFlat())
				 .expandSelectPrefix(", ", !representation.isFlat())
				 .addSqlIf("from measurementstringhistory me", from != null || to != null)
				 .addSqlIf("from measurementstring me", from == null && to == null)
				 .addSql("join station s on me.station_id = s.id")
				 .addSqlIfAlias("left join metadata m on m.id = s.meta_data_id", "smetadata")
				 .addSqlIfDefinition("left join station p on s.parent_id = p.id", "parent")
				 .addSqlIfAlias("left join metadata pm on pm.id = p.meta_data_id", "pmetadata")
				 .addSql("join type t on me.type_id = t.id")
				 .addSqlIfAlias("left join type_metadata tm on tm.id = t.meta_data_id", "tmetadata")
				 .addSql("where s.available = true")
				 .addSqlIfNotNull("and", aclWhereclause)
				 .addSqlIfNotNull(aclWhereclause, aclWhereclause)
				 .addSqlIfDefinition("and (p.id is null or p.available = true)", "parent")
				 .setParameterIfNotEmptyAnd("stationtypes", stationTypeSet, "and s.stationtype in (:stationtypes)", !stationTypeSet.contains("*"))
				 .setParameterIfNotEmptyAnd("datatypes", dataTypeSet, "and t.cname in (:datatypes)", !dataTypeSet.contains("*"))
				 .setParameterIfNotNull("from", from, "and timestamp >= :from::timestamptz")
				 .setParameterIfNotNull("to", to, "and timestamp < :to::timestamptz")
				 .setParameter("roles", roles)
				 .expandWhere()
				 .expandGroupByIf("_stationtype, _stationcode, _datatypename", !representation.isFlat());
		}

		if ((useMeasurementDouble || useMeasurementString) && useMeasurementJson) {
			query.addSql("union all");
		}

		if (useMeasurementJson) {
			query.reset(select, where, distinct, "station", "parent", "measurementjson", "measurement", "datatype")
				 .addSql("select")
				 .addSqlIf("distinct", distinct)
				 .addSqlIf("s.stationtype as _stationtype, s.stationcode as _stationcode, t.cname as _datatypename", !representation.isFlat())
				 .expandSelectPrefix(", ", !representation.isFlat())
				 .addSqlIf("from measurementjsonhistory me", from != null || to != null)
				 .addSqlIf("from measurementjson me", from == null && to == null)
				 .addSql("join station s on me.station_id = s.id")
				 .addSqlIfAlias("left join metadata m on m.id = s.meta_data_id", "smetadata")
				 .addSqlIfDefinition("left join station p on s.parent_id = p.id", "parent")
				 .addSqlIfAlias("left join metadata pm on pm.id = p.meta_data_id", "pmetadata")
				 .addSql("join type t on me.type_id = t.id")
				 .addSqlIfAlias("left join type_metadata tm on tm.id = t.meta_data_id", "tmetadata")
				 .addSql("where s.available = true")
				 .addSqlIfNotNull("and", aclWhereclause)
				 .addSqlIfNotNull(aclWhereclause, aclWhereclause)
				 .addSqlIfDefinition("and (p.id is null or p.available = true)", "parent")
				 .setParameterIfNotEmptyAnd("stationtypes", stationTypeSet, "and s.stationtype in (:stationtypes)", !stationTypeSet.contains("*"))
				 .setParameterIfNotEmptyAnd("datatypes", dataTypeSet, "and t.cname in (:datatypes)", !dataTypeSet.contains("*"))
				 .setParameterIfNotNull("from", from, "and timestamp >= :from::timestamptz")
				 .setParameterIfNotNull("to", to, "and timestamp < :to::timestamptz")
				 .setParameter("roles", roles)
				 .expandWhere()
				 .expandGroupByIf("_stationtype, _stationcode, _datatypename", !representation.isFlat());
		}

		if (mvalueToken != null && !mvalueToken.is("string") && !mvalueToken.is("number") && !mvalueToken.is("null")) {
			throw new SimpleException(ErrorCode.WHERE_WRONG_DATA_TYPE, "mvalue", mvalueToken.getName());
		}

		query.addSqlIf("order by _stationtype, _stationcode, _datatypename", !representation.isFlat())
			 .addLimit(limit)
			 .addOffset(offset);
		long timeBuild = timer.stop();

		// We need null values while tree building. We remove them during the output generation
		timer.start();
		List<Map<String, Object>> queryResult = QueryExecutor
				.init()
				.addParameters(query.getParameters())
				.build(query.getSql(), ignoreNull && representation.isFlat(), timeZone);
		long timeExec = timer.stop();

		Map<String, Object> logData = new HashMap<>();
		logData.put("stationTypes", stationTypeSet);
		logData.put("dataTypes", dataTypeSet);
		String command = (from == null && to == null) ? "fetchMeasurement" : "fetchMeasurementHistory";
		logStats(command, representation, queryResult.size(), timeBuild, timeExec, query.getSql(), logData);

		return queryResult;
	}

	private String getAclWhereClause(List<String> roles) {
		if (aclWhereClauses.isEmpty()) {
			for (String filename : FileUtils.listFiles("acl-rules")) {
				if (filename.equals("ADMIN.sql")) {
					continue;
				}

				if (filename.endsWith(".sql")) {
					String sql = FileUtils
						.loadFile("acl-rules/" + filename)
						.replaceAll("--.*\n", "\n")
						.replaceAll("//.*\n", "\n");
					aclWhereClauses.put(filename.substring(0, filename.length() - 4).toUpperCase(), sql);
				}
			}
		}

		if (roles.contains("ADMIN")) {
			return null;
		}

		StringJoiner sj = new StringJoiner(" or ", "(", ")");

		for (String role : roles) {
			sj.add(aclWhereClauses.get(role));
		}

		return sj.toString();
	}

	public List<Map<String, Object>> fetchStationsAndTypes(String stationTypeList, String dataTypeList,
			final Representation representation) {

		if (representation.isEdge()) {
			throw new SimpleException(ErrorCode.METHOD_NOT_ALLOWED_FOR_EDGE_REPR, "fetchStationsAndTypes");
		}

		Set<String> stationTypeSet = QueryBuilder.csvToSet(stationTypeList);
		Set<String> dataTypeSet = QueryBuilder.csvToSet(dataTypeList);

		Timer timer = new Timer();

		timer.start();
		SelectExpansion se = new SelectExpansionConfig().getSelectExpansion();
		QueryBuilder query = QueryBuilder
				.init(se, select, where, distinct, "station", "parent", "datatype");

		// FIXME This is redundant code, also copy7pasted into another function... fix it
		List<WhereClauseTarget> mvalueTokens = query.getSelectExpansion().getUsedAliasesInWhere().get("mvalue");
		WhereClauseTarget mvalueTarget = mvalueTokens == null ? null : mvalueTokens.get(0);
		Token mvalueToken = mvalueTarget == null ? null : mvalueTarget.getValue(0);

		/* We support functions only for double-typed measurements, so do not append a measurement-string query if any
		 */
		boolean hasFunctions = query.getSelectExpansion().hasFunctions();
		boolean hasJsonSel = mvalueTarget != null && mvalueTarget.hasJson();
		boolean useMeasurementDouble = mvalueToken == null || Token.is(mvalueToken, "number") || Token.is(mvalueToken, "null") && !hasJsonSel;
		boolean useMeasurementString = (mvalueToken == null || Token.is(mvalueToken, "string") || Token.is(mvalueToken, "null")) && !hasFunctions && !hasJsonSel;
		boolean useMeasurementJson = (mvalueToken == null || Token.is(mvalueToken, "json") || Token.is(mvalueToken, "null")) && !hasFunctions;

		if (!useMeasurementDouble && !useMeasurementString && !useMeasurementJson) {
			throw new SimpleException(ErrorCode.FUNCTIONS_AND_JSON_MIX_NOT_ALLOWED);
		}

		if (useMeasurementDouble) {
			query.addSql("select")
				 .addSqlIf("distinct", distinct)
				 .addSqlIf("s.stationtype as _stationtype, s.stationcode as _stationcode, t.cname as _datatypename", !representation.isFlat())
				 .expandSelectPrefix(", ", !representation.isFlat())
				 .addSql("from measurement me")
				 .addSql("join station s on me.station_id = s.id")
				 .addSqlIfAlias("left join metadata m on m.id = s.meta_data_id", "smetadata")
				 .addSqlIfDefinition("left join station p on s.parent_id = p.id", "parent")
				 .addSqlIfAlias("left join metadata pm on pm.id = p.meta_data_id", "pmetadata")
				 .addSql("join type t on me.type_id = t.id")
				 .addSqlIfAlias("left join type_metadata tm on tm.id = t.meta_data_id", "tmetadata")
				 .addSql("where s.available = true")
				 .addSqlIfDefinition("and (p.id is null or p.available = true)", "parent")
				 .setParameterIfNotEmptyAnd("stationtypes", stationTypeSet, "and s.stationtype in (:stationtypes)", !stationTypeSet.contains("*"))
				 .setParameterIfNotEmptyAnd("datatypes", dataTypeSet, "and t.cname in (:datatypes)", !dataTypeSet.contains("*"))
				 .setParameter("roles", roles)
				 .expandWhere()
				 .expandGroupByIf("_stationtype, _stationcode, _datatypename", !representation.isFlat());
		}

		if (useMeasurementDouble && useMeasurementString) {
			query.addSql("union all");
		}

		if (useMeasurementString) {
			query.reset(select, where, distinct, "station", "parent", "datatype")
				 .addSql("select")
				 .addSqlIf("distinct", distinct)
				 .addSqlIf("s.stationtype as _stationtype, s.stationcode as _stationcode, t.cname as _datatypename", !representation.isFlat())
				 .expandSelectPrefix(", ", !representation.isFlat())
				 .addSql("from measurementstring me")
				 .addSql("join station s on me.station_id = s.id")
				 .addSqlIfAlias("left join metadata m on m.id = s.meta_data_id", "smetadata")
				 .addSqlIfDefinition("left join station p on s.parent_id = p.id", "parent")
				 .addSqlIfAlias("left join metadata pm on pm.id = p.meta_data_id", "pmetadata")
				 .addSql("join type t on me.type_id = t.id")
				 .addSqlIfAlias("left join type_metadata tm on tm.id = t.meta_data_id", "tmetadata")
				 .addSql("where s.available = true")
				 .addSqlIfDefinition("and (p.id is null or p.available = true)", "parent")
				 .setParameterIfNotEmptyAnd("stationtypes", stationTypeSet, "and s.stationtype in (:stationtypes)", !stationTypeSet.contains("*"))
				 .setParameterIfNotEmptyAnd("datatypes", dataTypeSet, "and t.cname in (:datatypes)", !dataTypeSet.contains("*"))
				 .setParameter("roles", roles)
				 .expandWhere()
				 .expandGroupByIf("_stationtype, _stationcode, _datatypename", !representation.isFlat());
		}

		if ((useMeasurementDouble || useMeasurementString) && useMeasurementJson) {
			query.addSql("union all");
		}

		if (useMeasurementJson) {
			query.reset(select, where, distinct, "station", "parent", "datatype")
				 .addSql("select")
				 .addSqlIf("distinct", distinct)
				 .addSqlIf("s.stationtype as _stationtype, s.stationcode as _stationcode, t.cname as _datatypename", !representation.isFlat())
				 .expandSelectPrefix(", ", !representation.isFlat())
				 .addSql("from measurementjson me")
				 .addSql("join station s on me.station_id = s.id")
				 .addSqlIfAlias("left join metadata m on m.id = s.meta_data_id", "smetadata")
				 .addSqlIfDefinition("left join station p on s.parent_id = p.id", "parent")
				 .addSqlIfAlias("left join metadata pm on pm.id = p.meta_data_id", "pmetadata")
				 .addSql("join type t on me.type_id = t.id")
				 .addSqlIfAlias("left join type_metadata tm on tm.id = t.meta_data_id", "tmetadata")
				 .addSql("where s.available = true")
				 .addSqlIfDefinition("and (p.id is null or p.available = true)", "parent")
				 .setParameterIfNotEmptyAnd("stationtypes", stationTypeSet, "and s.stationtype in (:stationtypes)", !stationTypeSet.contains("*"))
				 .setParameterIfNotEmptyAnd("datatypes", dataTypeSet, "and t.cname in (:datatypes)", !dataTypeSet.contains("*"))
				 .setParameter("roles", roles)
				 .expandWhere()
				 .expandGroupByIf("_stationtype, _stationcode, _datatypename", !representation.isFlat());
		}

		if (mvalueToken != null && !mvalueToken.is("string") && !mvalueToken.is("number") && !mvalueToken.is("null")) {
			throw new SimpleException(ErrorCode.WHERE_WRONG_DATA_TYPE, "mvalue", mvalueToken.getName());
		}

		query.addSqlIf("order by _stationtype, _stationcode, _datatypename", !representation.isFlat())
			 .addLimit(limit)
			 .addOffset(offset);
		long timeBuild = timer.stop();

		// We need null values while tree building. We remove them during the output generation
		timer.start();
		List<Map<String, Object>> queryResult = QueryExecutor
				.init()
				.addParameters(query.getParameters())
				.build(query.getSql(), ignoreNull && representation.isFlat(), timeZone);
		long timeExec = timer.stop();

		Map<String, Object> logData = new HashMap<>();
		logData.put("stationTypes", stationTypeSet);
		logData.put("dataTypes", dataTypeSet);
		logStats("fetchStationsAndTypes", representation, queryResult.size(), timeBuild, timeExec, query.getSql(), logData);

		return queryResult;
	}

	public List<Map<String, Object>> fetchStationTypes(final Representation representation) {

		if (representation.isEdge()) {
			throw new SimpleException(ErrorCode.METHOD_NOT_ALLOWED_FOR_EDGE_REPR, "fetchStationTypes");
		}

		Timer timer = new Timer();

		String sql = "select distinct stationtype as id from station s where s.available = true order by 1";
		timer.start();
		List<Map<String, Object>> queryResult = QueryExecutor
				.init()
				.build(sql, true, timeZone);
		long timeExec = timer.stop();

		logStats("fetchStationTypes", representation, queryResult.size(), 0, timeExec, sql, null);

		return queryResult;
	}

	public List<Map<String, Object>> fetchEdgeTypes(final Representation representation) {

		if (!representation.isEdge()) {
			throw new SimpleException(ErrorCode.METHOD_NOT_ALLOWED_FOR_NODE_REPR, "fetchEdgeTypes");
		}

		Timer timer = new Timer();

		String sql = "select distinct stationtype as id from edge e join station s on e.edge_data_id = s.id where s.available = true order by 1";
		timer.start();
		List<Map<String, Object>> queryResult = QueryExecutor
				.init()
				.build(sql, true, timeZone);
		long timeExec = timer.stop();

		logStats("fetchEdgeTypes", representation, queryResult.size(), 0, timeExec, sql, null);

		return queryResult;
	}

	public List<Map<String, Object>> fetchEdges(String stationTypeList, final Representation representation) {

		if (!representation.isEdge()) {
			throw new SimpleException(ErrorCode.METHOD_NOT_ALLOWED_FOR_NODE_REPR, "fetchEdges");
		}

		Set<String> stationTypeSet = QueryBuilder.csvToSet(stationTypeList);

		Timer timer = new Timer();

		timer.start();
		SelectExpansion se = new SelectExpansionConfig().getSelectExpansion();
		QueryBuilder query = QueryBuilder
				.init(se, select, where, distinct, "edge", "stationbegin", "stationend")
				.addSql("select")
				.addSqlIf("distinct", distinct)
				.addSqlIf("i.stationtype as _edgetype, i.stationcode as _edgecode", !representation.isFlat())
				.expandSelectPrefix(", ", !representation.isFlat())
				.addSql("from edge e")
				.addSql("join station i on e.edge_data_id = i.id")
				.addSqlIfDefinition("left join station o on e.origin_id = o.id", "stationbegin")
				.addSqlIfDefinition("left join station d on e.destination_id = d.id", "stationend")
				.addSql("where i.available = true")
				.addSqlIfDefinition("and (o.available is null or o.available = true)", "stationbegin")
				.addSqlIfDefinition("and (d.available is null or d.available = true)", "stationend")
				.setParameterIfNotEmptyAnd("stationtypes", stationTypeSet, "AND i.stationtype in (:stationtypes)", !stationTypeSet.contains("*"))
				.expandWhere()
				.expandGroupByIf("_edgetype, _edgecode", !representation.isFlat())
				.addSqlIf("order by _edgetype, _edgecode", !representation.isFlat())
				.addLimit(limit)
				.addOffset(offset);
		long timeBuild = timer.stop();

		log.debug(query.getSql());

		// We need null values while tree building. We remove them during the output generation
		timer.start();
		List<Map<String, Object>> queryResult = QueryExecutor
				.init()
				.addParameters(query.getParameters())
				.build(query.getSql(), ignoreNull && representation.isFlat(), timeZone);
		long timeExec = timer.stop();

		log.trace(queryResult.toString());

		Map<String, Object> logData = new HashMap<>();
		logData.put("stationTypes", stationTypeSet);
		logStats("fetchEdges", representation, queryResult.size(), timeBuild, timeExec, query.getSql(), logData);

		return queryResult;
	}

	private void logStats(final String command, final Representation repr, long resultCount, long buildTime, long executionTime, final String sql, Map<String, Object> extraData) {
		Map<String, Object> logging = new HashMap<>();
		logging.put("command", command);
		logging.put("representation", repr);
		logging.put("result_count", resultCount);
		logging.put("build_time", Long.valueOf(buildTime));
		logging.put("execution_time", Long.valueOf(executionTime));
		logging.put("full_time", Long.valueOf(executionTime + buildTime));
		logging.put("sql", sql);
		if (extraData != null) {
			logging.putAll(extraData);
		}
		log.info("query_execution", v("payload", logging));
		log.debug(sql);
	}

	public void setLimit(long limit) {
		this.limit = limit;
	}

	public void setOffset(long offset) {
		this.offset = offset;
	}

	public void setRoles(List<String> roles) {
		if (roles == null) {
			roles = new ArrayList<>();
			roles.add("GUEST");
		}
		this.roles = roles;
	}

	public void setIgnoreNull(boolean ignoreNull) {
		this.ignoreNull = ignoreNull;
	}

	public void setSelect(String select) {
		/* No need to check for null, since the QueryBuilder
		 * will handle this with a "SELECT * ..."
		 */
		this.select = select;
	}

	public void setWhere(String where) {
		this.where = where;
	}

	public void setDistinct(Boolean distinct) {
		this.distinct = distinct;
	}

	public void setTimeZone(String timeZone) {
		this.timeZone = timeZone;
	}
}
