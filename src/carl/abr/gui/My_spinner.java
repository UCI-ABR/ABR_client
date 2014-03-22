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

import java.util.Vector;

import android.app.AlertDialog;
import android.content.Context;
import android.util.AttributeSet;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import carl.abr.utils.File_RW;

/** 
 * Spinner class (drop down menu) used to select the IP address of the server and port number for the TCP socket. <br>
 * Can save the items in a file when calling {@link #addItem(String)}, and load them when calling {@link #set_file_name(String)}.
 * @param context : context of the activity 
 * @see Spinner
 * @see #set_file_name(String)
 * 
 */	
public class My_spinner extends Spinner 
{	
	/** List of String items displayed in the spinner (drop down menu)*/
	ArrayAdapter<String> the_arrayAdapter;
	
	/** Name of the file where the items of the spinner are saved (e.g. IP adresses and port numbers).*/
	String file_name;
	
	/** List of String items save in the file {@link #file_name}*/
	Vector<String> list_items = new Vector<String>();
	
	/** 
	 * Constructor
	 * @param context : context of the activity 
	 * @see Spinner
	 */	
	public My_spinner(Context context){super(context);}
	
	/** 
	 * Constructor
	 * @param context : context of the activity 
	 * @see Spinner
	 */	
	public My_spinner(Context context, AttributeSet attrs) {super(context, attrs);}
	
	/** 
	 * Constructor
	 * @param context : context of the activity 
	 * @see Spinner
	 */	
	public My_spinner(Context context, AttributeSet attrs, int defStyle){	super(context, attrs, defStyle);}
	
	/** 
	 * Set the name of the file used for this spinner, and reads this file to add items to the spinner by calling {@link #initialize()}.
	 * @param name : name of the file 
	 * @see Spinner
	 * @see {@link #initialize()}
	 * 
	 */	
	public void set_file_name(String name)
	{
		file_name = name.toString();
		initialize();
	}

	/** 
	 * Adds the selected item to the end of the {@link #the_arrayAdapter}, and saves it in the file.
	 * @param item : String to be added to the spinner 
	 * 
	 */	
	public void addItem(String item) 
	{
		if(item.length() >0 && the_arrayAdapter.getPosition(item)== -1)
		{
			the_arrayAdapter.add(item);
			setEnabled(true);
			setSelection(the_arrayAdapter.getCount());
			File_RW.write_file(getContext(), file_name, item, true);
		}
		else
		{
			AlertDialog alertDialog;
			alertDialog = new AlertDialog.Builder(this.getContext()).create();
			alertDialog.setTitle("Error");
			if(item.length() ==0) alertDialog.setMessage("null value!  \n\n (press back to return)");
			else alertDialog.setMessage("Value already exists!  \n\n (press back to return)");
			alertDialog.show();
		}
	}
	
	/** 
	 * Removes the selected item from the {@link #the_arrayAdapter}, and from the file.
	 * @return boolean: true if there are still items that can be removed, false if the spinner is empty.
	 * 
	 */	
	public boolean remove_item()
	{
		boolean b=true;
		String s = getSelected();
		int size = the_arrayAdapter.getCount();
		if(s != null)
		{
			
			the_arrayAdapter.remove(s);
			size--;
			File_RW.reset_file(getContext(), file_name);

			for(int i=0; i<size; i++)
			{
				File_RW.write_file(getContext(), file_name, the_arrayAdapter.getItem(i), true);
			}
		}
		if(size == 0)
		{
			setEnabled(false);
			b = false;
		}
		return b;
	}

	/** 
	 * Returns the current selected item.
	 * @return String: selected item <br> null: if spinner is empty
	 * 
	 */	
	public String getSelected() 
	{
		if (this.getCount() > 0) return the_arrayAdapter.getItem(super.getSelectedItemPosition());
		else return null;
	}
	
	/** 
	 * Removes all items from the list and disable it
	 * 
	 */	
	public void clearItems() 
	{
		the_arrayAdapter.clear();
		setEnabled(false);
	}

	/** 
	 * Sets up the {@link #the_arrayAdapter} containing the items, bind it to the spinner and disable it if empty. Reads the items from the file {@link #file_name} and adds them to the {@link #the_arrayAdapter}.
	 * @see Spinner
	 * 
	 */
	private void initialize() 
	{
		the_arrayAdapter = new ArrayAdapter<String>(super.getContext(), android.R.layout.simple_spinner_item);
		the_arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		setAdapter(the_arrayAdapter);
		
		list_items = File_RW.read_file(getContext(), file_name);
		int size = list_items.size();
		for(int i=0; i<size; i++)
		{
			the_arrayAdapter.add(list_items.elementAt(i));
			setEnabled(true);
			setSelection(the_arrayAdapter.getCount());
		}
		 if(size ==0) setEnabled(false);
	}
}
