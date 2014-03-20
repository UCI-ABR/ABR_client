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

public class My_spinner_Class extends Spinner 
{
	Vector<String> list_items = new Vector<String>();
	String file_name;
	
	// constructors (each calls initialize)
	public My_spinner_Class(Context context) 
	{
		super(context);
//		this.initialise();
	}
	public My_spinner_Class(Context context, AttributeSet attrs) 
	{
		super(context, attrs);
//		this.initialise();
	}
	public My_spinner_Class(Context context, AttributeSet attrs, int defStyle) 
	{
		super(context, attrs, defStyle);
//		this.initialise();
	}
	
	public void set_file_name(String s)
	{
		file_name = s.toString();
		this.initialise();
	}

	// declare object to hold data values
	private ArrayAdapter<String> arrayAdapter;

	// add the selected item to the end of the list
	public void addItem(String item) 
	{
		if(item.length() >0 && arrayAdapter.getPosition(item)== -1)
		{
			arrayAdapter.add(item);
			this.setEnabled(true);
			this.setSelection(arrayAdapter.getCount());
			File_RW.write_file(getContext(), file_name, item, true);
		}
		else
		{
			//			Toast.makeText(this.getContext(), "IP address already saved", Toast.LENGTH_LONG).show();
			AlertDialog alertDialog;
			alertDialog = new AlertDialog.Builder(this.getContext()).create();
			alertDialog.setTitle("Error");
			if(item.length() ==0) alertDialog.setMessage("null value!  \n\n (press back to return)");
			else alertDialog.setMessage("Value already exists!  \n\n (press back to return)");
			alertDialog.show();
		}
	}
	
	public boolean remove_item()
	{
		boolean b=true;
		String s = getSelected();
		int size = arrayAdapter.getCount();
		if(s != null)
		{
			
			arrayAdapter.remove(s);
			size--;
			File_RW.reset_file(getContext(), file_name);

			for(int i=0; i<size; i++)
			{
				File_RW.write_file(getContext(), file_name, arrayAdapter.getItem(i), true);
			}
		}
		if(size == 0)
		{
			this.setEnabled(false);
			b = false;
		}
		return b;
	}

	// return the current selected item
	public String getSelected() 
	{
		if (this.getCount() > 0) return arrayAdapter.getItem(super.getSelectedItemPosition());
		else return null;
	}
	
	// remove all items from the list and disable it
	public void clearItems() 
	{
		arrayAdapter.clear();
		this.setEnabled(false);
	}

	// internal routine to set up the array adapter, bind it to the spinner and disable it as it is empty
	private void initialise() 
	{
		arrayAdapter = new ArrayAdapter<String>(super.getContext(), android.R.layout.simple_spinner_item);
		arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		this.setAdapter(arrayAdapter);
		
		list_items = File_RW.read_file(getContext(), file_name);
		int size = list_items.size();
		for(int i=0; i<size; i++)
		{
			arrayAdapter.add(list_items.elementAt(i));
			this.setEnabled(true);
			this.setSelection(arrayAdapter.getCount());
		}
		 if(size ==0) this.setEnabled(false);
	}
}
