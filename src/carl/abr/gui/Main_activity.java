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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.ToggleButton;
import carl.abr.threads.Main_thread;
import carl.gui.R;

/** 
 * Main activity (GUI) of the ABR_client app. This activity is used to save IP addresses and port numbers to connect to a TCP server.
 * It also creates a thread ({@link #the_main_thread}) that will run the main loop of the app (connect to the server, get data from sensors camera ioio, process data, send data ,set motor command ioio)
 * @see Main_thread
 * */
public class Main_activity extends Activity
{
	final String tag = "Main activity";
	
	/** Context of the activity used for gui stuff*/
	Context the_context;
	
	/** Main thread running the main loop (connect to the server, get data from sensors camera ioio, process data, send data ,set motor command ioio)
	 * @see Main_thread*/
	Main_thread the_main_thread;
	
	/** String containing the IP address of the server*/
	public String IP_server = null;
	
	/** The port number of the TCP socket used to connect to the server*/
	public int port_TCP;

	/** Used to keep screen on*/
	PowerManager.WakeLock wake;
	
	/** Spinners (dropdown menus) used to select IP and port number*/
	My_spinner spinner_IP, spinner_port;
	
	/** Edittext used to enter/type an IP and port number*/
	EditText ip_text, port_text;
	
	/** Buttons used to add/remove an IP and port number*/
	Button button_add_IP, button_delete_IP, button_add_port, button_delete_port;
	
	/** Button used to connect or disconnect the phone to the server*/
	public ToggleButton button_connect;		

	@Override
	public void onCreate(Bundle savedInstanceState) 
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main_acti);
		the_context = this.getApplicationContext();

		// get references to all the gui items
		ip_text = (EditText) findViewById(R.id.txt_IP);
		port_text = (EditText) findViewById(R.id.txt_port);
		spinner_IP = (My_spinner)findViewById(R.id.spinner_IP);
		spinner_port = (My_spinner)findViewById(R.id.spinner_ports);
		button_add_IP = (Button) findViewById(R.id.btn_add_IP);
		button_delete_IP= (Button) findViewById(R.id.btn_delete_IP);
		button_add_port = (Button) findViewById(R.id.btn_add_port);
		button_delete_port = (Button) findViewById(R.id.btn_delete_port);
		button_connect = (ToggleButton) findViewById(R.id.btn_connect);
		
		enable_buttons(false);
		button_connect.requestFocus();

		spinner_IP.set_file_name("IP_clients.txt");
		spinner_port.set_file_name("ports_clients.txt");

		spinner_IP.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> arg0, View arg1,int arg2, long arg3) 
			{
				IP_server = spinner_IP.getSelected();		
				Toast.makeText(the_context, "IP_address: " + IP_server, Toast.LENGTH_SHORT).show();	
				enable_buttons(true);
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
				Toast.makeText(the_context, "Port: " + port, Toast.LENGTH_SHORT).show();	
				enable_buttons(true);
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
				enable_buttons(true);
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
					enable_buttons(true);
				}
				catch(java.lang.NumberFormatException e)
				{
					AlertDialog alertDialog;
					alertDialog = new AlertDialog.Builder(the_context).create();
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
				if(spinner_IP.remove_item() == false) enable_buttons(false);
			}
		});		

		button_delete_port.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) 
			{
				if(spinner_port.remove_item() == false) enable_buttons(false);
			}
		});		

		button_connect.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) 
			{
				if (button_connect.isChecked()) start_main_thread();
				else  stop_main_thread();
			}
		});		
	}

	@Override
	protected void onResume() 
	{
		super.onResume();

		//keep the screen on while running the activity
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wake = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, tag);
		wake.acquire();		
	}
	
	@Override
	protected void onStop() 
	{
		stop_main_thread();
		wake.release();		
		super.onStop();
		finish();
	}
	
	/** 
	 * Enables or disables the delete IP, delete Port and Connect buttons.
	 * @param b : true to enable the buttons, false to disable the buttons 
	 * 
	 */	
	private void enable_buttons(boolean b)
	{
		button_delete_IP.setEnabled(b);
		button_delete_port.setEnabled(b);
		button_connect.setEnabled(b);
	}

	/** 
	 * Creates and starts the main thread ({@link #the_main_thread}). Starting the thread will call its run() function. 
	 * @see Main_thread 
	 * 
	 */	
	public void start_main_thread()
	{		
		the_main_thread = new Main_thread(this);
		the_main_thread.start();				
	}

	/** 
	 * Stops the main thread ({@link #the_main_thread}).
	 * @see Main_thread
	 * 
	 */	
	public void stop_main_thread()
	{
		if(the_main_thread != null) the_main_thread.stop_thread();	
		the_main_thread=null;
	}
}