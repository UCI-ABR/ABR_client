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

public class File_RW 
{
	static FileOutputStream fos = null;
	static FileInputStream fIn = null;		 
	static InputStreamReader isr = null;
	static OutputStreamWriter orw = null;
	

	public File_RW(){}

	public static void write_file(Context context, String file, String s, boolean BOOL_APPEND)
	{
		try 
		{
			if(BOOL_APPEND == true)fos = context.openFileOutput(file, Context.MODE_APPEND);
			else fos = context.openFileOutput(file, Context.MODE_PRIVATE);
			
			orw = new OutputStreamWriter(fos);
			BufferedWriter bf = new BufferedWriter(orw);
			bf.write(s);
			bf.newLine();
			bf.close();
			fos.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			Toast.makeText(context, "pb write file 2",Toast.LENGTH_SHORT).show();
			e.printStackTrace();
		}
	}
	
	public static void reset_file(Context context, String file)
	{
		try {
			context.openFileOutput(file, Context.MODE_PRIVATE);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

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
