package it.bz.idm.bdp.ninja.utils.resultbuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicLong;

import it.bz.idm.bdp.ninja.config.SelectExpansionConfig;
import it.bz.idm.bdp.ninja.utils.querybuilder.Schema;
import it.bz.idm.bdp.ninja.utils.querybuilder.Target;
import it.bz.idm.bdp.ninja.utils.querybuilder.TargetDefList;
import it.bz.idm.bdp.ninja.utils.simpleexception.ErrorCodeInterface;
import it.bz.idm.bdp.ninja.utils.simpleexception.SimpleException;

public class ResultBuilder {

	public enum ErrorCode implements ErrorCodeInterface {
		RESPONSE_SIZE("Response size of %d MB exceeded. Please rephrase your request. Use a flat representation, WHERE, SELECT, LIMIT with OFFSET or a narrow time interval."),
		WRONG_TREE_BUILDING_KEY_TYPE("The column '%s' used to build the TREE representation must be of type STRING");

		private final String msg;

		ErrorCode(final String msg) {
			this.msg = msg;
		}

		@Override
		public String getMsg() {
			return "TREE BUILDING: " + msg;
		}
	}

	@SuppressWarnings("unchecked")
	public static Map<String, Object> build(boolean ignoreNull, List<Map<String, Object>> queryResult, Schema schema, List<String> hierarchy, int maxAllowedSizeInMB) {
		AtomicLong size = new AtomicLong(0);

		long maxAllowedSize = maxAllowedSizeInMB > 0 ? maxAllowedSizeInMB * 1000000 : 0;

		if (queryResult == null || queryResult.isEmpty()) {
			return new HashMap<>();
		}

		List<String> currValues = new ArrayList<>();
		List<String> prevValues = new ArrayList<>();

		for (int i = 0; i < hierarchy.size(); i++) {
			prevValues.add("");
		}

		Map<String, Object> stationTypes = new HashMap<>();
		Map<String, Object> stations = null;
		Map<String, Object> datatypes = null;
		List<Object> measurements = null;

		Map<String, Object> stationType = null;
		Map<String, Object> station = null;
		Map<String, Object> parent = null;
		Map<String, Object> datatype = null;
		Map<String, Object> measurement = null;
		Map<String, Object> mvalueAndFunctions = null;

		for (Map<String, Object> rec : queryResult) {

			currValues.clear();
			int i = 0;
			boolean levelSet = false;
			int renewLevel = hierarchy.size();
			for (String alias : hierarchy) {
				String value = (String) rec.get(alias);
				if (value == null) {
					throw new RuntimeException(alias + " not found in select. Unable to build hierarchy.");
				}
				currValues.add(value);
				if (!levelSet && !value.equals(prevValues.get(i))) {
					renewLevel = i;
					levelSet = true;
				}
				i++;
			}

			switch (renewLevel) {
				case 0:
					stationType = makeObj(schema, rec, "stationtype", false, size);
				case 1:
					station = makeObj(schema, rec, "station", ignoreNull, size);
					parent = makeObj(schema, rec, "parent", ignoreNull, size);
				case 2:
					if (hierarchy.size() > 2) {
						datatype = makeObj(schema, rec, "datatype", ignoreNull, size);
					}
				default:
					if (hierarchy.size() > 3) {
						measurement = makeObj(schema, rec, "measurement", ignoreNull, size);

						/*
						 * We only need one measurement-type here ("measurementdouble"), since we look
						 * only for final names, that is we do not consider mvalue_double and
						 * mvalue_string here, but reduce both before handling to mvalue. See makeObj
						 * for details.
						 */
						mvalueAndFunctions = makeObj(schema, rec, "measurementdouble", ignoreNull, size);

						for (Entry<String, Object> entry : mvalueAndFunctions.entrySet()) {
							if (entry.getValue() != null || !ignoreNull) {
								measurement.put(entry.getKey(), entry.getValue());
							}
						}
					}
			}

			if (measurement != null && !measurement.isEmpty()) {
				measurements = (List<Object>) datatype.get("tmeasurements");
				if (measurements == null) {
					measurements = new ArrayList<>();
					datatype.put("tmeasurements", measurements);
				}
				measurements.add(measurement);
			}
			if (datatype != null && !datatype.isEmpty()) {
				datatypes = (Map<String, Object>) station.get("sdatatypes");
				if (datatypes == null) {
					datatypes = new HashMap<>();
					station.put("sdatatypes", datatypes);
				}
				datatypes.put(currValues.get(2), datatype);
			}
			if (!parent.isEmpty()) {
				station.put("sparent", parent);
			}
			if (!station.isEmpty()) {
				stations = (Map<String, Object>) stationType.get("stations");
				if (stations == null) {
					stations = new HashMap<>();
					stationType.put("stations", stations);
				}
				stations.put(currValues.get(1), station);
			}
			if (!stationType.isEmpty()) {
				stationTypes.put(currValues.get(0), stationType);
			}

			prevValues.clear();
			prevValues.addAll(currValues);

			if (maxAllowedSize > 0 && maxAllowedSize < size.get()) {
				throw new SimpleException(ErrorCode.RESPONSE_SIZE, maxAllowedSizeInMB);
			}
		}
		System.out.println(size);
		return stationTypes;
	}

	public static int calculateLevel(Map<String, Object> rec, List<String> hierarchy, List<String> prevValues, List<String> currValues) {

		if (prevValues.isEmpty()) {
			for (int i = 0; i < hierarchy.size(); i++) {
				prevValues.add("");
			}
		}

		currValues.clear();
		int i = 0;
		boolean levelSet = false;
		int renewLevel = hierarchy.size();
		for (String colname : hierarchy) {
			String value = (String) rec.get(colname);
			if (value == null) {
				throw new RuntimeException(colname + " not found in select. Unable to build hierarchy.");
			}
			currValues.add(value);
			if (!levelSet && !value.equals(prevValues.get(i))) {
				renewLevel = i;
				levelSet = true;
			}
			i++;
		}
		return renewLevel;
	}

	@SuppressWarnings("unchecked")
	public static Map<String, Object> buildGeneric(String entryPoint, String exitPoint, boolean ignoreNull, List<Map<String, Object>> queryResult, Schema schema, int maxAllowedSizeInMB) {
		AtomicLong size = new AtomicLong(0);
		long maxAllowedSize = maxAllowedSizeInMB > 0 ? maxAllowedSizeInMB * 1000000 : 0;

		if (queryResult == null || queryResult.isEmpty()) {
			return new HashMap<>();
		}

		List<String> currValues = new ArrayList<>();
		List<String> prevValues = new ArrayList<>();
		List<String> hierarchyTriggerKeys = schema.getHierarchyTriggerKeys(entryPoint, exitPoint);
		int maxLevel = hierarchyTriggerKeys.size();
		Map<String, List<Target>> catalog = new HashMap<>();
		Map<String, Object> result = new HashMap<>();

		// Should be present inside the definition, just entrypoint needed
		List<List<String>> hierarchy = schema.getHierarchy(entryPoint, exitPoint);


		Map<String, Map<String, Object>> cache = new HashMap<>();
		Map<String, Object> firstResultRecord = queryResult.get(0);

		for (String key : hierarchyTriggerKeys) {
			if (firstResultRecord.get(key) instanceof String)
				continue;

			throw new SimpleException(ErrorCode.WRONG_TREE_BUILDING_KEY_TYPE, key);
		}


		// create catalog of Targets, since each record in this result set contains exactly the same names
		for (List<String> level : hierarchy) {
			for (String targetDefListName : level) {
				Set<String> targetDefNames = schema.getOrNull(targetDefListName).getFinalNames();
				List<Target> currentTargetList = new ArrayList<>();
				for (String targetName : firstResultRecord.keySet()) {
					Target target = new Target(targetName);
					if (targetDefNames.contains(target.getName())) {
						currentTargetList.add(target);
						catalog.putIfAbsent(targetDefListName, currentTargetList);
						cache.putIfAbsent(targetDefListName, new HashMap<>());
					}
				}
			}
		}

		// We should check for all these prerequisites before starting the record loop to generate the result set
		// we can also limit the possible levels, if we see that it stops always at 2 (for datatypes, not here in edges, just an example)
		// and that the first two levels are mandatory, so it must never be lower than those

		for (Map<String, Object> rec : queryResult) {

			int renewLevel = calculateLevel(rec, hierarchyTriggerKeys, prevValues, currValues);

			for (int i = renewLevel; i <= maxLevel; i++) {
				for (String targetDefListName : hierarchy.get(i)) {
					Map<String, Object> curObject = makeObj2(catalog.get(targetDefListName), rec, ignoreNull, size);
					cache.put(targetDefListName, curObject);
				}
			}

			for (int i = maxLevel; i >= renewLevel; i--) {
				for (String targetDefListName : hierarchy.get(i)) {
					Map<String, Object> curObject = cache.get(targetDefListName);
					if (curObject == null || curObject.isEmpty()) {
						continue;
					}
					LookUp lookup = schema.get(targetDefListName).getLookUp();
					Map<String, Object> parent = cache.get(lookup.getParentDefListName());
					if (parent == null) {
						parent = result;
					}
					String mapTypeValue = (String) rec.get(lookup.getMapTypeKey());
					switch (lookup.getType()) {
						case INLINE:
							parent.put(lookup.getParentTargetName(), curObject);
							break;
						case MAP:
							if (lookup.getParentTargetName() == null) {
								parent.put(mapTypeValue, curObject);
								break;
							}

							Map<String, Object> parentSub = (Map<String, Object>) parent.getOrDefault(lookup.getParentTargetName(), new TreeMap<>());
							if (parentSub.isEmpty()) {
								parent.put(lookup.getParentTargetName(), parentSub);
								parentSub.put(mapTypeValue, curObject);
							} else {
								parentSub.putIfAbsent(mapTypeValue, curObject);
							}

							break;
						case LIST:
							List<Object> newList = (List<Object>) parent.getOrDefault(lookup.getParentTargetName(), new ArrayList<>());
							if (newList.isEmpty()) {
								parent.put(lookup.getParentTargetName(), newList);
							}
							newList.add(curObject);
							break;
					}
				}
			}

			prevValues.clear();
			prevValues.addAll(currValues);

			if (maxAllowedSize > 0 && maxAllowedSize < size.get()) {
				throw new SimpleException(ErrorCode.RESPONSE_SIZE, maxAllowedSizeInMB);
			}
		}
		System.out.println(size);
		return result;
	}

	public static Map<String, Object> makeObj(Schema schema, Map<String, Object> record, String defName, boolean ignoreNull, AtomicLong sizeEstimate) {
		TargetDefList def = schema.getOrNull(defName);
		Map<String, Object> result = new TreeMap<>();
		int size = 0;
		for (Entry<String, Object> entry : record.entrySet()) {
			if (ignoreNull && entry.getValue() == null)
				continue;

			Target target = new Target(entry.getKey());

			if (def.getFinalNames().contains(target.getName())) {
				if (target.hasJson()) {
					@SuppressWarnings("unchecked")
					Map<String, Object> jsonObj = (Map<String, Object>) result.getOrDefault(target.getName(), new TreeMap<String, Object>());
					jsonObj.put(target.getJson(), entry.getValue());
					size += target.getJson().length();
					if (jsonObj.size() == 1) {
						result.put(target.getName(), jsonObj);
						size += target.getName().length();
					}
				} else {
					result.put(target.getName(), entry.getValue());
					size += target.getName().length();
				}
				size += entry.getValue() == null ? 0 : entry.getValue().toString().length();
			}
		}
		sizeEstimate.getAndAdd(size);
		return result;
	}

	public static Map<String, Object> makeObj2(List<Target> targetCatalog, Map<String, Object> record, boolean ignoreNull, AtomicLong sizeEstimate) {

		if (targetCatalog == null || targetCatalog.isEmpty() || record == null || record.isEmpty()) {
			return new TreeMap<>();
		}

		Map<String, Object> result = new TreeMap<>();
		int size = 0;

		for (Target target : targetCatalog) {
			Object cellData = record.get(target.getName());

			if (ignoreNull && cellData == null)
				continue;

			if (target.hasJson()) {
				@SuppressWarnings("unchecked")
				Map<String, Object> jsonObj = (Map<String, Object>) result.getOrDefault(target.getName(), new TreeMap<>());
				jsonObj.put(target.getJson(), cellData);
				size += target.getJson().length();
				if (jsonObj.size() == 1) {
					result.put(target.getName(), jsonObj);
					size += target.getName().length();
				}
			} else {
				result.put(target.getName(), cellData);
				size += target.getName().length();
			}
			size += cellData == null ? 0 : cellData.toString().length();
		}

		sizeEstimate.getAndAdd(size);
		return result;
	}

	public static void main(String[] args) {
		Schema schema = new SelectExpansionConfig().getSelectExpansion().getSchema();

		Map<String, Object> rec1 = new HashMap<>();
		rec1.put("_stationtype", "AAA");
		rec1.put("_stationcode", "123");
		rec1.put("_datatypename", "t1");
		rec1.put("sname", "edgename1");
		rec1.put("tname", "t1");
		rec1.put("mperiod", 200);
		rec1.put("mvalidtime", 13);
		rec1.put("mtransactiontime", 88);
		// rec1.put("mvalue", 1111);

		Map<String, Object> rec2 = new HashMap<>();
		rec2.put("_stationtype", "AAA");
		rec2.put("_stationcode", "456");
		rec2.put("_datatypename", "t2");
		rec2.put("sname", "edgename2");
		rec2.put("tname", "t2");
		rec2.put("mperiod", 100);
		rec2.put("mvalidtime", 133);
		rec1.put("mtransactiontime", 8899);
		rec2.put("mvalue", 2222);

		List<Map<String, Object>> resultList = new ArrayList<>();
		resultList.add(rec1);
		resultList.add(rec2);

		// System.out.println(resultList);
		System.out.println(buildGeneric("stationtype", null, false, resultList, schema, 1000));

		// List<List<String>> result = schema.getHierarchy("stationtype", "stationend");
		// System.out.println(result);
		// System.out.println(schema.getHierarchyTriggerKeys("edgetype", "edge"));
	}

}
