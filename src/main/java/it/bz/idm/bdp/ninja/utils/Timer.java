package it.bz.idm.bdp.ninja.utils;

public class Timer {

	private long time;

	public void start() {
		time = System.nanoTime();
	}

	public long stop() {
		return (System.nanoTime() - time) / 1000000;
	}

}
