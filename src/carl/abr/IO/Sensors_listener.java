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
package carl.abr.IO;

import carl.abr.gui.Main_activity;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;

public class Sensors_listener implements SensorEventListener 
{
	final String tag = "Sensors";

	long time;
	public long old_time_acce,cycle_acce;
	public long old_time_gyro,cycle_gyro;
	public long old_time_compass,cycle_compass;
	
	/***************************************************************  Values for orientation and acceleration   ***************************************************************/
	float[] mMagneticValues;
	float[] mAccelerometerValues;
	float[] gyroscope_values;
	float[] orientation;
	float[] R;
	float mAzimuth;
	public boolean FIRST_TIME;

	public Sensors_listener(Main_activity act)						// default constructor 
	{
		super();
		R = new float[9];		
		mMagneticValues = new float[3];
		mAccelerometerValues = new float[3];
		orientation = new float[3];
		gyroscope_values = new float[3];
		FIRST_TIME = true;
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
	}

	@Override
	public void onSensorChanged(SensorEvent event) 
	{
		//		  if (event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE)
		//		        return;
		
		if(FIRST_TIME==true)
		{
			FIRST_TIME=false;
			old_time_compass = SystemClock.elapsedRealtime() - 10;	//just for first value...
			old_time_acce = old_time_compass;
			old_time_gyro = old_time_compass;
		}

		switch (event.sensor.getType()) 
		{
		case Sensor.TYPE_MAGNETIC_FIELD:
			mMagneticValues[0] = event.values[0];
			mMagneticValues[1] = event.values[1];
			mMagneticValues[2] = event.values[2];
			time = SystemClock.elapsedRealtime();
			cycle_compass = time - old_time_compass;			
			old_time_compass = time;		
			break;
		case Sensor.TYPE_ACCELEROMETER:
			mAccelerometerValues[0] = event.values[0];
			mAccelerometerValues[1] = event.values[1];
			mAccelerometerValues[2] = event.values[2];
			time = SystemClock.elapsedRealtime();
			cycle_acce = time - old_time_acce;			
			old_time_acce = time;		
			break;
		case Sensor.TYPE_GYROSCOPE:
			gyroscope_values[0] = event.values[0];
			gyroscope_values[1] = event.values[1];
			gyroscope_values[2] = event.values[2];
			time = SystemClock.elapsedRealtime();
			cycle_gyro = time - old_time_gyro;			
			old_time_gyro = time;		
			break;
		}

		SensorManager.getRotationMatrix(R, null, mAccelerometerValues, mMagneticValues);	        
		SensorManager.getOrientation(R, orientation);
		orientation[0] = (float) Math.toDegrees(orientation[0]);
		orientation[1] = (float) Math.toDegrees(orientation[1]);
		orientation[2] = (float) Math.toDegrees(orientation[2]);
	}

	public synchronized float[] get_orientation()
	{
		return orientation;
	}

	public synchronized float[] get_acceleration()
	{
		return mAccelerometerValues;
	}	

	public synchronized float[] get_gyro_values()
	{
		gyroscope_values[0] = (float) Math.toDegrees(gyroscope_values[0]);
		gyroscope_values[1] = (float) Math.toDegrees(gyroscope_values[1]);
		gyroscope_values[2] = (float) Math.toDegrees(gyroscope_values[2]);
		return gyroscope_values;
	}	

	public synchronized float get_azimuth_90()
	{
		mAzimuth = orientation[0] + 90f;		// +90 for landscape mode
		if(mAzimuth > 180f) 		mAzimuth -= 360f;
		else if(mAzimuth < -180f)	mAzimuth += 360f; 

		return mAzimuth;
	}
}

