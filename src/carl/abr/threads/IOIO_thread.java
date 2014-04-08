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

import ioio.lib.api.AnalogInput;
import ioio.lib.api.PwmOutput;
import ioio.lib.api.exception.ConnectionLostException;
import ioio.lib.util.BaseIOIOLooper;

/** 
 * Class (thread) used to open pins of the IOIO, as well as read IR values, and send pwm signals to the servo and motor of the robot.<br>
 * The main loop of the thread is executed in {@link #loop()}.
 * Values of the IR sensors can be accessed using {@link #get_IR_values()}.
 * The pulse width of the PWM signals sent to the motor and servo signal are set using {@link #set_PWM_values(float, float)}.
 * @see {@link #loop()} , {@link #get_IR_values()}, {@link #set_PWM_values(float, float)} , {@link #setup()} , {@link #set_inverted(boolean)}
 *
 */	
public class IOIO_thread extends BaseIOIOLooper 
{
//	static final String TAG = "IOIO_thread";

	/**default value of the pulse width of the PWM signals*/
	static final int DEFAULT_PWM = 1500;	
	
	/** PWM IOIO outputs used to control the motor and servo*/
	PwmOutput motor_output, servo_output;
	
	/** Analog IOIO inputs used to read the values of the infrared sensors*/
	AnalogInput IR_left, IR_right, IR_front_left, IR_front_right;

	/** array containing the pulse width of the PWM signals sent to the motor and servo*/
	float[] PWM_values;
	
	/** array containing the the values read from the infrared sensors*/
	float[] IR_values;
	
	/** true if the pwm values should be inverted (e.g. for some cars: 2000= right, others 1000=right)*/
	boolean INVERTED;		

	/** true: new IR values are available <br> false: IR values have already been accessed */
	boolean UPDATED = false;

	/** 
	 * Constructor <br>
	 *TODO: give default pwm for motor and servo sent by server 
	 */	
	public IOIO_thread()
	{		
		INVERTED = false;

		IR_values  = new float[5];
		PWM_values = new float[2];

		PWM_values[0] = DEFAULT_PWM;
		PWM_values[1] = DEFAULT_PWM;		
	}

	/** 
	 * Setup function that opens input and outputs with pins numbers.
	 * 
	 */	
	@Override
	public void setup() throws ConnectionLostException 
	{	
		motor_output = ioio_.openPwmOutput(5, 100);	
		servo_output = ioio_.openPwmOutput(7, 100);
		IR_left = ioio_.openAnalogInput(38);
		IR_front_left = ioio_.openAnalogInput(39);
		IR_front_right = ioio_.openAnalogInput(40);
		IR_right = ioio_.openAnalogInput(41);
	}

	/** 
	 * Main loop of the thread. Reads values of analog inputs (e.g. IR sensors), calls {@link #wait()}, then sets pulse width of PWM outputs. <br>
	 * When wait() is called, the thread pauses and waits to be awaken by the main thread that will call {@link #set_PWM_values(float, float)} which calls  {@link #notify()}.
	 * @see {@link #wait()} , {@link #set_PWM_values(float, float)} , {@link #notify()}
	 */	
	@Override
	public void loop() throws ConnectionLostException 
	{
		synchronized(this)
		{			
			try 
			{				
				IR_values[0] = IR_left.read();			
				IR_values[1] = IR_front_left.read();
				IR_values[2] = IR_front_right.read();
				IR_values[3] = IR_right.read();	
				UPDATED=true;
				
				wait();	

				float pwm_M = PWM_values[0];
				float pwm_S = PWM_values[1];
				if(INVERTED == true)
				{
					pwm_M = 1500 - (pwm_M-1500);
					pwm_S = 1500 - (pwm_S-1500);
				}
				motor_output.setPulseWidth(pwm_M);
				servo_output.setPulseWidth(pwm_S);
//				Thread.sleep(20);
			}
			catch (InterruptedException e) {ioio_.disconnect();	}
		}
	}

	/** 
	 * Get the new values from the IR sensors  
	 * @param float[5] : normalized values [0;1] of the IR sensors<br>
	 * null : if IR values have already been accessed
	 * 
	 */	
	public synchronized float[] get_IR_values()
	{		
		if(UPDATED == true)
		{
			UPDATED= false;
			return IR_values;
		}
		else return null;
	}	 

	/** 
	 * Sets pulse width of the PWM signals of the motor and servo, then calls {@link #notify()} to awake the ioio thread.
	 * @param pwm_motor : pulse width of the PWM signal controlling the motor
	 * @param pwm_servo : pulse width of the PWM signal controlling the servo
	 * @see {@link #wait()} , {@link #loop()} , {@link #notify()}
	 */	
	public synchronized void set_PWM_values(float pwm_motor, float pwm_servo)
	{
		PWM_values[0] = pwm_motor;
		PWM_values[1] = pwm_servo;

		notify();					//awake this IOIO thread waiting
	}	

	/** 
	 * Set inverted pwm commands
	 * @param inv : true to inverse pwm pulse width around 1500 (e.g. 1000 becomes 2000, and 2000 becomes 1000)
	 * 
	 */	
	public synchronized void set_inverted(boolean inv)
	{
		INVERTED = inv;
	}	
}
