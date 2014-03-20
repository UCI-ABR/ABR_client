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

import java.io.IOException;
import java.util.List;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.util.Log;

public class Camera_feedback
{
	private static final String TAG = "IP_cam";	
	Camera mCamera;
	List<Size> mSupportedPreviewSizes;
	List<int[]> mSupportedFPS;
	public Size mPreviewSize;
	int idx_selected_size;
	byte[] data_image;		
	SurfaceTexture dummy_surface;
	
	public long old_time,cycle, time;
	int total_frames=0;

	boolean NEW_FRAME;

	public Camera_feedback(int idx_size)
	{
		idx_selected_size = idx_size;			// index used to set preview size
		NEW_FRAME = false;
		try 
		{			 
			mCamera = Camera.open();      			
			dummy_surface = new SurfaceTexture(1);

			Camera.Parameters parameters = mCamera.getParameters(); 
			mSupportedPreviewSizes = parameters.getSupportedPreviewSizes();	
			mPreviewSize = mSupportedPreviewSizes.get(idx_selected_size);
			parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
			mSupportedFPS = parameters.getSupportedPreviewFpsRange();
			
//			int fps1=0,fps2=0;		
//			for(int i=0; i<mSupportedFPS.size();i++)
//			{
//				fps1 = mSupportedFPS.get(i)[0];
//				fps2 = mSupportedFPS.get(i)[1];	
//				Log.i(TAG, "fps: " + fps1 + " ; " + fps2);
//			}
//			parameters.setPreviewFpsRange(fps1, fps2);			
////			parameters.setFocusMode(Parameters.FOCUS_MODE_INFINITY);			
//			parameters.setSceneMode(Camera.Parameters.SCENE_MODE_ACTION); //for higher fps BUT...can greatly vary on phone 
			mCamera.setParameters(parameters);

			try { mCamera.setPreviewTexture(dummy_surface); } catch (IOException t) {}

			mCamera.setPreviewCallback(new cam_PreviewCallback());			
			mCamera.startPreview();
			
//			old_time = SystemClock.elapsedRealtime();
		} 
		catch (Exception exception)	{	Log.e(TAG, "Error: ", exception);}
	}

	public synchronized void stop_camera()
	{
		if(mCamera != null)
		{
			mCamera.setPreviewCallback(null);
			mCamera.stopPreview();
			mCamera.release();
			mCamera = null;
		}
	}

	public synchronized byte[] get_data()
	{		
		if(data_image != null && NEW_FRAME == true)
		{
			NEW_FRAME = false;
			return data_image;
		}
		else return null;		
	}	

	/********************************************************************************************************************************************************************/
	/***************************************************************   camera  callback ***************************************************************/
	/********************************************************************************************************************************************************************/
	private class cam_PreviewCallback implements PreviewCallback 	// Preview callback called whenever a new frame is available 
	{
		@Override
		public void onPreviewFrame(byte[] data, Camera camera)
		{		
			NEW_FRAME = true;
			total_frames++;
			data_image = data.clone();

//			time = SystemClock.elapsedRealtime();
//			cycle = time - old_time;			
//			old_time = time;
		}
	}
}
