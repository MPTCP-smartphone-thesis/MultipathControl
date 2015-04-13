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

package be.uclouvain.multipathcontrol.ifaces;

import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashMap;

import android.util.Log;
import be.uclouvain.multipathcontrol.global.Config;
import be.uclouvain.multipathcontrol.global.Manager;
import be.uclouvain.multipathcontrol.system.Cmd;
import be.uclouvain.multipathcontrol.system.IPRouteUtils;
import be.uclouvain.multipathcontrol.system.Sysctl;

public class IPRoute {

	private final MobileDataMgr mobileDataMgr;

	private HashMap<String, Integer> mIntfState;

	public IPRoute(MobileDataMgr mobileDataMgr) {
		mIntfState = new HashMap<String, Integer>();
		this.mobileDataMgr = mobileDataMgr;
		monitorInterfaces();
	}

	/* Add Policy routing for interface */
	private void setupRule(NetworkInterface iface, boolean update) {
		int table = IPRouteUtils.mapIfaceToTable(iface);

		if (update)
			IPRouteUtils.resetRule(iface);

		if (iface.getInterfaceAddresses().isEmpty())
			return;

		for (InterfaceAddress intfAddr : iface.getInterfaceAddresses()) {
			InetAddress addr = intfAddr.getAddress();
			int prefix = intfAddr.getNetworkPrefixLength();
			InetAddress subnet;
			String gateway = IPRouteUtils.getGateway(iface);

			if (gateway == null)
				continue;
			gateway = IPRouteUtils.removeScope(gateway);

			try {
				subnet = IPRouteUtils.toSubnet(addr, prefix);
			} catch (UnknownHostException e) {
				continue;
			}

			if (addr.isLinkLocalAddress())
				continue;

			String hostAddr = IPRouteUtils.removeScope(addr.getHostAddress());
			String subnetAddr = IPRouteUtils.removeScope(subnet
					.getHostAddress());
			if (hostAddr == null || subnetAddr == null) {
				Log.w(Manager.TAG, "hostAddr and/or subnetAddr is null");
				continue;
			}

			try {
				Cmd.runAsRoot(new String[] {
						"ip " + IPRouteUtils.getIPVersion(hostAddr)
								+ " rule add from " + hostAddr + " table "
								+ table,
						"ip " + IPRouteUtils.getIPVersion(subnetAddr)
								+ " route add " + subnetAddr + "/" + prefix
								+ " dev " + iface.getName()
								+ " scope link table " + table,
						"ip " + IPRouteUtils.getIPVersion(gateway)
								+ " route add default via " + gateway + " dev "
								+ iface.getName() + " table " + table, });
				if (IPRouteUtils.isMobile(iface)) {
					if (Config.defaultRouteData)
						IPRouteUtils.setDefaultRoute(iface.getName(), gateway,
								false);
					if (Config.dataBackup)
						Cmd.runAsRoot("ip link set dev " + iface.getName()
								+ " multipath backup");
					mobileDataMgr.keepMobileConnectionAlive();
				}
			} catch (Exception e) {
			}
			if (!Config.ipv6)
				Sysctl.setIPv6(false, iface.getName());
		}

	}

	public boolean monitorInterfaces() {
		boolean update = false;
		try {
			for (NetworkInterface iface : Collections.list(NetworkInterface
					.getNetworkInterfaces())) {
				int addrs = Config.mEnabled ? iface.getInterfaceAddresses()
						.hashCode() : 1;
				String name = iface.getName();

				if (iface.isLoopback())
					continue;

				if (!mIntfState.containsKey(name)) {
					if (addrs != 1) /* hashcode of an empty List is 1 */
						setupRule(iface, false);
					Log.i(Manager.TAG, "New monitor " + name + " addrs: "
							+ addrs);
					mIntfState.put(name, addrs);
					update = true;
					continue;
				}

				if (addrs != mIntfState.get(name)) {
					Log.i(Manager.TAG, "New addrs for " + name + " addrs: "
							+ addrs);
					setupRule(iface, true);
					mIntfState.put(name, addrs);
					update = true;
				}
			}
		} catch (SocketException e) {
		}

		// if WLan iface has been modified, default route will be wrong
		if (update && Config.defaultRouteData)
				IPRouteUtils.setDefaultRoute();

		return update;
	}
}
