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

public class IOIO_thread extends BaseIOIOLooper 
{
//	private static final String TAG = "IOIO_thread";

	PwmOutput motor_output, servo_output;
	AnalogInput IR_left, IR_right, IR_front_left, IR_front_right;
	float pwm_M, pwm_S;
	boolean INVERTED, AUTO;	

	static int DEFAULT_PWM = 1500;	

	float[] IR_values;
	float[] PWM_values;

	public boolean UPDATED = false;

	public IOIO_thread()
	{		
		INVERTED = false;

		IR_values  = new float[5];
		PWM_values = new float[2];

		PWM_values[0] = DEFAULT_PWM;
		PWM_values[1] = DEFAULT_PWM;		
	}

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

				pwm_M = PWM_values[0];
				pwm_S = PWM_values[1];
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

	public synchronized float[] get_IR_values()
	{		
		if(UPDATED == true)
		{
			UPDATED= false;
			return IR_values;
		}
		else return null;

	}	 

	public synchronized void set_PWM_values(float pwm_motor, float pwm_servo)
	{
		PWM_values[0] = pwm_motor;
		PWM_values[1] = pwm_servo;

		notify();					//awake this IOIO thread waiting
	}	

	public synchronized void set_inverted(boolean inv)
	{
		INVERTED = inv;
	}	
}
