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
import java.util.Vector;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfInt;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;

import android.graphics.Bitmap;
import android.hardware.Camera;
import android.hardware.GeomagneticField;
import android.hardware.Camera.Size;
import android.location.Location;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import carl.abr.IO.Camera_feedback;
import carl.abr.IO.GPS_listener;
import carl.abr.IO.Sensors_listener;
import carl.abr.gui.Main_activity;
import carl.abr.utils.File_RW;

public class Main_thread extends Thread 
{
	static final String TAG = "main_thread";
	Main_activity the_gui;	
	boolean STOP = false, NEW_IMA=true, SENSORS_SOCKET=false, RECONNECT_TCP=true;
	long old_time,cycle, time;
	float update_rate;
	//	Vector<Long> cycles;

	/***************************************************************   camera   ***************************************************************/
	Camera_feedback the_cam;
	Bitmap the_image;

	/***************************************************************  image processing/packeting************************************************************/
	ByteArrayOutputStream byteStream;
	byte frame_nb = 0;	
	int width_ima, height_ima,packetCount,nb_packets,size;
	byte[] picData;
	int compression_rate;
	Mat m,dest, dest2;							//openCV image
	boolean NEW_FRAME;

	/*****************************************   IOIO    ***************************************************************/
	IOIO_thread ioio_thread;
	static int DEFAULT_PWM = 1500, MIN_PWM_MOTOR=1400, MAX_PWM_MOTOR=1600, MIN_PWM_SERVO=1000, MAX_PWM_SERVO=2000;	
	float IR_right, IR_left, IR_front_left, IR_front_right; //IR_front	
	float pwm_servo = DEFAULT_PWM;
	float pwm_motor = DEFAULT_PWM;	
	boolean RC_MODE, EXPLORE_MODE;	
	float[] IR_vals, PWM_vals;
	String string_ioio_vals;
	boolean NEW_DATA_IOIO;

	/*****************************************   sensors    ***************************************************************/
	Sensors_listener the_sensors;
	GPS_listener GPS;
	float[] acceleration;
	float[] orientation;
	float[] gyroscope;
	String string_sensors_vals;
	Location lastKnownLocation_GPS, target_location;
	double latitude, longitude, altitude, accuracy;
	float declination;
	boolean DECLI;
	boolean NEW_DATA_GPS;

	/*****************************************   UDP   ***************************************************************/
	InetAddress serverAddr;
	String ip_address_server;	
	DatagramSocket socket_udp_ioio, socket_udp_sensors, socket_udp_camera;	
	int port_ioio, port_sensors, port_camera;
	int size_p=0;
	static int HEADER_SIZE = 5;
	static int DATAGRAM_MAX_SIZE = 1450 - HEADER_SIZE;		

	/*****************************************   TCP   ***************************************************************/
	int port_TCP;
	Socket the_TCP_socket;
	String message_TCP;
	InetSocketAddress serverAddr_TCP;
	BufferedWriter out;
	BufferedReader input;
	boolean CLOSED_SOCKET=false;
	int counter_TCP_check=0;

	/***************************************************************   auto drive   ***************************************************************/
	// Warren and Fajen constants made smaller
	static final double k0 = 2.5;
	static final double c3 = 4;
	static final double c4 = 2;

	static final float TOOCLOSE = 0.2f;
	static final float ALLCLEAR = 0.5f;
	static final double maxIR = 2.5;
	static final double speedConst = -5.0;
	boolean GOING_FORWARD = true;
	double[] ang, dist;
	double speed, turn, minDist;


	public Main_thread(Main_activity gui, String msg_tcp)
	{
		the_gui = gui;		
		ip_address_server = the_gui.IP_server;	
		message_TCP = new String(msg_tcp);
		port_TCP = the_gui.port_TCP;
		string_sensors_vals = new String();
		string_ioio_vals = new String();

		//autodrive
		ang = new double[4];
		dist = new double[4];		
		minDist = 9999.0;	

		NEW_FRAME = false;
		NEW_DATA_IOIO = false;
		NEW_DATA_GPS = false;

		//		cycles = new Vector<Long>();
	}

	public synchronized void stop_thread()
	{
		STOP = true;
	}	

	/********************************************************************************************************************************************************************/
	/***************************************************************   main  loop   ***************************************************************/
	/********************************************************************************************************************************************************************/
	@Override
	public final void run() 
	{	
		start_tcp();	// connect to the server

		old_time = SystemClock.elapsedRealtime();

		while(STOP == false)
		{		
			synchronized(this)					//thread cannot be stopped by activity (user) while running this part of the code
			{
				//get (update) data
				get_sensors_values();
				get_camera_frame();
				get_ioio_vals();

				//do stuff with data here...
				if (EXPLORE_MODE == true)	autoDriveWF();

				//send data to server (udp sockets)
				send_sensors_data();
				send_camera_data();
				send_ioio_data();
			}
			read_socket_tcp();					//read tcp message (timeout 20ms)... set RECONNECT_TCP=true if problem

			if(ioio_thread != null) ioio_thread.set_PWM_values(pwm_motor, pwm_servo);			//set pwm values, wake up ioio thread 

			//			if(the_gui.CAMERA_STARTED == true || the_gui.SENSORS_STARTED == true || the_gui.IOIO_STARTED == true)
			//			{
			//				time = SystemClock.elapsedRealtime();
			//				cycle = time - old_time;			
			//				old_time = time;
			//				cycles.add(cycle);
			//			}
			//			Log.i(TAG,"cycle: " + cycle);
		}

		stop_tcp();
		stop_all();
	}

	private void stop_all()
	{
		stop_camera_udp();
		stop_sensors_udp();
		stop_IOIO_udp();
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
				//				the_TCP_socket.setSoTimeout(5);				//read timeout  (ms)
				the_TCP_socket.setSoTimeout(10);							//read timeout  (ms)
				out = new BufferedWriter(new OutputStreamWriter(the_TCP_socket.getOutputStream()));
				input = new BufferedReader(new InputStreamReader(the_TCP_socket.getInputStream()));

				RECONNECT_TCP = false;
				run_on_UI(7);

				out.write(message_TCP);
				out.flush();
//				Log.i("tcp","send msg " + message_TCP);
			}
			catch(java.io.IOException e) 
			{
				RECONNECT_TCP = true;
				//				Log.e("tcp","error connect: ", e);
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

	private void read_socket_tcp()
	{
		counter_TCP_check++;
		if(counter_TCP_check==500)	//every 5s approx.
		{
			counter_TCP_check=0;
			try 
			{
//				Log.i("tcp","send tcp check");
				out.write("TCP_CHECK");
				out.flush();
			} 
			catch (IOException e) 					//if connection is lost	
			{	
				Log.e("tcp","error write: ", e); 
				stop_tcp();							//close properly
				stop_all();
				run_on_UI(6);
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
					//				Log.i("tcp","tcp server still connected");
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
					the_gui.idx_size_cam = Integer.parseInt(sss[2]);					
					run_on_UI(0);
					start_camera_udp();
				}
				else if(sss[0].matches("CAMERA_OFF") == true)
				{
					Log.i(TAG,"stop cam ");					
					run_on_UI(1);			
					stop_camera_udp();
				}
				else if(sss[0].matches("IMG_RATE") == true)
				{	
					set_compression_rate(Integer.parseInt(sss[1]));
				}
				else if(sss[0].matches("SENSORS_ON") == true)
				{
					Log.i(TAG,"start sensors ");
					port_sensors = Integer.parseInt(sss[1]);
					run_on_UI(2);
					start_sensors_udp();
				}
				else if(sss[0].matches("SENSORS_OFF") == true)
				{
					Log.i(TAG,"stop sensors ");
					run_on_UI(3);
					stop_sensors_udp();
				}
				else if(sss[0].matches("IOIO_ON") == true)
				{				
					Log.i(TAG,"start ioio");	
					port_ioio = Integer.parseInt(sss[1]);
					the_gui.INVERTED = (Byte.parseByte(sss[2])!=0);
					RC_MODE = (Byte.parseByte(sss[3])!=0);
					EXPLORE_MODE = (Byte.parseByte(sss[4])!=0);
					run_on_UI(4);
					start_IOIO_udp();
				}
				else if(sss[0].matches("IOIO_OFF") == true)
				{
					Log.i(TAG,"stop ioio ");	
					run_on_UI(5);
					stop_IOIO_udp();
				}
				else if(sss[0].matches("MODE") == true)
				{
					Log.i(TAG,"change mode");
					RC_MODE = (Byte.parseByte(sss[1])!=0);
					EXPLORE_MODE = (Byte.parseByte(sss[2])!=0);
				}
			}
		}
	}

	private void run_on_UI(final int nb)
	{
		the_gui.runOnUiThread(new Runnable() 
		{
			@Override
			public void run() 
			{
				switch(nb)
				{
				case 0:	the_gui.start_video(); 
				break;

				case 1:	the_gui.stop_video(); 
				break;

				case 2:	the_gui.start_sensors();      
				break;

				case 3:	the_gui.stop_sensors();
				break;

				case 4:	the_gui.start_IOIO();
				break;

				case 5:	the_gui.stop_IOIO();      
				break;		

				case 6: //if(the_gui.button_connect.isChecked()) the_gui.button_connect.setChecked(false);
					the_gui.stop_all2();
					break;	

				case 7: if(the_gui.button_connect.isChecked()==false) the_gui.button_connect.setChecked(true);
				break;	
				}                      
			}
		}); 
	}

	/********************************************************************************************************************************************************************/
	/***************************************************************   sensors   ***************************************************************/	
	/********************************************************************************************************************************************************************/
	private void start_sensors_udp()
	{
		try
		{
			serverAddr = InetAddress.getByName(ip_address_server);
			socket_udp_sensors = new DatagramSocket();
		}
		catch (Exception exception) {	Log.e(TAG, "Error: ", exception);}		
		DECLI = false;
	}

	private void stop_sensors_udp()
	{
		if(socket_udp_sensors!= null) 
		{
			socket_udp_sensors.close();
		}
	}

	private void get_sensors_values() 		//get new values from sensors
	{
		if(the_gui.SENSORS_STARTED == true)
		{
			the_sensors = the_gui.sensor_listener;
			GPS = the_gui.locationListener_GPS;

			orientation = the_sensors.get_orientation();
			acceleration = the_sensors.get_acceleration();	
			gyroscope = the_sensors.get_gyro_values();			
			lastKnownLocation_GPS = GPS.get_gps_loc();

			if(lastKnownLocation_GPS != null)
			{
				latitude = lastKnownLocation_GPS.getLatitude();
				longitude = lastKnownLocation_GPS.getLongitude();
				altitude = lastKnownLocation_GPS.getAltitude();
				accuracy = lastKnownLocation_GPS.getAccuracy();

				if(DECLI==false)		//only get declination once...the robot won't move really far
				{
					DECLI = true;
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
		if(the_gui.SENSORS_STARTED == true && socket_udp_sensors.isClosed()==false)
		{
			if(the_sensors.FIRST_TIME == false)
			{
				string_sensors_vals = "Azimuth: "+ Float.toString(orientation[0])+ "/Pitch: "+ Float.toString(orientation[1])+ "/Roll: " + Float.toString(orientation[2]) +
				"/Acceleration x: " + Float.toString(acceleration[0]) + "/Acceleration y: " + Float.toString(acceleration[1]) + "/Acceleration z: " + Float.toString(acceleration[2]) + 
				"/Angular speed x: " + Float.toString(gyroscope[0]) + "/Angular speed y: " + Float.toString(gyroscope[1]) + "/Angular speed z: " + Float.toString(gyroscope[2]) +
				"/Latitude: " + Double.toString(latitude)  + "/Longitude: " + Double.toString(longitude)  + "/Altitude: " + Double.toString(altitude)  + 
				"/Accuracy: " + Double.toString(accuracy) + "/" + the_sensors.cycle_acce + "/" + the_sensors.cycle_compass + "/" + the_sensors.cycle_gyro;

				if(NEW_DATA_GPS == true)
				{
					string_sensors_vals += "/" + GPS.cycle;
				}
				//				long gc = GPS.cycle;
				//				if(gc > -10) string_sensors_vals += "/" + gc;

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
	private void start_IOIO_udp()
	{
		try
		{
			serverAddr = InetAddress.getByName(ip_address_server);
			socket_udp_ioio = new DatagramSocket();
		}
		catch (IOException exception) {	Log.e(TAG, "Error socket: ", exception);}
	}

	private void stop_IOIO_udp()
	{
		if(socket_udp_ioio!= null)
		{
			socket_udp_ioio.close();
			socket_udp_ioio = null;
		}
		ioio_thread = null;
		EXPLORE_MODE=false;
		RC_MODE=true;
	}

	private void get_ioio_vals()
	{
		if(the_gui.IOIO_STARTED == true)
		{
			ioio_thread = the_gui.the_IOIO;
			IR_vals  = ioio_thread.get_IR_values();

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
		if(the_gui.IOIO_STARTED == true && socket_udp_ioio!= null)
		{
			if(NEW_DATA_IOIO == true)
			{
				string_ioio_vals = "IR left: " + Float.toString(IR_left) + "/IR front left: " + Float.toString(IR_front_left) +
				"/IR front right: " + Float.toString(IR_front_right) + "/IR right: " + Float.toString(IR_right) +
				"/PWM motor: " + Float.toString(pwm_motor) +"/PWM servo: " + Float.toString(pwm_servo) + "/" + ioio_thread.cycle;

				try 
				{
					DatagramPacket packet = new DatagramPacket(string_ioio_vals.getBytes(), string_ioio_vals.length(), serverAddr, port_ioio);
					socket_udp_ioio.send(packet);					//send data
				} 
				catch (IOException e) {Log.e(TAG,"error sending: ", e);}
			}
		}
	}

	// Obstacle avoidance and steering algorithm based on Warren & Fajen
	private void autoDriveWF()
	{
		//		d = lastKnownLocation_GPS.distanceTo(target_location);

		//		azimuth = the_sensors.get_azimuth_90();
		//		azimuth += declination;
		//		bearing = lastKnownLocation_GPS.bearingTo(target_location);
		//		float direction = azimuth - bearing;
		//		if(direction > 180) 		direction -= 360;
		//		else if(direction < -180)	direction += 360; 

		ang[0] = Math.PI / 4.0;		//for left
		ang[1] = Math.PI / 8.0;			//for front left 
		ang[2] = -Math.PI / 8.0;		//for front right 
		ang[3] = -Math.PI / 4.0;		//for right 

		dist[0] = 1.0 - IR_left / maxIR;
		dist[1] = 1.0 - IR_front_left / maxIR;
		dist[2] = 1.0 - IR_front_right / maxIR;
		dist[3] = 1.0 - IR_right / maxIR;

		turn = 0;
		for (int i = 0; i < 4; i++) 
		{
			turn = turn + (k0 * ang[i]) * Math.exp(-c3 * Math.abs(ang[i]))* Math.exp(-c4 * dist[i]);
			if (dist[i] < minDist) 	minDist = dist[i];					//find minimum distance (closest obstacle)
		}
		speed = Math.exp(speedConst * minDist);		

		if ((GOING_FORWARD==true) && (minDist < TOOCLOSE))				// check for reversal from forward to reverse 
		{
			GOING_FORWARD = false;
			pwm_servo = DEFAULT_PWM;
			pwm_motor = DEFAULT_PWM;
			Log.d(TAG, "forward to reverse");
		}
		else if ((GOING_FORWARD == false) && (minDist > ALLCLEAR)) 		// check for reversal from reverse to forward 
		{
			GOING_FORWARD = true;
			pwm_servo = DEFAULT_PWM;
			pwm_motor = DEFAULT_PWM;
			Log.d(TAG, "reverse to forward");
		}
		else if (GOING_FORWARD == true)									// going forward 
		{
			pwm_servo =  (float) (DEFAULT_PWM + turn * 2500);			//if turn is positive: turn right; left otherwise
			pwm_motor = (float) ((DEFAULT_PWM + 65) - speed * 65);			
			if (pwm_motor > MAX_PWM_MOTOR)  	pwm_motor = MAX_PWM_MOTOR; 
			else if (pwm_motor < DEFAULT_PWM) 	pwm_motor = DEFAULT_PWM;			
		} 
		else															// going in reverse
		{
			pwm_servo = (float) (DEFAULT_PWM - turn * 2500);
			pwm_motor = (float) (DEFAULT_PWM - 85);
			if (pwm_motor < MIN_PWM_MOTOR)  	pwm_motor = MIN_PWM_MOTOR; 
		}

		if (pwm_servo > MAX_PWM_SERVO)  		pwm_servo = MAX_PWM_SERVO; 
		else if (pwm_servo < MIN_PWM_SERVO) 	pwm_servo = MIN_PWM_SERVO;

		Log.d(TAG, "forward(y/n):" +GOING_FORWARD + ", speed=" + minDist + ", turn=" + turn);
		Log.d(TAG, "pwm_motor=" + pwm_motor + ", pwm_servo=" + pwm_servo);
	}

	/********************************************************************************************************************************************************************/
	/***************************************************************   camera   ***************************************************************/
	/********************************************************************************************************************************************************************/
	private void start_camera_udp()
	{
		try
		{
			serverAddr = InetAddress.getByName(ip_address_server);
			socket_udp_camera = new DatagramSocket();
		}
		catch (Exception exception) {	Log.e(TAG, "Error: ", exception);}

		NEW_IMA = true;
	}

	private void stop_camera_udp()
	{
		if(socket_udp_camera!= null)
		{
			socket_udp_camera.close();
			socket_udp_camera=null;
			NEW_FRAME = false;

			//			try 
			//			{
			//				Log.i("tcp","send fps");
			//				the_cam.t_intervals.remove(0);
			//				String s = "FPS/" + the_cam.t_intervals.toString();
			//				out.write(s);
			//				out.flush();
			//				the_cam.t_intervals.clear();
			//			} 
			//			catch (IOException e){}
		}
	}

	private void set_compression_rate(int nb)
	{
		compression_rate = nb;
	}

	private void get_camera_frame() 
	{
		if(the_gui.CAMERA_STARTED == true)
		{
			if(NEW_IMA==true)
			{
				the_cam = the_gui.the_cam;	
				width_ima = the_cam.mPreviewSize.width;
				height_ima = the_cam.mPreviewSize.height;		
				the_image = Bitmap.createBitmap(width_ima, height_ima, Bitmap.Config.ARGB_8888);
				m = new Mat(height_ima + height_ima / 2, width_ima, CvType.CV_8UC1);	//m will be YUV format

				dest = new Mat(64 + 32,80,CvType.CV_8UC1);	
				dest2 = new Mat();
				byteStream = new ByteArrayOutputStream();
				compression_rate = 75;					// default jpeg compression rate
				NEW_IMA=false;

				Log.i(TAG, "new ima");
			}			

			byte[] data = the_cam.get_data();
			if(data != null)
			{
				m.put(0, 0, data);
				//				Imgproc.cvtColor(m, dest, Imgproc.COLOR_YUV420sp2RGB,4);	//YUV to ARGB

				Imgproc.resize(m, dest, dest.size());
				Imgproc.cvtColor(dest, dest2, Imgproc.COLOR_YUV420sp2GRAY);		//format to grayscale				

				/** compress to jpeg using opencv...not sure it's faster than using Bitmap Compress**/
				MatOfInt  params = new MatOfInt(Highgui.IMWRITE_JPEG_QUALITY, compression_rate);				
				MatOfByte buff = new MatOfByte();	
				Highgui.imencode(".jpg", dest2, buff, params);				
				/************************/

				picData = buff.toArray();

				/** compress to jpeg  **/
				//				Utils.matToBitmap(dest, the_image);
				//				byteStream.reset();
				//				the_image.compress(Bitmap.CompressFormat.JPEG, compression_rate, byteStream);	// !!!!!!!  change compression rate to change packets size
				//				picData = byteStream.toByteArray();		
				NEW_FRAME = true;
			}
			else NEW_FRAME = false;
		}
	}

	private void send_camera_data()
	{
		if(NEW_FRAME == true && socket_udp_camera!=null) //the_gui.CAMERA_STARTED
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

				int tt = (int) the_cam.cycle;
				data2[3] = (byte)(tt >> 8);
				data2[4] = (byte)tt;
				//				data2[3] = (byte)(size >> 8);
				//				data2[4] = (byte)size;

				System.arraycopy(picData, packetCount * DATAGRAM_MAX_SIZE, data2, HEADER_SIZE, size);	// Copy current slice to byte array		
				try 
				{			
					size_p = data2.length;
					DatagramPacket packet = new DatagramPacket(data2, size_p, serverAddr, port_camera);
					socket_udp_camera.send(packet);
				}catch (Exception e) {	Log.e(TAG, "Error: ", e);}	
			}
			frame_nb++;

			if(frame_nb == 127)frame_nb=0;
		}
	}
}