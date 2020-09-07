package it.bz.idm.bdp.ninja.utils;

import java.util.HashSet;
import java.util.Set;

import it.bz.idm.bdp.ninja.utils.simpleexception.ErrorCodeInterface;
import it.bz.idm.bdp.ninja.utils.simpleexception.SimpleException;

public enum Representation {
	TREE_NODE,
	FLAT_NODE,
	TREE_EDGE,
	FLAT_EDGE;

	private enum ErrorCode implements ErrorCodeInterface {
		WRONG_REPRESENTATION("Please choose 'flat' or 'tree' as representation, and 'edge' or 'node' (default) as dataset. Separate them with a comma. '%s' is not allowed.");

		private final String msg;

		ErrorCode(final String msg) {
			this.msg = msg;
		}

		@Override
		public String getMsg() {
			return "PARSING ERROR: " + msg;
		}
	}

	public static Representation get(final String representation) {
		Set<String> resultSet = new HashSet<>();
		for (String value : representation.split(",")) {
			value = value.toLowerCase().trim();
			resultSet.add(value);
		}

		if (resultSet.contains("flat")) {
			if (resultSet.contains("edge")) {
				return Representation.FLAT_EDGE;
			}
			if (resultSet.size() == 1 || resultSet.contains("node")) {
				return Representation.FLAT_NODE;
			}
		} else if (resultSet.contains("tree")) {
			if (resultSet.contains("edge")) {
				return Representation.TREE_EDGE;
			}
			if (resultSet.size() == 1 || resultSet.contains("node")) {
				return Representation.TREE_NODE;
			}
		}
		throw new SimpleException(ErrorCode.WRONG_REPRESENTATION, representation);
	}

	public boolean isFlat() {
		return this.ordinal() == 1 || this.ordinal() == 3;
	}

	public boolean isEdge() {
		return this.ordinal() == 2 || this.ordinal() == 3;
	}

	@Override
	public String toString() {
		return super.toString().toLowerCase().replace("_", ",");
	}
}
