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

public class Main_activity extends Activity
{
	final String tag = "Main activity";
	Context the_context;
	
	public Main_thread the_main_thread;	//main thread running the main loop (get data from sensors camera ioio, process data, send data ,set motor command ioio)
	public String IP_server = null;
	public int port_TCP;

	/***************************************************************   GUI stuff   **********************************************************/
	PowerManager.WakeLock wake;			//used to keep screen on
	My_spinner_Class spinner_IP, spinner_port;
	EditText ip_text, port_text;
	Button button_add_IP, button_delete_IP, button_add_port, button_delete_port;
	public ToggleButton button_connect;		

	
	/**************************************************************************************************************************************/
	/********************************************************** activity / GUI ************************************************************/
	/**************************************************************************************************************************************/
	@Override
	public void onCreate(Bundle savedInstanceState) 
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main_acti);
		the_context = this.getApplicationContext();

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
				Toast.makeText(the_context, "IP_address: " + IP_server, Toast.LENGTH_SHORT).show();	
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
				Toast.makeText(the_context, "Port: " + port, Toast.LENGTH_SHORT).show();	
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

	@SuppressWarnings("deprecation")
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
		stop_all();
		wake.release();		
		super.onStop();
		this.finish();
	}
	
	private void set_buttons(boolean b)
	{
		button_delete_IP.setEnabled(b);
		button_delete_port.setEnabled(b);
		button_connect.setEnabled(b);
	}

	/***************************************************************************************************************************************************/
	/************************************************************* main thread / TCP client ************************************************************/
	/***************************************************************************************************************************************************/
	public void Start_TCP_client()
	{		
		the_main_thread = new Main_thread(this);
		the_main_thread.start();					//start thread...calls run function
	}

	public void stop_all()
	{
		if(the_main_thread != null) the_main_thread.stop_thread();	
		the_main_thread=null;
	}
}