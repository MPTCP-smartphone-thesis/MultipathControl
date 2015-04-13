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

package be.uclouvain.multipathcontrol.activities;

import android.app.ListActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;
import be.uclouvain.multipathcontrol.global.Config;
import be.uclouvain.multipathcontrol.system.Sysctl;

public class TCPCCActivity extends ListActivity {
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_list_item_1, Sysctl.getAvailableCC());
		setListAdapter(adapter);
		setSelected(Config.tcpcc);
	}

	private void setSelected(String item) {
		getActionBar().setTitle("Selected: " + item);
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		String item = (String) getListAdapter().getItem(position);
		if (!Config.tcpcc.equals(item) && Sysctl.setCC(item)) {
			setSelected(item);
			Config.tcpcc = item;
			Config.saveStatus(getApplicationContext());
		} else
			Toast.makeText(TCPCCActivity.this, "Not able to set " + item,
					Toast.LENGTH_LONG).show();
	}
}
