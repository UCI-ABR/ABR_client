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

package carl.abr.utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Vector;

import android.content.Context;
import android.widget.Toast;

/** 
 * Class used to read and write into files.
 * 
 */	
public class File_RW 
{
	static FileOutputStream fos = null;
	static FileInputStream fIn = null;		 
	static InputStreamReader isr = null;
	static OutputStreamWriter orw = null;
	

	public File_RW(){}

	/** 
	 * Writes text into a file.
	 * @param context : context of the parent calling this function 
	 * @param file : name of the file to be modified or created
	 * @param text : text (data) to write in the file
	 * @param append_mode : true if text to be added at the end of an existing file, false to create a new file (and replace existing one)
	 * 
	 */	
	public static void write_file(Context context, String file, String text, boolean append_mode)
	{
		try 
		{
			if(append_mode == true)fos = context.openFileOutput(file, Context.MODE_APPEND);
			else fos = context.openFileOutput(file, Context.MODE_PRIVATE);
			
			orw = new OutputStreamWriter(fos);
			BufferedWriter bf = new BufferedWriter(orw);
			bf.write(text);
			bf.newLine();
			bf.close();
			fos.close();
		} catch (IOException e) {
			Toast.makeText(context, "pb write file 2",Toast.LENGTH_SHORT).show();
			e.printStackTrace();
		}
	}
	
	/** 
	 * Deletes and recreates an empty file with the same name.
	 * @param context : context of the parent calling this function 
	 * @param file : name of the file to be re-created
	 * 
	 */	
	public static void reset_file(Context context, String file)
	{
		try {
			context.openFileOutput(file, Context.MODE_PRIVATE);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/** 
	 * Reads a file and output a list (Vector) of Strings containing the data.
	 * @param context : context of the parent calling this function 
	 * @param file : name of the file to be read
	 * @return Vector containing the data read from the file
	 * 
	 */	
	public static Vector<String> read_file(Context context, String file)
	{
		String str="";
		Vector<String> vs = new Vector<String>();
		try
		{			
			fIn = context.openFileInput(file);    
			if (fIn!=null) 
			{
				isr = new InputStreamReader(fIn);	
				BufferedReader reader = new BufferedReader(isr);
				
				while ((str = reader.readLine()) != null) 
				{	
					vs.add(str);
				}
				isr.close();
				fIn.close();
			}		
		}catch (IOException e) 
		{      
			e.printStackTrace();
			Toast.makeText(context, "pb read file",Toast.LENGTH_SHORT).show();
		}
		
		return vs;
	}
}
