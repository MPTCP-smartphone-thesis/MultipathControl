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
