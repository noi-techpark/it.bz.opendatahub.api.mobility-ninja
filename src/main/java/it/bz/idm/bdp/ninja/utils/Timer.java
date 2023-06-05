// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

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
