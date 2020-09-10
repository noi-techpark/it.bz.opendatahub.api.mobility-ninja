package it.bz.idm.bdp.ninja.utils.resultbuilder;

public class LookUp {

	private String parentDefListName;
	private String parentTargetName;
	private LookUpType type;
	private String mapTypeKey;

	public LookUp(LookUpType type, String parentDefListName, String parentTargetName, String mapTypeKey) {
		this.type = type;
		this.mapTypeKey = mapTypeKey;
		this.parentDefListName = parentDefListName;
		this.parentTargetName = parentTargetName;
	}

	public String getParentTargetName() {
		return parentTargetName;
	}

	public String getParentDefListName() {
		return this.parentDefListName;
	}

	public LookUpType getType() {
		return this.type;
	}

	public String getMapTypeKey() {
		return this.mapTypeKey;
	}
}
