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

import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;

public class GPS_listener implements LocationListener
{	
	private static final int ONE_MINUTES = 1000 * 60 * 1;
	Location lastKnownLocation_GPS;
	boolean NEW_LOCATION;

	public GPS_listener()
	{
		super();
		NEW_LOCATION = false;
	}
	
	public synchronized Location get_gps_loc()
	{
		if(lastKnownLocation_GPS != null && NEW_LOCATION==true)
		{
			NEW_LOCATION = false;
			return lastKnownLocation_GPS;  //return new Location(lastKnownLocation_GPS);
		}
		else return null;
	}

	public void onLocationChanged(Location location) 
	{				
		if(isBetterLocation(location,  lastKnownLocation_GPS) == true)
		{
			lastKnownLocation_GPS = location;	
			NEW_LOCATION = true;
		}
	}
	public void onStatusChanged(String provider, int status, Bundle extras) {} // function never called ?
	public void onProviderEnabled(String provider) {}
	public void onProviderDisabled(String provider) {}

	/********************************************************************************************************************************************************************/
	/**************************************************************** functions for GPS *********************************************************************************/
	/********************************************************************************************************************************************************************/

	/** Determines whether one Location reading is better than the current Location fix
	 * @param location  The new Location that you want to evaluate
	 * @param currentBestLocation  The current Location fix, to which you want to compare the new one
	 */
	protected boolean isBetterLocation(Location location, Location currentBestLocation) 
	{
		if (currentBestLocation == null) {  return true; } 	        // A new location is always better than no location

		// Check whether the new location fix is newer or older
		long timeDelta = location.getTime() - currentBestLocation.getTime();
		boolean isSignificantlyNewer = timeDelta > ONE_MINUTES;
		boolean isSignificantlyOlder = timeDelta < -ONE_MINUTES;
		boolean isNewer = timeDelta > 0;

		// If it's been more than two minutes since the current location, use the new location
		// because the user has likely moved
		if (isSignificantlyNewer) {return true;
		} else if (isSignificantlyOlder) {return false; } 	    // If the new location is more than two minutes older, it must be worse

		// Check whether the new location fix is more or less accurate
		int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
		boolean isLessAccurate = accuracyDelta > 0;
		boolean isMoreAccurate = accuracyDelta < 0;
		boolean isSignificantlyLessAccurate = accuracyDelta > 200;

		// Check if the old and new location are from the same provider
		boolean isFromSameProvider = isSameProvider(location.getProvider(), currentBestLocation.getProvider());

		// Determine location quality using a combination of timeliness and accuracy
		if (isMoreAccurate) { return true;
		} else if (isNewer && !isLessAccurate) { return true;
		} else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {   return true;  }
		return false;
	}

	/** Checks whether two providers are the same */
	private boolean isSameProvider(String provider1, String provider2) 
	{
		if (provider1 == null) { return provider2 == null;}
		return provider1.equals(provider2);
	}
}
