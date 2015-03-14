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

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		multiIfaceSwitch = (Switch) findViewById(R.id.enable_multiiface);

		mpctrl = Manager.create(this);
		if (mpctrl == null) {
			Toast.makeText(this, "It seems this is not a rooted device",
					Toast.LENGTH_LONG).show();
			moveTaskToBack(true);
			return;
		}

		// do that now, to avoid useless call to onCheckedChangeListerner
		multiIfaceSwitch.setChecked(mpctrl.getEnabled());
		multiIfaceSwitch.setOnCheckedChangeListener(onCheckedChangeListerner);

		// start a new service if needed
		startService(new Intent(this, MainService.class));
	}

	@Override
	protected void onResume() {
		super.onResume();

		multiIfaceSwitch.setChecked(mpctrl.getEnabled());
	}

	protected void onDestroy() {
		super.onDestroy();
		Manager.destroy(this);
	}

	OnCheckedChangeListener onCheckedChangeListerner = new OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(CompoundButton buttonView,
				boolean isChecked) {
			mpctrl.setStatus(isChecked);
		}
	};
}
