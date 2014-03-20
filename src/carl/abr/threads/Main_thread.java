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

package carl.abr.threads;

import ioio.lib.util.IOIOLooper;
import ioio.lib.util.IOIOLooperProvider;
import ioio.lib.util.android.IOIOAndroidApplicationHelper;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import android.content.Context;
import android.hardware.Camera;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.Camera.Size;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfInt;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;

import carl.abr.IO.Camera_feedback;
import carl.abr.IO.GPS_listener;
import carl.abr.IO.Sensors_listener;
import carl.abr.gui.Main_activity;

public class Main_thread extends Thread implements IOIOLooperProvider 		// implements IOIOLooperProvider: from IOIOActivity
{
	static final String TAG = "main_thread";
	Main_activity the_gui;	

	/***************************************************************   booleans  ***************************************************************/
	boolean STOP 			= false;
	boolean RECONNECT_TCP	= true;
	boolean SENSORS_STARTED	= false;
	boolean IOIO_STARTED	= false;
	boolean CAMERA_STARTED	= false;
	boolean NEW_IMA			= true;		//first time, image will be created, then modified
	boolean NEW_FRAME		= false;
	boolean NEW_DATA_IOIO	= false;	
	boolean NEW_DATA_GPS	= false;
	boolean NEW_DECLI		= false;	//only get earth declination once
	boolean RC_MODE 		= true;		// rc mode for robot
	boolean INVERTED 		= false;	// inverted pwm signal

	/***************************************************************   camera   ***************************************************************/
	Camera_feedback the_camera;
	int idx_size_cam; 					//index used to set size of image from camera	

	ByteArrayOutputStream byteStream;
	byte frame_nb = 0;	
	int width_ima, height_ima,packetCount,nb_packets,size;
	byte[] picData;
	int compression_rate;
	Mat the_frame,dest, dest2;							//openCV images	

	/*****************************************   IOIO    ***************************************************************/
	final IOIOAndroidApplicationHelper ioio_helper;
	IOIO_thread the_ioio;
	static int DEFAULT_PWM = 1500, MIN_PWM_MOTOR=1400, MAX_PWM_MOTOR=1600, MIN_PWM_SERVO=1000, MAX_PWM_SERVO=2000;	
	float pwm_servo = DEFAULT_PWM;
	float pwm_motor = DEFAULT_PWM;	
	float IR_right, IR_left, IR_front_left, IR_front_right; 
	float[] IR_vals, PWM_vals;
	String string_ioio_vals;	

	/*****************************************   sensors    ***************************************************************/
	SensorManager sensorManager;	
	Sensor compass, accelerometer, gyro;
	LocationManager locationManager;
	Sensors_listener the_sensors;
	GPS_listener the_GPS;
	float[] acceleration;
	float[] orientation;
	float[] gyroscope;
	String string_sensors_vals;
	Location lastKnownLocation_GPS, target_location;
	double latitude, longitude, altitude, accuracy;
	float declination;

	/*****************************************   UDP   ***************************************************************/
	static int HEADER_SIZE = 5;
	static int DATAGRAM_MAX_SIZE = 1450 - HEADER_SIZE;	
	InetAddress serverAddr;
	String ip_address_server;	
	DatagramSocket socket_udp_ioio, socket_udp_sensors, socket_udp_camera;	
	int port_ioio, port_sensors, port_camera;	

	/*****************************************   TCP   ***************************************************************/
	int port_TCP;
	Socket the_TCP_socket;
	String message_TCP;
	InetSocketAddress serverAddr_TCP;
	BufferedWriter out;
	BufferedReader input;
	int counter_TCP_check=0;

	public Main_thread(Main_activity gui)
	{
		the_gui = gui;
		ip_address_server = the_gui.IP_server;	
		port_TCP = the_gui.port_TCP;

		ioio_helper = new IOIOAndroidApplicationHelper(the_gui, this);			// from IOIOActivity
		ioio_helper.create();	// from IOIOActivity		
				
		message_TCP = new String();
		string_sensors_vals = new String();
		string_ioio_vals = new String();
		
		message_TCP = "PHONE/" + Build.MODEL + " " + Build.MANUFACTURER + " " + Build.PRODUCT;		

		try	// get supported sizes of camera...to send to the server
		{
			Camera mCamera = Camera.open();        
			Camera.Parameters parameters = mCamera.getParameters(); 
			List<Size> mSupportedPreviewSizes = parameters.getSupportedPreviewSizes();
			Size a_size;

			for(int i=0;i<mSupportedPreviewSizes.size();i++)
			{
				a_size = mSupportedPreviewSizes.get(i);
				message_TCP += "/" + Integer.toString(a_size.width) + "x"+ Integer.toString(a_size.height); 
			}
			message_TCP += "/\n";

			if (mSupportedPreviewSizes != null) {Log.i(TAG, "nb supported sizes: " + mSupportedPreviewSizes.size());}
			mCamera.release(); 
		}
		catch(Exception e){Log.e(TAG, "error camera");}
	}

	/********************************************************************************************************************************************************************/
	/***************************************************************   main  loop   ***************************************************************/
	/********************************************************************************************************************************************************************/
	@Override
	public final void run() 
	{	
		start_tcp();	// connect to the server

		while(STOP == false)
		{		
			synchronized(this)					//thread cannot be stopped by activity (user) while running this part of the code
			{
				//get (update) data
				get_sensors_data();
				get_camera_data();
				get_ioio_data();

				//do stuff with data here...
				//				if (EXPLORE_MODE == true)	autoDriveWF();

				//send data to server (udp sockets)
				send_sensors_data();
				send_camera_data();
				send_ioio_data();
			}
			read_tcp();					//read tcp message (timeout 20ms)... set RECONNECT_TCP=true if problem

			if(the_ioio != null) the_ioio.set_PWM_values(pwm_motor, pwm_servo);			//set pwm values, wake up ioio thread 
		}

		stop_tcp();
		stop_all();
	}
	
	public synchronized void stop_thread()
	{
		STOP = true;
	}

	private void stop_all()
	{
		stop_camera();
		stop_sensors();
		stop_IOIO();
	}

	/********************************************************************************************************************************************************************/
	/***************************************************************   TCP   ***************************************************************/	
	/********************************************************************************************************************************************************************/
	private void start_tcp()
	{
		while(RECONNECT_TCP==true && STOP==false)			//try to reconnect
		{
			serverAddr_TCP = new InetSocketAddress(ip_address_server,port_TCP);
			try 
			{				
				the_TCP_socket = new Socket();	
				the_TCP_socket.connect(serverAddr_TCP, 5000);				//connect timeout  (ms)
				the_TCP_socket.setSoTimeout(10);							//read timeout  (ms)
				out = new BufferedWriter(new OutputStreamWriter(the_TCP_socket.getOutputStream()));
				input = new BufferedReader(new InputStreamReader(the_TCP_socket.getInputStream()));

				RECONNECT_TCP = false;

				the_gui.runOnUiThread(new Runnable() 
				{
					@Override
					public void run() 
					{
						if(the_gui.button_connect.isChecked()==false) the_gui.button_connect.setChecked(true);                
					}
				}); 

				out.write(message_TCP);
				out.flush();
				//				Log.i("tcp","send msg " + message_TCP);
			}
			catch(java.io.IOException e) 
			{
				RECONNECT_TCP = true;
				Log.e("tcp","error connect: " + e);
			}
		}
	}

	private void stop_tcp()
	{
		try 
		{	
			if(out != null)		out.close();
			if(input != null)	input.close();
			the_TCP_socket.close();				//Close connection
		} 
		catch (IOException e) {	Log.e("tcp","error close: ", e);}
		Log.i("tcp","tcp client stopped ");
	}

	private void read_tcp()
	{
		counter_TCP_check++;
		if(counter_TCP_check==500)	//every 5s approx.
		{
			counter_TCP_check=0;
			try 
			{
				out.write("TCP_CHECK");
				out.flush();
			} 
			catch (IOException e) 					//if connection is lost	
			{	
				Log.e("tcp","error write: ", e); 
				stop_tcp();							//close properly
				stop_all();
				RECONNECT_TCP = true;
				start_tcp();
			} 			
		}			

		if(RECONNECT_TCP==false)
		{
			String st=null;		
			try
			{					
				st = input.readLine();
			}
			catch (java.net.SocketTimeoutException e) {}	//at every timeout
			catch (IOException e) 	{		Log.e("tcp","error read: ", e);	}

			if(st != null)
			{	        	
				final String[]sss= st.split("/");
				if(sss[0].matches("TCP_OK") == true)
				{
					RECONNECT_TCP = false;	//the server replied so no need to reconnect
				}
				else if(sss[0].matches("PWM") == true)
				{		
					pwm_motor = Integer.parseInt(sss[1]);
					pwm_servo = Integer.parseInt(sss[2]);
				}
				else if(sss[0].matches("CAMERA_ON") == true)
				{	        		
					Log.i(TAG,"start cam");	      
					port_camera = Integer.parseInt(sss[1]);
					idx_size_cam = Integer.parseInt(sss[2]);
					start_camera();
				}
				else if(sss[0].matches("CAMERA_OFF") == true)
				{
					Log.i(TAG,"stop cam ");			
					stop_camera();
				}
				else if(sss[0].matches("IMG_RATE") == true)
				{	
					set_compression_rate(Integer.parseInt(sss[1]));
				}
				else if(sss[0].matches("SENSORS_ON") == true)
				{
					Log.i(TAG,"start sensors ");
					port_sensors = Integer.parseInt(sss[1]);
					start_sensors();
				}
				else if(sss[0].matches("SENSORS_OFF") == true)
				{
					Log.i(TAG,"stop sensors ");
					stop_sensors();
				}
				else if(sss[0].matches("IOIO_ON") == true)
				{				
					Log.i(TAG,"start ioio");	
					port_ioio = Integer.parseInt(sss[1]);
					INVERTED = (Byte.parseByte(sss[2])!=0);
					RC_MODE = (Byte.parseByte(sss[3])!=0);
					start_IOIO();
				}
				else if(sss[0].matches("IOIO_OFF") == true)
				{
					Log.i(TAG,"stop ioio ");	
					stop_IOIO();
				}
				else if(sss[0].matches("MODE") == true)
				{
					Log.i(TAG,"change mode");
					RC_MODE = (Byte.parseByte(sss[1])!=0);
				}
			}
		}
	}
	
	/********************************************************************************************************************************************************************/
	/***************************************************************   sensors   ***************************************************************/	
	/********************************************************************************************************************************************************************/
	private void start_sensors()
	{
		try
		{
			the_sensors = new Sensors_listener();
			the_GPS = new GPS_listener();	

			the_gui.runOnUiThread(new Runnable() 
			{
				@Override
				public void run() 
				{
					sensorManager = (SensorManager) the_gui.getSystemService(Context.SENSOR_SERVICE);
					compass = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);	
					accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
					gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
					sensorManager.registerListener(the_sensors, compass, SensorManager.SENSOR_DELAY_FASTEST);
					sensorManager.registerListener(the_sensors, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
					sensorManager.registerListener(the_sensors, gyro, SensorManager.SENSOR_DELAY_FASTEST);

					locationManager = (LocationManager) the_gui.getSystemService(Context.LOCATION_SERVICE);	
					locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, the_GPS);
				}
			});

			serverAddr = InetAddress.getByName(ip_address_server);
			socket_udp_sensors = new DatagramSocket();

			SENSORS_STARTED = true;
		}
		catch (Exception exception) {	Log.e(TAG, "Error: ", exception);}		
		NEW_DECLI = false;
	}

	private void stop_sensors()
	{
		if(socket_udp_sensors!= null) 
		{
			sensorManager.unregisterListener(the_sensors);
			locationManager.removeUpdates(the_GPS);	
			socket_udp_sensors.close();
			SENSORS_STARTED = false;
		}
	}

	private void get_sensors_data() 		//get new values from sensors
	{
		if(SENSORS_STARTED == true)
		{
			orientation = the_sensors.get_orientation();
			acceleration = the_sensors.get_acceleration();	
			gyroscope = the_sensors.get_gyro_values();			
			lastKnownLocation_GPS = the_GPS.get_gps_loc();

			if(lastKnownLocation_GPS != null)
			{
				latitude = lastKnownLocation_GPS.getLatitude();
				longitude = lastKnownLocation_GPS.getLongitude();
				altitude = lastKnownLocation_GPS.getAltitude();
				accuracy = lastKnownLocation_GPS.getAccuracy();

				if(NEW_DECLI==false)		//only get declination once...the robot won't move really far
				{
					NEW_DECLI = true;
					GeomagneticField geoField;
					geoField = new GeomagneticField(
							Double.valueOf(lastKnownLocation_GPS.getLatitude()).floatValue(),
							Double.valueOf(lastKnownLocation_GPS.getLongitude()).floatValue(),
							Double.valueOf(lastKnownLocation_GPS.getAltitude()).floatValue(),
							System.currentTimeMillis()
					);
					declination = geoField.getDeclination();				// used to correct the angle from magnetic north to true north
				}
				NEW_DATA_GPS = true;
			}
			else NEW_DATA_GPS = false;
		}
	}

	private void send_sensors_data() 
	{
		if(SENSORS_STARTED == true && socket_udp_sensors.isClosed()==false)
		{
			if(the_sensors.FIRST_TIME == false)
			{
				string_sensors_vals = "Azimuth: "+ Float.toString(orientation[0])+ "/Pitch: "+ Float.toString(orientation[1])+ "/Roll: " + Float.toString(orientation[2]) +
				"/Acceleration x: " + Float.toString(acceleration[0]) + "/Acceleration y: " + Float.toString(acceleration[1]) + "/Acceleration z: " + Float.toString(acceleration[2]) + 
				"/Angular speed x: " + Float.toString(gyroscope[0]) + "/Angular speed y: " + Float.toString(gyroscope[1]) + "/Angular speed z: " + Float.toString(gyroscope[2]) +
				"/Latitude: " + Double.toString(latitude)  + "/Longitude: " + Double.toString(longitude)  + "/Altitude: " + Double.toString(altitude)  + 
				"/Accuracy: " + Double.toString(accuracy);

				if(NEW_DATA_GPS == true) string_sensors_vals += "/" + the_GPS.cycle;

				try 
				{			 
					DatagramPacket packet = new DatagramPacket(string_sensors_vals.getBytes(), string_sensors_vals.length(), serverAddr, port_sensors);
					socket_udp_sensors.send(packet);
				} 
				catch (Exception e) {	Log.e(TAG, "Error send: ", e);}
			}
		}
	}

	/********************************************************************************************************************************************************************/
	/***************************************************************   IOIO   ***************************************************************/
	/********************************************************************************************************************************************************************/
	private void start_IOIO()
	{
		if(IOIO_STARTED == false)
		{
			try
			{
				serverAddr = InetAddress.getByName(ip_address_server);
				socket_udp_ioio = new DatagramSocket();

				ioio_helper.start();			
				IOIO_STARTED = true;	
			}
			catch (IOException exception) {	Log.e(TAG, "Error socket: ", exception);}
		}
	}

	private void stop_IOIO()
	{
		if(IOIO_STARTED == true)
		{
			if(socket_udp_ioio!= null)
			{
				socket_udp_ioio.close();
				socket_udp_ioio = null;
				ioio_helper.stop();
				ioio_helper.destroy();
			}
			the_ioio = null;
			RC_MODE=true;
			IOIO_STARTED = false;	
		}
	}

	private void get_ioio_data()
	{
		if(IOIO_STARTED == true && the_ioio != null)
		{
			IR_vals  = the_ioio.get_IR_values();

			if(IR_vals != null)
			{
				IR_left = IR_vals[0];
				IR_front_left = IR_vals[1];			
				IR_front_right = IR_vals[2];
				IR_right = IR_vals[3];
				NEW_DATA_IOIO = true;
			}
			else NEW_DATA_IOIO = false;			
		}
	}

	private void send_ioio_data() 
	{
		if(IOIO_STARTED == true && socket_udp_ioio!= null)
		{
			if(NEW_DATA_IOIO == true)
			{
				string_ioio_vals = "IR left: " + Float.toString(IR_left) + "/IR front left: " + Float.toString(IR_front_left) +
				"/IR front right: " + Float.toString(IR_front_right) + "/IR right: " + Float.toString(IR_right) +
				"/PWM motor: " + Float.toString(pwm_motor) +"/PWM servo: " + Float.toString(pwm_servo) + "/" + the_ioio.cycle;

				try 
				{
					DatagramPacket packet = new DatagramPacket(string_ioio_vals.getBytes(), string_ioio_vals.length(), serverAddr, port_ioio);
					socket_udp_ioio.send(packet);					//send data
				} 
				catch (IOException e) {Log.e(TAG,"error sending: ", e);}
			}
		}
	}

	/********************************************************************************************************************************************************************/
	/****************************************************** function from original IOIOActivity *********************************************************************************/
	/********************************************************************************************************************************************************************/
	@Override
	public IOIOLooper createIOIOLooper(String connectionType, Object extra) 
	{
		if(the_ioio == null && connectionType.matches("ioio.lib.android.bluetooth.BluetoothIOIOConnection"))
		{
			Log.i(TAG,"create ioio: " +  SystemClock.elapsedRealtime());
			the_ioio = new IOIO_thread();
			the_ioio.set_inverted(INVERTED);
			return the_ioio;
		}
		else return null;
	}

	/********************************************************************************************************************************************************************/
	/***************************************************************   camera   ***************************************************************/
	/********************************************************************************************************************************************************************/
	private void start_camera()
	{
		if(CAMERA_STARTED == false)
		{
			the_camera = new Camera_feedback(idx_size_cam);
			try
			{
				serverAddr = InetAddress.getByName(ip_address_server);
				socket_udp_camera = new DatagramSocket();
			}
			catch (Exception exception) {	Log.e(TAG, "Error: ", exception);}

			CAMERA_STARTED = true;
			NEW_IMA = true;
		}		
	}

	private void stop_camera()
	{
		if(CAMERA_STARTED == true)
		{
			the_camera.stop_camera();	

			if(socket_udp_camera!= null)
			{
				socket_udp_camera.close();
				socket_udp_camera=null;
			}
			NEW_FRAME = false;
			the_camera = null;			
			CAMERA_STARTED = false;
		}
	}

	private void set_compression_rate(int nb)
	{
		compression_rate = nb;
	}

	private void get_camera_data() 
	{
		if(CAMERA_STARTED == true && the_camera != null)
		{
			if(NEW_IMA==true)
			{
				width_ima = the_camera.mPreviewSize.width;
				height_ima = the_camera.mPreviewSize.height;		
				the_frame = new Mat(height_ima + height_ima / 2, width_ima, CvType.CV_8UC1);	//m will be YUV format

				dest = new Mat(64 + 32,80,CvType.CV_8UC1);	
				dest2 = new Mat();
				byteStream = new ByteArrayOutputStream();
				compression_rate = 75;					// default jpeg compression rate
				NEW_IMA=false;

				Log.i(TAG, "new ima");
			}			

			byte[] data = the_camera.get_data();
			if(data != null)
			{
				the_frame.put(0, 0, data);
				//				Imgproc.cvtColor(m, dest, Imgproc.COLOR_YUV420sp2RGB,4);	//YUV to ARGB

				Imgproc.resize(the_frame, dest, dest.size());
				Imgproc.cvtColor(dest, dest2, Imgproc.COLOR_YUV420sp2GRAY);		//format to grayscale				

				/** compress to jpeg using opencv...not sure it's faster than using Bitmap Compress**/
				MatOfInt  params = new MatOfInt(Highgui.IMWRITE_JPEG_QUALITY, compression_rate);				
				MatOfByte buff = new MatOfByte();	
				Highgui.imencode(".jpg", dest2, buff, params);				
				/************************/

				picData = buff.toArray();
				NEW_FRAME = true;
			}
			else NEW_FRAME = false;
		}
	}

	private void send_camera_data()
	{
		if(NEW_FRAME == true && socket_udp_camera!=null)
		{				
			nb_packets = (int)Math.ceil(picData.length / (float)DATAGRAM_MAX_SIZE);				//Number of packets used for this bitmap
			size = DATAGRAM_MAX_SIZE;

			for(packetCount = 0; packetCount < nb_packets; packetCount++)						// Loop through slices of the bitmap
			{
				//If last or only one packet: set packet size to what's left of data
				if(packetCount == nb_packets-1)	size = picData.length - packetCount * DATAGRAM_MAX_SIZE;

				/* create own header */
				byte[] data2 = new byte[HEADER_SIZE + size];
				data2[0] = (byte)frame_nb;
				data2[1] = (byte)nb_packets;
				data2[2] = (byte)packetCount;

				System.arraycopy(picData, packetCount * DATAGRAM_MAX_SIZE, data2, HEADER_SIZE, size);	// Copy current slice to byte array		
				try 
				{			
					DatagramPacket packet = new DatagramPacket(data2, data2.length, serverAddr, port_camera);
					socket_udp_camera.send(packet);
				}catch (Exception e) {	Log.e(TAG, "Error: ", e);}	
			}
			frame_nb++;

			if(frame_nb == 127)frame_nb=0;
		}
	}
}