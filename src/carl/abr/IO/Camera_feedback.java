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

/** Used to open camera, and capture each frame using cam_PreviewCallback.*/
public class Camera_feedback
{
	private static final String TAG = "IP_cam";	
	
	/** Object used to interact with the camera of the phone*/
	Camera mCamera;
	
	/** List of resolution (preview sizes) supported by the camera of the phone*/
	List<Size> mSupportedPreviewSizes;
	
	/** Selected preview size*/
	public Size mPreviewSize;
	
	/** Index of the selected preview size: sent from the server over tcp socket*/
	int idx_selected_size;
	
	/** byte array used to store the data of the image/frame */
	byte[] data_image;
	
	/** dummy surface that will be set to 1 so we do not display the video frame on the screen (trick) */
	SurfaceTexture dummy_surface;
	
	/** true: a new frame is available <br> false: the frame has already been accessed */
	boolean NEW_FRAME;

	/** 
	 * Constructor that will open the camera, set parameters and link a preview callback that will copy the latest frame.
	 * @param int idx_size: index of selected resolution (preview size) sent by server 
	 * 
	 * */
	public Camera_feedback(int idx_size)
	{
		idx_selected_size = idx_size;			// index used to set preview size
		NEW_FRAME = false;
		try 
		{			 
			mCamera = Camera.open();      			
			dummy_surface = new SurfaceTexture(1);							// so we do not display the video frame on the screen (trick)

			Camera.Parameters parameters = mCamera.getParameters(); 
			mSupportedPreviewSizes = parameters.getSupportedPreviewSizes();	
			mPreviewSize = mSupportedPreviewSizes.get(idx_selected_size);
			parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
			mCamera.setParameters(parameters);

			try { mCamera.setPreviewTexture(dummy_surface); } catch (IOException t) {}

			mCamera.setPreviewCallback(new cam_PreviewCallback());			// create a new cam_PreviewCallback and attach it to the camera
			mCamera.startPreview();											// start camera with cam_PreviewCallback
		} 
		catch (Exception exception)	{	Log.e(TAG, "Error: ", exception);}
	}
	
	/** 
	 * Stop the camera preview and release the camera. 
	 * 
	 * */
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

	/** 
	 * Returns the data of the new image/frame.
	 * @return -byte array containing the data/frame. <br> -null if the frame has already been accessed and/or has not been updated yet.
	 * 
	 * */
	public synchronized byte[] get_data()
	{		
		if(data_image != null && NEW_FRAME == true)
		{
			NEW_FRAME = false;
			return data_image;
		}
		else return null;		
	}	

	/** 
	 * Implements PreviewCallback interface with {@link #onPreviewFrame(byte[], Camera)} which is called every time a new frame is available.
	 * @see #onPreviewFrame(byte[], Camera)  
	 * 
	 * */
	private class cam_PreviewCallback implements PreviewCallback  
	{
		@Override
		public void onPreviewFrame(byte[] data, Camera camera)
		{		
			NEW_FRAME = true;
			data_image = data.clone();	// copy the image/frame
		}
	}
}
