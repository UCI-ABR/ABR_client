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
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfInt;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;

import android.content.Context;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.util.Log;
import carl.abr.IO.Camera_feedback;
import carl.abr.IO.GPS_listener;
import carl.abr.IO.Sensors_listener;
import carl.abr.gui.Main_activity;

/** 
 * Main thread that runs the main loop of the app ({@link #run()}). Is used to: <br>
 * - connect to the server over TCP socket <br>
 * - start/stop sensors, camera, and ioio <br>
 * - get data from sensors, camera, ioio  <br>
 * - process data  <br>
 * - send data over UDP  <br>
 * - get TCP commands from server  <br>
 * - set motor command ioio
 * @see {@link #start_tcp()}, {@link #start_sensors()}, {@link #start_camera()}, {@link #start_IOIO()}, {@link #get_sensors_data()}, {@link #get_camera_data()}, 
 * {@link #get_ioio_data()}, {@link #send_sensors_data()}, {@link #send_camera_data()}, {@link #send_ioio_data()}, {@link #stop_sensors()}, {@link #stop_camera()}, {@link #stop_IOIO()}
 * 
 */	
public class Main_thread extends Thread implements IOIOLooperProvider 		// implements IOIOLooperProvider: from IOIOActivity
{
	static final String TAG = "main_thread";
	/** Reference to the main activity*/
	Main_activity the_gui;	

	//***************************************************************   synchronization / booleans  ***************************************************************/
	/** true: thread should be stopped (exit main loop in function {@link #run()})*/
	boolean STOP 			= false;
	
	/** true: reconnect to tcp server. See:  {@link #start_tcp()}, {@link #read_tcp()}*/
	boolean RECONNECT_TCP	= true;
	
	/** true: sensors listeners have been created and attached (started), See:  {@link #start_sensors()}, {@link #stop_sensors()()}*/
	boolean SENSORS_STARTED	= false;
	
	/** true: ioio thread has been been created and started. See:  {@link #start_IOIO()}, {@link #stop_IOIO()}*/
	boolean IOIO_STARTED	= false;
	
	/** true: camera feedback has been been created and started. See {@link #start_camera()}, {@link #stop_camera()}*/
	boolean CAMERA_STARTED	= false;
	
	/** true: when camera starts, a new image ({@link #the_frame}) will be created <br> false: the same image is then modified. <br> See {@link #start_camera()}, {@link #get_camera_data()}*/
	boolean NEW_IMA			= true;	
	
	/** true: a new frame is available after calling {@link Camera_feedback#get_data()} in {@link #get_camera_data()}.  <br><br> See {@link #start_camera()}, {@link #get_camera_data()}*/
	boolean NEW_FRAME		= false;
	
	/** true: new data from the IOIO is available after calling {@link IOIO_thread#get_IR_values()} in {@link #get_ioio_data()}.  <br><br> See {@link #get_ioio_data()}*/
	boolean NEW_DATA_IOIO	= false;
	
	/** true: new data from the GPS is available after calling {@link GPS_listener#get_location()} in {@link #get_sensors_data()}.  <br><br> See {@link #get_sensors_data()}*/
	boolean NEW_DATA_GPS	= false;
	
	/** true: used to get the earth declination only once in {@link #get_sensors_data()}.  <br><br> See {@link #get_sensors_data()}*/
	boolean NEW_DECLI		= false;
	
	/** true: RC mode for robot. Commands are sent over network and received when reading TCP socket in {@link #read_tcp()} <br><br> See {@link #read_tcp()}*/
	boolean RC_MODE 		= true;
	
	/** true: explore mode for robot. Function to be implemented {@link #read_tcp()} <br><br> See {@link #read_tcp()}*/
	boolean EXPLORE_MODE	= true;
	
	/** true if the pwm values should be inverted (e.g. for some cars: 2000= right, others 1000=right). <br> See {@link #read_tcp()} , {@link IOIO_thread#INVERTED}*/
	boolean INVERTED 		= false;
	
	/** true: send color image to server. <br>false: send black and white image. <br>See {@link #get_camera_data()}*/
	boolean COLOR_MODE 		= true;	
	
	/** true: resize image sent to server. <br>See {@link #get_camera_data()}*/
	boolean RESIZE_IMA 		= false;
	
	
	//***************************************************************   camera   ***************************************************************/
	/** Used to get the frame from the camera. <br> See {@link Camera_feedback}*/
	Camera_feedback the_camera;
	
	/** Index used to set size of image (resolution) of the camera. <br> See {@link #start_camera()}*/
	int idx_size_cam;
	
	/**  Index number of the actual frame (reset to 0 after 126)*/
	byte idx_frame = 0;
	
	/**  Resolution / size of frame*/
	int width_ima, height_ima;
	
	/**  Array containing video frame after jpeg compression. This array is being sent to server over udp socket <br> See {@link #get_camera_data()} , {@link #send_camera_data()}*/
	byte[] data_frame;
	
	/** JPEG Compression rate / quality*/
	int compression_rate;
	
	/** openCV images used containing video frame before and after image processing. <br> See {@link #get_camera_data()}*/
	Mat the_frame,dest, dest2;						

	//*****************************************   IOIO    ***************************************************************/
	/** Used to connect to the IOIO*/
	IOIOAndroidApplicationHelper ioio_helper;
	
	/** Used to open the pins of the IOIO, and read/write values. <br> See {@link IOIO_thread}*/
	IOIO_thread the_ioio;
	
	/** values of the pulse width of the PWM signals. Sent by server in: read_tcp()*/
	int min_servo, min_motor, max_servo, max_motor, default_servo, default_motor;
	
	/** Pulse width of the pwm signal used to control the servo.*/
	float pwm_servo;
	
	/** Pulse width of the pwm signal used to control the motor.*/
	float pwm_motor;	
	
	/** Values read from the IR sensors.*/
	float IR_right, IR_left, IR_front_left, IR_front_right; 
	
	/** Values read from the IR sensors.*/
	float[] IR_vals;
	
	/** Pulse width of the pwm signal used to control the motor and servo.*/
	float[] PWM_vals;

	//*****************************************   sensors  & GPS  ***************************************************************/
	/** Used to have a access to the phone's sensors. <br> See {@link SensorManager}*/ 
	SensorManager sensorManager;
	
	/** Sensors of the phone being used.*/
	Sensor compass, accelerometer, gyro;
	
	/** Used to have access to the location (e.g. GPS) of the phone. <br> See  {@link LocationManager}*/
	LocationManager locationManager;
	
	/** Listener used to have access to the phone's sensors. <br> See {@link Sensors_listener}*/ 
	Sensors_listener the_sensors;
	
	/** Listener used to have access to the phone's location. <br> See {@link GPS_listener}*/ 
	GPS_listener the_GPS;
	
	/** Acceleration values of the phone. Set using {@link Sensors_listener#get_acceleration()}*/
	float[] acceleration;
	
	/** Orientation values of the phone. Set using {@link Sensors_listener#get_orientation()}*/
	float[] orientation;
	
	/**  Rate of rotation  of the phone. Set using {@link Sensors_listener#get_gyro_values()}*/
	float[] gyroscope;
	
	/** Locations of the phone.  Set using {@link GPS_listener#get_location()}*/
	Location lastKnownLocation_GPS, target_location;
	
	/** attributes of the last GPS fix / location*/
	double latitude, longitude, altitude, accuracy;
	
	/** Declination of the horizontal component of the magnetic field from true north. Set using {@link GeomagneticField#getDeclination()}*/
	float declination;

	//*****************************************   UDP   ***************************************************************/
	/** Size of the header of each UDP packet sent to the server. ONLY used for sending the video frame. <br> See {@link #send_camera_data()}*/
	static int HEADER_SIZE 			= 5;
	
	/** Maximum size of a UDP packet sent to the server. ONLY used for sending the video frame. <br> See {@link #send_camera_data()}*/
	static int DATAGRAM_MAX_SIZE 	= 1450 - HEADER_SIZE;		
	
	/** IP address of the server.*/
	InetAddress serverAddr;
	
	/** IP address (as a String) of the server.*/
	String ip_address_server;	
	
	/** UDP sockets used to send data to the server.*/
	DatagramSocket socket_udp_ioio, socket_udp_sensors, socket_udp_camera;
	
	/** Port opened for the UDP sockets used to send data to the server.*/
	int port_ioio, port_sensors, port_camera;	

	//*****************************************   TCP   ***************************************************************/
	/** Port opened for the TCP socket used to connect to the server.*/
	int port_TCP;
	
	/** TCP socket*/
	Socket the_TCP_socket;
	
	/** IP address of the server.*/
	InetSocketAddress serverAddr_TCP;
	
	/** Used to write to the TCP socket.*/
	BufferedWriter out;
	
	/** Used to read on the TCP socket.*/
	BufferedReader input;
	
	/** Counter used to check the state of the TCP connection to the server. <br> See {@link #check_tcp()}*/
	int counter_TCP_check			= 0;
	
	/**  Check tcp connection to the server every 500 timesteps. <br> See {@link #check_tcp()}*/
	static int TCP_CHECK_RATE		= 500;
	
	/**  Timeout (ms) for connecting tcp socket <br> See {@link #start_tcp()}*/
	static int CONNECT_TIMEOUT 		= 5000;
	
	/**  Timeout (ms) when reading on tcp socket...also used as a wait()/sleep() for main loop<br> See {@link #start_tcp()}*/
	static int READ_TIMEOUT 		= 20;	

	
	/** 
	 * Constructor main thread.
	 * */
	public Main_thread(Main_activity gui)
	{
		the_gui = gui;
		ip_address_server = the_gui.IP_server;	
		port_TCP = the_gui.port_TCP;
	}
	

	/** 
	 * Loop of the main thread. This run function is called when: the_main_thread.start();  is called in the Main_activity. <br><br>
	 * First, this thread loads the openCV libraries, then connects to the TCP server ({@link #start_tcp()}).<br><br>
	 * Then it collects data from sensors, GPS, camera, IRs ({@link #get_sensors_data()}, {@link #get_camera_data()}, {@link #get_ioio_data()}). <br><br>
	 * It then performs some computation (e.g. autonomous behavior), then sends data to the server ({@link #send_sensors_data()}, {@link #send_camera_data()}, {@link #send_ioio_data()}).<br><br>
	 * It then checks the status of the TCP connection {@link #check_tcp()} and read any messages sent over the TCP socket {@link #read_tcp()}. <br><br>
	 * Finally, it updates the IOIO motor commands ({@link IOIO_thread#set_PWM_values(float, float)}). <br><br>
	 * This thread performed video streaming by capturing frames from the camera, which were then converted 
	 * from YUV to RGB using OpenCV, compressed into JPEG images in {@link #get_camera_data()}, sliced into UDP packets, and sent over Wi-Fi/3G/4G to the server in {@link #send_camera_data()}.
	 * 
	 * @see {@link #start_tcp()}, {@link #get_sensors_data()}, {@link #get_camera_data()}, {@link #get_ioio_data()}, {@link #send_sensors_data()}, {@link #send_camera_data()}, {@link #send_ioio_data()}
	 * 
	 */	
	@Override
	public final void run() 
	{	
		load_opencv();							// load opencv libraries for image processing
		start_tcp();							// connect to the server

		while(STOP == false)
		{		
			synchronized(this)					//thread cannot be stopped by activity (user) while running this part of the code
			{
				//get (update) data
				get_sensors_data();
				get_camera_data();
				get_ioio_data();

				//do stuff with data here...
				if (EXPLORE_MODE == true)	explore();

				//send data to server (udp sockets)
				send_sensors_data();
				send_camera_data();
				send_ioio_data();
			}
			check_tcp();		
			read_tcp();					//read tcp message (timeout 20ms)... set RECONNECT_TCP=true if problem

			if(the_ioio != null) the_ioio.set_PWM_values(pwm_motor, pwm_servo);			//set pwm values, wake up ioio thread 
		}

		stop_tcp();
		stop_all();
	}

	/** stops the thread*/
	public synchronized void stop_thread()
	{
		STOP = true;
	}

	/** Stop camera, ioio and sensors (if running), by calling {@link #stop_camera()} , {@link #stop_IOIO()} and {@link #stop_sensors()}.
	 * Called when tcp connection is lost in {@link #check_tcp()}, and when the thread is stopping in {@link #run()}.
	 * @see #stop_camera()
	 * @see #stop_IOIO() 
	 * @see #stop_sensors()*/
	private void stop_all()
	{
		stop_camera();
		stop_sensors();
		stop_IOIO();
	}
	
	
	//********************************************************************************************************************************************************************/
	//***********************************************************************   TCP  *************************************************************************************/
	//********************************************************************************************************************************************************************/

	/** 
	 * Function will try to connect to the server indefinitely. Creates and open a tcp socket, and input and output streams.
	 * If connected, sends robot/phone parameters to the server by calling {@link #send_param_tcp()}.
	 * @see #send_param_tcp() 
	 * @param no param	 
	 * */
	private void start_tcp()
	{
		while(RECONNECT_TCP==true && STOP==false)			//try to (re)connect
		{
			serverAddr_TCP = new InetSocketAddress(ip_address_server,port_TCP);
			try 
			{				
				the_TCP_socket = new Socket();	
				the_TCP_socket.connect(serverAddr_TCP, CONNECT_TIMEOUT);				//connect with timeout  (ms)
				the_TCP_socket.setSoTimeout(READ_TIMEOUT);								//read with timeout  (ms)
				out = new BufferedWriter(new OutputStreamWriter(the_TCP_socket.getOutputStream()));
				input = new BufferedReader(new InputStreamReader(the_TCP_socket.getInputStream()));
				
				RECONNECT_TCP = !send_param_tcp();		//send parameters of the phone/robot... if problem, reconnect

				the_gui.runOnUiThread(new Runnable() //update gui on its own thread
				{
					@Override
					public void run() 
					{
						if(the_gui.button_connect.isChecked()==false) the_gui.button_connect.setChecked(true);                
					}
				}); 
			}
			catch(java.io.IOException e) 
			{
				RECONNECT_TCP = true;
//				Log.e("tcp","error connect: " + e);
			}
		}
	}

	/**
	 * Close input and output streams, and close the tcp socket.
	 * */
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
	

	/**Send parameters of the phone/robot to the server (name, supported resolution camera...).
	 * Should only be called once (the first time the robot connects to the server).  
	 * @return true if message correctly sent, false otherwise
	 * */
	private boolean send_param_tcp()
	{
		String message_TCP = new String();
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
			
			out.write(message_TCP);
			out.flush();
			return true;
		}
		catch(Exception e)
		{
			Log.e(TAG, "error send_param_tcp" + e); 
			return false;
		}
	}
	
	/**
	 * Checks tcp connection by trying to send the message "TCP_CHECK" to the server every KEEPALIVE_MAX_COUNT (e.g. 500) timesteps.
	 * If disconnected, function will stop everything and try to reconnect to the server using: {@link #stop_tcp()}, {@link #stop_all()}, then {@link #start_tcp()}
	 * <p> The server should send back "TCP_OK" but we don't really care about it here, see {@link #read_tcp()}
	 * @see {@link #read_tcp()} , {@link #stop_tcp()}, {@link #stop_all()}, {@link #start_tcp()}
	 *  */	
	private void check_tcp()
	{
		counter_TCP_check++;
		if(counter_TCP_check==TCP_CHECK_RATE)	
		{
			counter_TCP_check=0;
			try 
			{
				out.write("TCP_CHECK");
				out.flush();				
			} 
			catch (IOException e) 					// if connection is lost	...cannot send/write on socket
			{	
				Log.e("tcp","error write: ", e); 
				RECONNECT_TCP = true;				
			} 		
			
			if(RECONNECT_TCP == true)
			{
				Log.i("tcp","reconnect");
				stop_tcp();							// close properly
				stop_all();							// stop everything
				start_tcp();						// reconnect to server
			}
		}	
	}

	/**
	 * Read tcp socket and perform action corresponding to message: start/stop camera, ioio, sensors, set pwm values, compression rate, mode.
	 * Also see if the server sent back "TCP_OK" message after sending "TCP_CHECK" in {@link #check_tcp()}
	 * @return true if it receives a known command, false otherwise
	 * @see #check_tcp()
	 * */	
	private boolean read_tcp()
	{
		String st=null;		
		boolean output = false; 
		
		try
		{					
			st = input.readLine();
		}
		catch (java.net.SocketTimeoutException e) {}	//exception will be caught at every timeout (so very often)
		catch (IOException e) {	Log.e("tcp","error read: ", e);	}

		if(st != null)
		{	        	
			final String[]sss= st.split("/");
			output = true;
			
			if(sss[0].matches("TCP_OK") == true){}	//the server replied...good but don't really care actually
			else if(sss[0].matches("PWM") == true)
			{		
				pwm_motor 		= Integer.parseInt(sss[1]);
				pwm_servo 		= Integer.parseInt(sss[2]);
			}
			else if(sss[0].matches("CAMERA_ON") == true)
			{	        		 
				port_camera 	= Integer.parseInt(sss[1]);
				idx_size_cam 	= Integer.parseInt(sss[2]);
				COLOR_MODE		= (Byte.parseByte(sss[3])!=0);
				RESIZE_IMA		= (Byte.parseByte(sss[4])!=0);
				width_ima 		= Integer.parseInt(sss[5]);
				height_ima		= Integer.parseInt(sss[6]);
				start_camera();
			}
			else if(sss[0].matches("CAMERA_OFF") == true)
			{			
				stop_camera();
			}
			else if(sss[0].matches("IMG_RATE") == true)
			{	
				set_compression_rate(Integer.parseInt(sss[1]));
			}
			else if(sss[0].matches("SENSORS_ON") == true)
			{
				port_sensors 	= Integer.parseInt(sss[1]);
				start_sensors();
			}
			else if(sss[0].matches("SENSORS_OFF") == true)
			{
				stop_sensors();
			}
			else if(sss[0].matches("IOIO_ON") == true)
			{				
				port_ioio 		= Integer.parseInt(sss[1]);
				INVERTED 		= (Byte.parseByte(sss[2])!=0);
				RC_MODE 		= (Byte.parseByte(sss[3])!=0);
				EXPLORE_MODE 	= (Byte.parseByte(sss[4])!=0);
				min_servo		= Integer.parseInt(sss[5]);
				min_motor		= Integer.parseInt(sss[6]);
				max_servo		= Integer.parseInt(sss[7]);
				max_motor		= Integer.parseInt(sss[8]);
				default_servo	= Integer.parseInt(sss[9]);
				default_motor	= Integer.parseInt(sss[10]);
				start_IOIO();
			}
			else if(sss[0].matches("IOIO_OFF") == true)
			{
				stop_IOIO();
			}
			else if(sss[0].matches("MODE") == true)
			{
				RC_MODE 		= (Byte.parseByte(sss[1])!=0);
				EXPLORE_MODE 	= (Byte.parseByte(sss[2])!=0);
			}
			else if(sss[0].matches("COLOR") == true)
			{
				COLOR_MODE 		= (Byte.parseByte(sss[1])!=0);
			}
			else
				output 			= false;		//if unknown command sent
		}
		return output;
	}

	
	//********************************************************************************************************************************************************************/
	//******************************************************************   sensors and GPS   *****************************************************************************/
	//********************************************************************************************************************************************************************/

	/**
	 * Starts sensors and GPS: creates a new {@link Sensors_listener} and a new {@link GPS_listener}} as well as a new UDP socket to send data to server.
	 * Called when server sends message over TCP socket. See {@link #read_tcp()}
	 * @see  {@link #read_tcp()} {@link #get_sensors_data()} {@link #send_sensors_data()} {@link #stop_sensors()}
	 * */	
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
	
	/**
	 * Stops sensors and GPS and close UDP socket.
	 * @see  {@link #read_tcp()} {@link #get_sensors_data()} {@link #send_sensors_data()} {@link #start_sensors()}
	 * */	
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
	
	/**
	 * Get values from sensors and GPS listeners: {@link #the_sensors} {@link #the_GPS}.
	 * @see {@link #send_sensors_data()} {@link #start_sensors()}
	 * */	
	private void get_sensors_data() 		//get new values from sensors
	{
		if(SENSORS_STARTED == true)
		{
			orientation = the_sensors.get_orientation();
			acceleration = the_sensors.get_acceleration();	
			gyroscope = the_sensors.get_gyro_values();			
			lastKnownLocation_GPS = the_GPS.get_location();

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

	/**
	 * Send values of sensors and GPS listeners to the server as a string.
	 * @see {@link #get_sensors_data()} {@link #start_sensors()}
	 * */	
	private void send_sensors_data() 
	{
		if(SENSORS_STARTED == true && socket_udp_sensors.isClosed()==false)
		{
			String string_sensors_vals = "Azimuth: "+ Float.toString(orientation[0])+ "/Pitch: "+ Float.toString(orientation[1])+ "/Roll: " + Float.toString(orientation[2]) +
					"/Acceleration x: " + Float.toString(acceleration[0]) + "/Acceleration y: " + Float.toString(acceleration[1]) + "/Acceleration z: " + Float.toString(acceleration[2]) + 
					"/Angular speed x: " + Float.toString(gyroscope[0]) + "/Angular speed y: " + Float.toString(gyroscope[1]) + "/Angular speed z: " + Float.toString(gyroscope[2]) +
					"/Latitude: " + Double.toString(latitude)  + "/Longitude: " + Double.toString(longitude)  + "/Altitude: " + Double.toString(altitude)  + 
					"/Accuracy: " + Double.toString(accuracy);

			try 
			{			 
				DatagramPacket packet = new DatagramPacket(string_sensors_vals.getBytes(), string_sensors_vals.length(), serverAddr, port_sensors);
				socket_udp_sensors.send(packet);
			} 
			catch (Exception e) {	Log.e(TAG, "Error send: ", e);}
		}
	}

	//********************************************************************************************************************************************************************/
	//***********************************************************************   IOIO   ***********************************************************************************/
	//********************************************************************************************************************************************************************/
	
	/**
	 * Start IOIO as well as a new UDP socket to send data to server. <br>
	 * Start a {@link IOIOAndroidApplicationHelper} that will call {@link #createIOIOLooper(String, Object)} to create and start a {@link IOIO_thread}.
	 * Called when server sends message over TCP socket. See {@link #read_tcp()}.
	 * @see  {@link #read_tcp()} {@link #get_ioio_data()} {@link #send_ioio_data()} {@link #stop_IOIO()}
	 * */
	private void start_IOIO()
	{
		if(IOIO_STARTED == false)
		{
			try
			{
				serverAddr = InetAddress.getByName(ip_address_server);
				socket_udp_ioio = new DatagramSocket();
				
				ioio_helper = new IOIOAndroidApplicationHelper(the_gui, this);	// create ioio_helper used to connect to the ioio (copied from IOIOActivity)				
				ioio_helper.create();											// copied from IOIOActivity
				ioio_helper.start();			
				IOIO_STARTED = true;	
			}
			catch (IOException exception) {	Log.e(TAG, "Error socket: ", exception);}
		}
	}
	
	/**
	 * Stop IOIO and close UDP socket..
	 * Called when server sends message over TCP socket. See {@link #read_tcp()}
	 * @see  {@link #read_tcp()} {@link #get_ioio_data()} {@link #send_ioio_data()} {@link #start_IOIO()}
	 * */
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

	/**
	 * Get IR values from the IOIO thread.
	 * @see {@link #send_ioio_data()} {@link #start_IOIO()}
	 * */
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

	/**
	 * Send IR values from the IOIO thread to the server over UDP.
	 * @see {@link #get_ioio_data()} {@link #start_IOIO()}
	 * */
	private void send_ioio_data() 
	{
		if(IOIO_STARTED == true && socket_udp_ioio!= null)
		{
			if(NEW_DATA_IOIO == true)
			{
				String string_ioio_vals = "IR left: " + Float.toString(IR_left) + "/IR front left: " + Float.toString(IR_front_left) +
						"/IR front right: " + Float.toString(IR_front_right) + "/IR right: " + Float.toString(IR_right) +
						"/PWM motor: " + Float.toString(pwm_motor) +"/PWM servo: " + Float.toString(pwm_servo);

				try 
				{
					DatagramPacket packet = new DatagramPacket(string_ioio_vals.getBytes(), string_ioio_vals.length(), serverAddr, port_ioio);
					socket_udp_ioio.send(packet);					//send data
				} 
				catch (IOException e) {Log.e(TAG,"error sending: ", e);}
			}
		}
	}
	
	/**
	 * TODO: get sensors values, process and output motor commands to generate exploration behavior
	 * @see {@link #get_ioio_data()} {@link #start_IOIO()} 
	 * */
	private void explore()
	{
		//TODO: get sensors values , process and set ioio values to generate autonomous behavior
	}
	
	/**
	 * Create the  {@link IOIO_thread}. Called by the {@link IOIOAndroidApplicationHelper}. <br>
	 * Function copied from original IOIOActivity.
	 * @see {@link #get_ioio_data()} {@link #start_IOIO()} 
	 * */
	@Override
	public IOIOLooper createIOIOLooper(String connectionType, Object extra) 
	{
		if(the_ioio == null && connectionType.matches("ioio.lib.android.bluetooth.BluetoothIOIOConnection"))
		{
//			Log.i(TAG,"create ioio: " +  SystemClock.elapsedRealtime());
			the_ioio = new IOIO_thread(default_servo, default_motor);
			the_ioio.set_inverted(INVERTED);
			return the_ioio;
		}
		else return null;
		
//		//for any type of connection: ADB, OpenAccessory, Bluetooth   (will create one thread for each possible connection)
//		Log.i(TAG,"create ioio: " +  SystemClock.elapsedRealtime());
//		the_ioio = new IOIO_thread(default_servo, default_motor);
//		the_ioio.set_inverted(INVERTED);
//		return the_ioio;
	}

	//********************************************************************************************************************************************************************/
	//********************************************************************************   camera   ************************************************************************/
	//********************************************************************************************************************************************************************/
	/**
	 * Start Camera: create a new {@link Camera_feedback} object as well as a new UDP socket to send data to server. <br>
	 * Called when server sends message over TCP socket. See {@link #read_tcp()}.
	 * @see  {@link #read_tcp()} {@link #get_camera_data()} {@link #send_camera_data()} {@link #stop_camera()}
	 * */
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

	/**
	 * Stop Camera and close UDP socket.<br>
	 * Called when server sends message over TCP socket. See {@link #read_tcp()}.
	 * @see  {@link #read_tcp()} {@link #get_camera_data()} {@link #send_camera_data()} {@link #start_camera()}
	 * */
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

	/**
	 * Set rate for jpeg compression (changing quality) done in   {@link #get_camera_data()}.<br>
	 * Called when server sends message over TCP socket. See {@link #read_tcp()}.
	 * @param rate : jpeg compression rate (quality)
	 * @see  {@link #read_tcp()} {@link #get_camera_data()} {@link #send_camera_data()} {@link #start_camera()}
	 * */
	private void set_compression_rate(int rate)
	{
		compression_rate = rate;
	}

	/**
	 * Get camera frame from {@link Camera_feedback} by calling {@link Camera_feedback#get_data()} and use openCV to change format from YUV to RGB or grayscale and compress it to jpeg.<br>
	 * @see {@link #send_camera_data()} {@link #start_camera()}
	 * */
	private void get_camera_data() 
	{
		if(CAMERA_STARTED == true && the_camera != null)
		{
			if(NEW_IMA==true)
			{	
				compression_rate = 75;					// default jpeg compression rate
				the_frame = new Mat(the_camera.mPreviewSize.height + the_camera.mPreviewSize.height / 2, the_camera.mPreviewSize.width, CvType.CV_8UC1);	//the_frame will be YUV format
				dest = new Mat();
				
				if(RESIZE_IMA) 		//if image has to be resized, use this temp image
					dest2 = new Mat(height_ima,width_ima,CvType.CV_8UC1);				
				
				NEW_IMA=false;
				Log.i(TAG, "new ima");
			}			

			byte[] data = the_camera.get_data();
			if(data != null)
			{
				the_frame.put(0, 0, data);
				
				if(COLOR_MODE)	Imgproc.cvtColor(the_frame, dest, Imgproc.COLOR_YUV420sp2RGB);	//YUV to ARGB				
				else			Imgproc.cvtColor(the_frame, dest, Imgproc.COLOR_YUV420sp2GRAY);	//format to grayscale	
				
				if(RESIZE_IMA)	Imgproc.resize(dest, dest2, dest2.size());						//if image has to be resized
				else 			dest2 = dest;

				// compress to jpeg using opencv...not sure it's faster than using Bitmap Compress
				MatOfInt  params = new MatOfInt(Highgui.IMWRITE_JPEG_QUALITY, compression_rate);		
				MatOfByte buff = new MatOfByte();
				Highgui.imencode(".jpg", dest2, buff, params);	//actually return data in BGR format (default openCV format)
				//************************/
				
				data_frame = buff.toArray();
				NEW_FRAME = true;
			}
			else NEW_FRAME = false;
		}
	}

	/**
	 * Send camera frame to server over UDP socket. Slice the image into smaller packets.
	 * @see {@link #get_camera_data()} {@link #start_camera()}
	 * */
	private void send_camera_data()
	{
		if(NEW_FRAME == true && socket_udp_camera!=null)
		{				
			int nb_packets = (int)Math.ceil(data_frame.length / (float)DATAGRAM_MAX_SIZE);				//Number of packets used for this bitmap
			int size = DATAGRAM_MAX_SIZE;

			for(int packetCount = 0; packetCount < nb_packets; packetCount++)						// Loop through slices of the bitmap
			{
				//If last or only one packet: set packet size to what's left of data
				if(packetCount == nb_packets-1)	size = data_frame.length - packetCount * DATAGRAM_MAX_SIZE;

				/* create own header */
				byte[] data2 = new byte[HEADER_SIZE + size];
				data2[0] = (byte)idx_frame;
				data2[1] = (byte)nb_packets;
				data2[2] = (byte)packetCount;

				System.arraycopy(data_frame, packetCount * DATAGRAM_MAX_SIZE, data2, HEADER_SIZE, size);	// Copy current slice to byte array		
				try 
				{			
					DatagramPacket packet = new DatagramPacket(data2, data2.length, serverAddr, port_camera);
					socket_udp_camera.send(packet);
				}catch (Exception e) {	Log.e(TAG, "Error: ", e);}	
			}
			idx_frame++;

			if(idx_frame == 127)idx_frame=0;
		}
	}
	
	/**
	 * Load openCV libraries 2.4.8.
	 * */
	private void load_opencv()
	{
		BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(the_gui) {
			@Override
			public void onManagerConnected(int status) 
			{
				switch (status) 
				{
				case LoaderCallbackInterface.SUCCESS:
					Log.i(TAG, "OpenCV loaded successfully");
					break;
				default:
					super.onManagerConnected(status);
					break;
				}
			}
		};		
		if (!OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_8, the_gui, mLoaderCallback)) // load opencv 2.4.8 libraries
			Log.e(TAG, "Cannot connect to OpenCV Manager");		
	}
}