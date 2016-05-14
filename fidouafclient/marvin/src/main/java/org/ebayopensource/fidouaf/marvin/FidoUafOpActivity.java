/*
 * Copyright 2015 eBay Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ebayopensource.fidouaf.marvin;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Browser;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.google.gson.Gson;

import org.ebayopensource.fidouaf.marvin.client.op.Auth;
import org.ebayopensource.fidouaf.marvin.client.op.Reg;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FidoUafOpActivity extends Activity {

	private Logger logger = Logger.getLogger(this.getClass().getName());
	private Gson gson = new Gson();
	private TextView operation;
	private TextView uafMsg;
	private Reg regOp = new Reg();
	private Auth authOp = new Auth();
	private KeyguardManager keyguardManager;
	private int REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS = 1;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
		Bundle extras = this.getIntent().getExtras();
		setContentView(R.layout.activity_fido_uaf_consent);
		operation = (TextView)findViewById(R.id.marvinTextViewOperation);
		uafMsg = (TextView)findViewById(R.id.marvinTextViewOpMsg);
		if (extras != null) {
			operation.setText(extras.getString("UAFIntentType"));
			uafMsg.setText(extras.getString("message"));
		}
		Uri data = this.getIntent().getData();
		if (data != null) {
			String path = getPath(data.toString());
			Map<String, String> map = parse(path);
			operation.setText(map.get("UAFIntentType"));
			uafMsg.setText(map.get("message"));
		}
	}

	private String getPath (String uri){
		String path = "";
		String[] arr = uri.split("\\?");
		if (arr != null && arr.length == 2){
			path = arr[1];
		}
		return path;
	}

	private Map<String,String> parse (String path){
		Map<String,String> ret = new HashMap<>();
		if (ret != null) {
			String[] nvPairs = path.split("&");
			if (nvPairs != null) {
				for (String nv : nvPairs) {
					String[] arr = nv.split("=");
					if (arr != null && arr.length == 2) {
						ret.put(arr[0], arr[1]);
					}
				}
			}
		}
		return ret;
	}
	
	private void finishWithResult(){
	    Bundle data = new Bundle();
		String inMsg = getInMsg();
		String msg = "";
		try {
			if (inMsg != null && inMsg.length()>0) {
				msg = processOp (inMsg);
			}
		} catch (Exception e){
			uafMsg.setText(e.getMessage());
			logger.log(Level.WARNING, "Not able to get registration response", e);
			return;
		}
		data.putString("message", msg);
	    Intent intent = new Intent();
	    intent.putExtras(data);
	    setResult(RESULT_OK, intent);
	    finish();
	}

	private String getInMsg (){
		String ret = "";
		Intent i = this.getIntent();
		if (i.getExtras() != null) {
			ret = i.getExtras().getString("message");
		}
		if (i.getData() != null) {
			ret = parse(getPath(i.getData().toString())).get("message");
			Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.head2toes.org#message="+ret));
			browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			browserIntent.setPackage("com.android.browser");
			browserIntent.putExtra(Browser.EXTRA_APPLICATION_ID, "com.android.browser");
			//setResult(RESULT_OK, browserIntent);
			//finish();
			this.startActivity(browserIntent);
			setResult(RESULT_OK);
			finish();
		}
		return ret;
	}

	private String processOp (String inUafOperationMsg) throws Exception{
		String msg = "";
		String inMsg = extract(inUafOperationMsg);
		if (inMsg.contains("\"Reg\"")) {
			msg = regOp.register(inMsg, getBaseContext());
		} else if (inMsg.contains("\"Auth\"")) {
			msg = authOp.auth(inMsg);
		} else if (inMsg.contains("\"Dereg\"")) {

		}
		return msg;
	}


//	@TargetApi ((Build.VERSION_CODES.LOLLIPOP))
	public void proceed(View view) {
		if (keyguardManager.isKeyguardSecure()) {
			Intent intent = keyguardManager.createConfirmDeviceCredentialIntent("UAF", "Confirm Identity");
			if (intent != null) {
				startActivityForResult(intent, REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS);
			}
		} else {
			finishWithResult();
		}

	}

	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS) {
			// Challenge completed, proceed with using cipher
			if (resultCode == RESULT_OK) {
				finishWithResult();
			} else {
				// The user canceled or didn’t complete the lock screen
				// operation. Go to error/cancellation flow.
			}
		}
	}
	
	public void back(View view) {
		Bundle data = new Bundle();
		String msg = "";
		logger.info("Registration canceled by user");
		data.putString("message", msg);
		Intent intent = new Intent();
		intent.putExtras(data);
		setResult(RESULT_OK, intent);
		finish();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();

		return super.onOptionsItemSelected(item);
	}

	private String extract(String inMsg) {
		try {
			JSONObject tmpJson = new JSONObject(inMsg);
			String uafMsg = tmpJson.getString("uafProtocolMessage");
			uafMsg.replace("\\\"", "\"");
			return uafMsg;
		} catch (Exception e){
			logger.log(Level.WARNING, "Input message is invalid!", e);
			return "";
		}

	}
}