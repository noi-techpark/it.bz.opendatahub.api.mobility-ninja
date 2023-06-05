// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package it.bz.idm.bdp.ninja.utils.miniparser;

public interface ConsumerExtended extends Consumer {
	boolean before(Token t);
	boolean after(Token t);
}
