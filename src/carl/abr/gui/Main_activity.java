/*******************************************************************************************************
Copyright (c) 2011 Regents of the University of California.
All rights reserved.

This software was developed at the University of California, Irvine.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions
are met:

1. Redistributions of source code must retain the above copyright
   notice, this list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright
   notice, this list of conditions and the following disclaimer in
   the documentation and/or other materials provided with the
   distribution.

3. All advertising materials mentioning features or use of this
   software must display the following acknowledgment:
   "This product includes software developed at the University of
   California, Irvine by Nicolas Oros, Ph.D.
   (http://www.cogsci.uci.edu/~noros/)."

4. The name of the University may not be used to endorse or promote
   products derived from this software without specific prior written
   permission.

5. Redistributions of any form whatsoever must retain the following
   acknowledgment:
   "This product includes software developed at the University of
   California, Irvine by Nicolas Oros, Ph.D.
   (http://www.cogsci.uci.edu/~noros/)."

THIS SOFTWARE IS PROVIDED ``AS IS'' AND WITHOUT ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, WITHOUT LIMITATION, THE IMPLIED
WARRANTIES OF MERCHANTIBILITY AND FITNESS FOR A PARTICULAR PURPOSE.
IN NO EVENT SHALL THE UNIVERSITY OR THE PROGRAM CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*******************************************************************************************************/

package carl.abr.gui;

import java.util.List;

import ioio.lib.util.IOIOLooper;
import ioio.lib.util.IOIOLooperProvider;
import ioio.lib.util.android.IOIOAndroidApplicationHelper;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.Camera.Size;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.ToggleButton;
import carl.abr.IO.Camera_feedback;
import carl.abr.IO.GPS_listener;
import carl.abr.IO.Sensors_listener;
import carl.abr.threads.IOIO_thread;
import carl.abr.threads.Main_thread;
import carl.gui.R;

// I've copied what was in IOIOActivity to this activity 
public class Main_activity extends Activity implements IOIOLooperProvider 		// implements IOIOLooperProvider: from IOIOActivity
{
	private final IOIOAndroidApplicationHelper helper_ = new IOIOAndroidApplicationHelper(this, this);			// from IOIOActivity

	final String tag = "Main activity";
	Main_activity the_activity;				//reference to this activity	

	/***************************************************************   Android Inputs   ***************************************************************/
	SensorManager sensorManager;	
	public LocationManager locationManager;
	public Sensors_listener sensor_listener;
	Sensor compass, accelerometer, gyro;
	public GPS_listener locationListener_GPS;
	public Camera_feedback the_cam;

	/***************************************************************   Threads   ***************************************************************/
	public Main_thread the_main_thread;

	/***************************************************************   IOIO  ***************************************************************/
	public IOIO_thread the_IOIO;
//	public boolean RC_MODE=false;
//	public boolean EXPLORE_MODE=false;
	public boolean INVERTED=false;

	/***************************************************************   GUI stuff   ***************************************************************/
	My_spinner_Class spinner_IP, spinner_port;
	EditText ip_text, port_text;
	Button button_add_IP, button_delete_IP, button_add_port, button_delete_port;
	public ToggleButton button_connect;	

	PowerManager.WakeLock wake;

	/***************************************************************   Networking    ***************************************************************/
	public String IP_server = null;
	public int port_TCP;

	/***************************************************************   extra variables   ***************************************************************/
	public int idx_size_cam; //index used to set size of image from camera
	public boolean SENSORS_STARTED, CAMERA_STARTED, IOIO_STARTED;

	/****************************************************************************** opencv 2.4.5*********************************************************************************/	
	BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
		@Override
		public void onManagerConnected(int status) 
		{
			switch (status) 
			{
			case LoaderCallbackInterface.SUCCESS:
				Log.i(tag, "OpenCV loaded successfully");
				break;
			default:
				super.onManagerConnected(status);
				break;
			}
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState) 
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main_acti);

		the_activity = this;
		helper_.create();	// from IOIOActivity

		SENSORS_STARTED = false;
		CAMERA_STARTED = false;
		IOIO_STARTED = false;

		/****************************************************************************** opencv 2.4.5 *********************************************************************************/		
		Log.i(tag, "Trying to load OpenCV library");
		if (!OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_5, this, mLoaderCallback)) Log.e(tag, "Cannot connect to OpenCV Manager");

		/********************************************************************************************************************************************************************/
		/****************************************************************************** GUI *********************************************************************************/
		/********************************************************************************************************************************************************************/
		ip_text = (EditText) findViewById(R.id.txt_IP);
		port_text = (EditText) findViewById(R.id.txt_port);
		spinner_IP = (My_spinner_Class)findViewById(R.id.spinner_IP);
		spinner_port = (My_spinner_Class)findViewById(R.id.spinner_ports);
		button_add_IP = (Button) findViewById(R.id.btn_add_IP);
		button_delete_IP= (Button) findViewById(R.id.btn_delete_IP);
		button_add_port = (Button) findViewById(R.id.btn_add_port);
		button_delete_port = (Button) findViewById(R.id.btn_delete_port);
		button_connect = (ToggleButton) findViewById(R.id.btn_connect);
		set_buttons(false);
		button_connect.requestFocus();

		spinner_IP.set_file_name("IP_clients.txt");
		spinner_port.set_file_name("ports_clients.txt");

		spinner_IP.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> arg0, View arg1,int arg2, long arg3) 
			{
				IP_server = spinner_IP.getSelected();		
				Toast.makeText(the_activity, "IP_address: " + IP_server, Toast.LENGTH_SHORT).show();	
				set_buttons(true);
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {}
		});	

		spinner_port.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> arg0, View arg1,int arg2, long arg3) 
			{
				String port = spinner_port.getSelected();
				port_TCP = Integer.parseInt(port);
				Toast.makeText(the_activity, "Port: " + port, Toast.LENGTH_SHORT).show();	
				set_buttons(true);
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {}
		});	

		button_add_IP.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) 
			{
				IP_server = ip_text.getText().toString(); 
				ip_text.setText("");
				InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
				imm.hideSoftInputFromWindow(ip_text.getWindowToken(), 0);
				spinner_IP.addItem(IP_server);
				ip_text.clearFocus();
				set_buttons(true);
			}
		});		

		button_add_port.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) 
			{
				String port = port_text.getText().toString(); 

				try
				{
					port_TCP = Integer.parseInt(port);
					spinner_port.addItem(port);
					set_buttons(true);
				}
				catch(java.lang.NumberFormatException e)
				{
					AlertDialog alertDialog;
					alertDialog = new AlertDialog.Builder(the_activity).create();
					alertDialog.setTitle("Error port");
					alertDialog.setMessage("enter a number  \n\n (press back to return)");
					alertDialog.show();			
				}

				port_text.setText("");
				InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
				imm.hideSoftInputFromWindow(port_text.getWindowToken(), 0);
				port_text.clearFocus();
			}
		});		

		button_delete_IP.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) 
			{
				if(spinner_IP.remove_item() == false) set_buttons(false);
			}
		});		

		button_delete_port.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) 
			{
				if(spinner_port.remove_item() == false) set_buttons(false);
			}
		});		

		button_connect.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) 
			{
				if (button_connect.isChecked()) Start_TCP_client();
				else  stop_all();
			}
		});		
	}

	@Override
	protected void onResume() 
	{
		super.onResume();

		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wake = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, tag);
		wake.acquire();		
	}

	private void set_buttons(boolean b)
	{
		button_delete_IP.setEnabled(b);
		button_delete_port.setEnabled(b);
		button_connect.setEnabled(b);
	}

	/********************************************************************************************************************************************************************/
	/************************************************************* TCP, VIDEO, SENSORS, IOIO *********************************************************************************/
	/********************************************************************************************************************************************************************/

	public void Start_TCP_client()
	{
		String txt = new String();
		txt = "PHONE/" + Build.MODEL + " " + Build.MANUFACTURER + " " + Build.PRODUCT;

		try	// get supported sizes of camera...to send to the server
		{
			Camera mCamera = Camera.open();        
			Camera.Parameters parameters = mCamera.getParameters(); 
			List<Size> mSupportedPreviewSizes = parameters.getSupportedPreviewSizes();
			Size a_size;

			for(int i=0;i<mSupportedPreviewSizes.size();i++)
			{
				a_size = mSupportedPreviewSizes.get(i);
				txt += "/" + Integer.toString(a_size.width) + "x"+ Integer.toString(a_size.height); 
			}
			txt += "/\n";

			if (mSupportedPreviewSizes != null) {Log.i(tag, "nb supported sizes: " + mSupportedPreviewSizes.size());}
			mCamera.release(); 
		}
		catch(Exception e){Log.e(tag, "error camera");}
		
		the_main_thread = new Main_thread(this, txt);
		the_main_thread.start();
	}

	public void stop_all()
	{
		if(the_main_thread != null)the_main_thread.stop_thread();
		
		stop_all2();		
	}
	
	public void stop_all2()
	{
		stop_video();
		stop_sensors();
		stop_IOIO();	
	}
	

	//main thread takes care of connecting and disconnecting UDP socket...not here
	public void start_video()
	{
		if(CAMERA_STARTED == false)
		{
			the_cam = new Camera_feedback(idx_size_cam);						
//			the_main_thread.start_camera_udp();
			CAMERA_STARTED = true;
		}
	}

	public void stop_video()
	{
		if(CAMERA_STARTED == true)
		{
			the_cam.stop_camera();	
			the_cam = null;			
//			the_main_thread.stop_camera_udp();
			CAMERA_STARTED = false;
		}
	}

	public void start_sensors()
	{	
		if(SENSORS_STARTED == false)
		{
			sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
			locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);	
			
			sensor_listener = new Sensors_listener(the_activity);
			locationListener_GPS = new GPS_listener(the_activity);	

			compass = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);	
			accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
			gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
			sensorManager.registerListener(sensor_listener, compass, SensorManager.SENSOR_DELAY_FASTEST);
			sensorManager.registerListener(sensor_listener, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
			sensorManager.registerListener(sensor_listener, gyro, SensorManager.SENSOR_DELAY_FASTEST);
			locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener_GPS);
			
//			the_main_thread.start_sensors_udp();
			SENSORS_STARTED = true;
		}
	}

	public void stop_sensors()
	{
		if(SENSORS_STARTED == true)
		{			
			sensorManager.unregisterListener(sensor_listener);
			locationManager.removeUpdates(locationListener_GPS);			
//			the_main_thread.stop_sensors_udp();
			SENSORS_STARTED = false;
		}
	}

	public void start_IOIO()
	{
		if(IOIO_STARTED == false)
		{
			helper_.start();			
			IOIO_STARTED = true;			
//			the_main_thread.start_IOIO_udp();
		}
	}

	public void stop_IOIO()
	{
		if(IOIO_STARTED == true)
		{	
//			the_main_thread.stop_IOIO_udp();
			helper_.stop();
			the_IOIO = null;
			IOIO_STARTED = false;						
		}
	}

	/********************************************************************************************************************************************************************/
	/****************************************************** functions from original IOIOActivity *********************************************************************************/
	/********************************************************************************************************************************************************************/

//	protected IOIOLooper createIOIOLooper() 
//	{
//		//  !!!!!!!!!!!!!!!!!   create our own IOIO thread (Looper) with a reference to this activity  !!!!!!!!!!!!!!!!! 
////		the_IOIO = new IOIO_thread(this);
////		the_IOIO.set_inverted(INVERTED);
////		
////		Log.i(tag, "create ioio_looper");		
//
//		return the_IOIO;
//	}

	@Override
	public IOIOLooper createIOIOLooper(String connectionType, Object extra) 
	{
//		Log.i(tag, "type: " + connectionType);
	
		if(connectionType.matches("ioio.lib.android.bluetooth.BluetoothIOIOConnection"))
		{
			Log.i(tag,"create ioio: " +  SystemClock.elapsedRealtime());
			
			the_IOIO = new IOIO_thread(this);
			the_IOIO.set_inverted(INVERTED);
			return the_IOIO;
		}
		else return null;
	}

	@Override
	protected void onDestroy() 
	{
		helper_.destroy();
		super.onDestroy();
	}

	@Override
	protected void onStart() 
	{
		super.onStart();
		//		helper_.start();
		Log.i(tag,"start: " +  SystemClock.elapsedRealtime());
	}

	@Override
	protected void onStop() 
	{
		//		helper_.stop();

		wake.release();
		stop_all();
		super.onStop();

		Log.i(tag, "stopping activity ");
		this.finish();
	}

	@Override
	protected void onNewIntent(Intent intent) 
	{
		super.onNewIntent(intent);
		if ((intent.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0) 
		{
			helper_.restart();
			Log.i(tag,"new intent: " +  SystemClock.elapsedRealtime());
		}
	}

}