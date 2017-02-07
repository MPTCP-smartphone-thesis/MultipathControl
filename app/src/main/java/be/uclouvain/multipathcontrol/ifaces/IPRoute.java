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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import android.os.Handler;
import android.util.Log;
import be.uclouvain.multipathcontrol.global.Config;
import be.uclouvain.multipathcontrol.global.Manager;
import be.uclouvain.multipathcontrol.system.Cmd;
import be.uclouvain.multipathcontrol.system.IPRouteUtils;
import be.uclouvain.multipathcontrol.system.Sysctl;

import static be.uclouvain.multipathcontrol.system.IPRouteUtils.disableDoze;
import static be.uclouvain.multipathcontrol.system.IPRouteUtils.disableVlan;
import static be.uclouvain.multipathcontrol.system.IPRouteUtils.seLinuxSetPermissive;

public class IPRoute {

	private final MobileDataMgr mobileDataMgr;

	private HashMap<String, Integer> mIntfState;
	private int ifaceCount;

	public class DefaultIFaceWatchdogRunnable implements Runnable {
		private IPRoute iproute;
		private int counter;
		public DefaultIFaceWatchdogRunnable(IPRoute ip) {
			this.iproute = ip;
			this.counter = 0;
		}
		public void run() {
			if (counter == 0) {
				counter = 5;
			}
			disableVlan();
			disableDoze();
			seLinuxSetPermissive();
			List<String> cmdOutput = Cmd.getAllLines("ip route", false);
			if (cmdOutput != null && cmdOutput.size() > 0) {
				int count = 0;
				for (String line: cmdOutput) {
					if (line.startsWith("default"))
						count += 1;
				}
				if (count != iproute.ifaceCount) {
					Log.d("defaultIfaceWatchdow", "Bites with " + count + " and " + iproute.ifaceCount);
					IPRouteUtils.setDefaultRoute();
				}
			}
			counter -= 1;
			if (counter > 0)
				handler.postDelayed(this, 1000);
			else
				handler.postDelayed(this, 60000);
		}
	};

	private Handler handler = new Handler();
	private DefaultIFaceWatchdogRunnable difw;

	public IPRoute(MobileDataMgr mobileDataMgr) {
		mIntfState = new HashMap<String, Integer>();
		this.mobileDataMgr = mobileDataMgr;
		this.ifaceCount = 0;
		monitorInterfaces();
		this.difw = new DefaultIFaceWatchdogRunnable(this);
		this.handler.postDelayed(difw, 1000);
	}

	/* Add Policy routing for interface */
	private void setupRule(NetworkInterface iface, boolean update) {
		int table = IPRouteUtils.mapIfaceToTable(iface);

		if (iface.getInterfaceAddresses().isEmpty())
			return;

		if (update)
			IPRouteUtils.resetRule(iface);

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
				String metric;
				if (IPRouteUtils.isMobile(iface)) {
					metric = Config.defaultRouteData ? "50" : "100";
				} else {
					metric = Config.defaultRouteData ? "100" : "50";
				}
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
								+ iface.getName() + " metric " + metric + " table " + table, });
				if (IPRouteUtils.isMobile(iface)) {
					IPRouteUtils.setDefaultRoute(iface.getName(), gateway, false,
							Config.defaultRouteData);
					if (Config.dataBackup)
						Cmd.runAsRootSafe("ip link set dev " + iface.getName()
								+ " multipath backup");
					mobileDataMgr.keepMobileConnectionAlive();
				} else if (IPRouteUtils.isWifi(iface)) {
					IPRouteUtils.setDefaultRoute(iface.getName(), gateway,
								false, !Config.defaultRouteData);
				}
			} catch (Exception e) {
			}
			if (!Config.ipv6)
				Sysctl.setIPv6(false, iface.getName());
		}

	}

	public boolean monitorInterfaces() {
		boolean update = false;
		int count = 0;
		try {
			for (NetworkInterface iface : Collections.list(NetworkInterface
					.getNetworkInterfaces())) {
				int addrs = Config.mEnabled ? iface.getInterfaceAddresses()
						.hashCode() : 1;
				String name = iface.getName();

				if (iface.isLoopback())
					continue;

				if (iface.isUp() && (IPRouteUtils.isMobile(iface) || IPRouteUtils.isWifi(iface)))
					count += 1;

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
		// But if Android does not stop the mobile interface, change default interface on every update
		if (update || count != this.ifaceCount) {
			IPRouteUtils.setDefaultRoute();
			this.handler.postDelayed(difw, 1000);
		}

		this.ifaceCount = count;

		return update;
	}
}
