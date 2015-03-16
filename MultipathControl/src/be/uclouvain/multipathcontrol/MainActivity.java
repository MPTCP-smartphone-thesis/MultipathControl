package be.uclouvain.multipathcontrol;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.Switch;
import android.widget.Toast;

public class MainActivity extends Activity {

	private MPCtrl mpctrl;
	private Switch multiIfaceSwitch;
	private Switch defaultDataSwitch;
	private Switch dataBackupSwitch;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		multiIfaceSwitch = (Switch) findViewById(R.id.switch_multiiface);
		defaultDataSwitch = (Switch) findViewById(R.id.switch_default_data);
		dataBackupSwitch = (Switch) findViewById(R.id.switch_data_backup);

		mpctrl = Manager.create(this);
		if (mpctrl == null) {
			Toast.makeText(this, "It seems this is not a rooted device",
					Toast.LENGTH_LONG).show();
			moveTaskToBack(true);
			return;
		}

		// do that now, to avoid useless call to onCheckedChangeListerner
		setChecked();
		multiIfaceSwitch
				.setOnCheckedChangeListener(onCheckedChangeListernerMultiIface);
		defaultDataSwitch
				.setOnCheckedChangeListener(onCheckedChangeListernerDefaultData);
		dataBackupSwitch
				.setOnCheckedChangeListener(onCheckedChangeListernerDataBackup);

		// start a new service if needed
		startService(new Intent(this, MainService.class));
	}

	@Override
	protected void onResume() {
		super.onResume();

		setChecked();
	}

	protected void onDestroy() {
		super.onDestroy();
		Manager.destroy(this);
	}

	private void setChecked() {
		multiIfaceSwitch.setChecked(mpctrl.getEnabled());
		defaultDataSwitch.setChecked(mpctrl.getDefaultData());
		dataBackupSwitch.setChecked(mpctrl.getDataBackup());
	}

	private OnCheckedChangeListener onCheckedChangeListernerMultiIface = new OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(CompoundButton buttonView,
				boolean isChecked) {
			if (mpctrl.setStatus(isChecked) && !isChecked)
				Toast.makeText(
						MainActivity.this,
						"The second interface will be disabled in a few seconds",
						Toast.LENGTH_LONG).show();
		}
	};

	private OnCheckedChangeListener onCheckedChangeListernerDefaultData = new OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(CompoundButton buttonView,
				boolean isChecked) {
			mpctrl.setDefaultData(isChecked);
		}
	};

	private OnCheckedChangeListener onCheckedChangeListernerDataBackup = new OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(CompoundButton buttonView,
				boolean isChecked) {
			mpctrl.setDataBackup(isChecked);
		}
	};
}
