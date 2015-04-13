/*
 * This file is part of MultipathControl.
 *
 * Copyright 2012 UCLouvain - Gregory Detal <first.last@uclouvain.be>
 * Copyright 2015 UCLouvain - Matthieu Baerts <first.last@student.uclouvain.be>
 *
 * MultipathControl is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package be.uclouvain.multipathcontrol.system;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import android.util.Log;
import be.uclouvain.multipathcontrol.global.Manager;

public class Cmd {
	private Cmd() {
	}

	public static Process runAsUser(String cmd) throws Exception {
		return Runtime.getRuntime().exec(cmd.split(" "));
	}

	public static Process runAsRoot(String cmd) throws Exception {
		Log.d(Manager.TAG, "command: " + cmd);
		return Runtime.getRuntime().exec(new String[] { "su", "-c", cmd });
	}

	public static void runAsRoot(String[] cmds) throws Exception {
		for (String cmd : cmds) {
			runAsRoot(cmd);
		}
	}

	public static String getFirstLine(String cmd, boolean root) {
		String line;
		Process p;
		try {
			if (root)
				p = runAsRoot(cmd);
			else
				p = runAsUser(cmd);
			BufferedReader in = new BufferedReader(new InputStreamReader(
					p.getInputStream()));
			line = in.readLine();
			in.close();
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		return line;
	}

	public static String getFirstLine(String cmd) {
		return getFirstLine(cmd, false);
	}

	public static List<String> getAllLines(String cmd, boolean root) {
		List<String> lines = new ArrayList<String>();
		String line;
		Process p;
		BufferedReader in = null;
		try {
			if (root)
				p = runAsRoot(cmd);
			else
				p = runAsUser(cmd);
			in = new BufferedReader(new InputStreamReader(p.getInputStream()));
			while ((line = in.readLine()) != null) {
				lines.add(line);
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (in != null)
				try {
					in.close();
				} catch (IOException e) {
				}
		}
		return lines.size() > 0 ? lines : null;
	}

	public static List<String> getAllLines(String cmd) {
		return getAllLines(cmd, false);
	}

	public static String getAllLinesString(String cmd, boolean root, char sep) {
		StringBuffer sBuffer = new StringBuffer();
		List<String> lines = getAllLines(cmd, root);
		for (String line : lines) {
			sBuffer.append(line);
			sBuffer.append(sep);
		}
		return sBuffer.toString();
	}

	public static String getAllLinesString(String cmd, char sep) {
		return getAllLinesString(cmd, false, sep);
	}

	public static String getAllLinesString(String cmd, boolean root) {
		return getAllLinesString(cmd, root, '\n');
	}

	public static String getAllLinesString(String cmd) {
		return getAllLinesString(cmd, false, '\n');
	}
}
