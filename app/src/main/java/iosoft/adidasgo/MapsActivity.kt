package iosoft.adidasgo

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.net.Uri
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.util.Log

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment

import android.support.v4.app.ActivityCompat
import android.support.v4.app.ActivityCompat.requestPermissions
import android.view.View
import android.widget.Toast
import com.bumptech.glide.Glide
import com.github.kittinunf.fuel.android.extension.responseJson
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.result.Result
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.*
import com.google.android.gms.tasks.Task
import kotlinx.android.synthetic.main.fragment_score.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI


class MapsActivity : AppCompatActivity(), OnMapReadyCallback, ScoreFragment.OnFragmentInteractionListener {

    override fun onFragmentInteraction(uri: Uri) {

    }

    private lateinit var mMap: GoogleMap
    private lateinit var mFusedLocationProviderClient: FusedLocationProviderClient
    private var mLocationPermissionGranted: Boolean = false
    private var PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: Int = 200
    private lateinit var mLastKnownLocation: Location
    private lateinit var mDefaultLocation: LatLng
    private lateinit var locationCallback: LocationCallback
    private lateinit var locationRequest: LocationRequest
    private var locationUpdateState = false
    private lateinit var listaPuntos: ArrayList<LatLng>
    private var routing : Boolean = false
    private var finished : Boolean = false
    private var startTime : Long = 0
    private var stopTime : Long = 0

    private var userTeam : String? = null
    private var userName : String? = null
    private var userPhoto : String? = null

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
        private const val REQUEST_CHECK_SETTINGS = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        userTeam = intent.getStringExtra("team")
        userName = intent.getStringExtra("displayName")
        userPhoto = intent.getStringExtra("urlPhoto")

        when (userTeam) {
            "1" -> userTeamItem.setText("Team Technique")
            "2" -> userTeamItem.setText("Team Control")
            "3" -> userTeamItem.setText("Team Power")
        }
        userNameItem.setText(userName)
        Glide.with(this).load(userPhoto).into(userPhotoItem)

//        personRecommendationButton.setOnClickListener({
//            val personRecommendationActivity = Intent(this, PersonRecommendationActivity::class.java)
//            startActivity(personRecommendationActivity)
//        })

//        productRecommendationButton.setOnClickListener({
//            val productRecommendationActivity = Intent(this, ProductRecommendationActivity::class.java)
//            startActivity(productRecommendationActivity)
//        })

        setupPermissions()

        buttonBegin.setOnClickListener({v ->
            startTime = System.currentTimeMillis()
            routing = true
            finished = false
            buttonBegin.visibility = View.GONE
            buttonFinish.visibility = View.VISIBLE
        })

        buttonFinish.setOnClickListener({v ->
            routing = false
            finished = true
            stopTime = System.currentTimeMillis()
            buttonBegin.visibility = View.VISIBLE
            buttonFinish.visibility = View.GONE
        })

        listaPuntos = ArrayList<LatLng>()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(p0: LocationResult) {
                super.onLocationResult(p0)

                mLastKnownLocation = p0.lastLocation

                if (routing) {
                    Log.d("Tag", "En ruta")
                    listaPuntos.add(LatLng(mLastKnownLocation.latitude, mLastKnownLocation.longitude))
                    drawRouteOnMap()
                }

                if (finished) {
                    Log.d("Tag", "Finished")

                    finished = false
                    Toast.makeText(baseContext, "Has terminado la ruta!",Toast.LENGTH_LONG)
                    Toast.makeText(applicationContext, "Has terminado la ruta!",Toast.LENGTH_LONG)
                    var timeTotal : Long = (stopTime - startTime) / 1000
                    var distancia : Double = calculeTotalDistance()

                    // idUser: id google, points: array de lats y longs, distance: distancia, time: tiempo
                    var jsonArray : JSONArray = JSONArray()
                    for (a in listaPuntos) {
                        var j : JSONObject = JSONObject()
                        j.put("lat", a.latitude)
                        j.put("lng",a.longitude)
                        jsonArray.put(j)
                    }

                    Log.d("JSON", jsonArray.toString())
                    var names : JSONArray = JSONArray()
                    names.put("point")

                    var json : JSONObject = JSONObject()
                    json.put("idUser", intent.getStringExtra("id"))
                    json.put("points", jsonArray.toJSONObject(names))
                    json.put("distance", distancia)
                    json.put("time", timeTotal)

                    ("http://" + ApiHandler.IP + ":" + ApiHandler.Port + "/postRoute").
                            httpPost().body(json.toString()).responseString { request, response, result ->
                        Log.d("REQUEST", request.toString())
                        // Limpiamos los puntos una vez enviados los datos
                        listaPuntos.clear()
                    }
                }
            }
        }
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        createLocationRequest()
    }

    fun goToPersonRecommendation(v: View) {
        val personRecommendationActivity = Intent(this, PersonRecommendationActivity::class.java)
        startActivity(personRecommendationActivity)
    }

    fun goToProductRecommendation(v: View) {
        val productRecommendationActivity = Intent(this, ProductRecommendationActivity::class.java)
        startActivity(productRecommendationActivity)
    }
    private fun calculeTotalDistance(): Double {
        if (listaPuntos.size > 1 ) {
            var totalDistancia : Double = 0.0
            var lastLatitude : Double = listaPuntos[0].latitude
            var lastLongitude : Double = listaPuntos[0].longitude
            var actualLatitude : Double
            var actualLongitude : Double

            for (i in 1.. listaPuntos.size-1) {
                actualLatitude = listaPuntos[i].latitude
                actualLongitude = listaPuntos[i].longitude
                val theta = lastLongitude - actualLongitude
                totalDistancia += Math.sin(deg2rad(lastLatitude)) * Math.sin(deg2rad(actualLatitude)) + (Math.cos(deg2rad(lastLatitude))
                        * Math.cos(deg2rad(actualLatitude))
                        * Math.cos(deg2rad(theta)))
                lastLatitude = actualLatitude
                lastLongitude = actualLongitude
            }

            totalDistancia = Math.acos(totalDistancia)
            totalDistancia = rad2deg(totalDistancia)
            totalDistancia = totalDistancia * 60.0 * 1.1515
            return totalDistancia
        } else return 0.0
    }

    private fun deg2rad(deg: Double): Double {
        return deg * Math.PI / 180.0
    }

    private fun rad2deg(rad: Double): Double {
        return rad * 180.0 / Math.PI
    }

    private fun drawRouteOnMap() {
        var options: PolylineOptions = PolylineOptions().width(5F).color(Color.BLUE).geodesic(true)
        options.addAll(listaPuntos)
        var polyline: Polyline = mMap.addPolyline(options)
        var cameraPosition: CameraPosition = CameraPosition.Builder()
                .target( LatLng(listaPuntos.get(0).latitude, listaPuntos.get(0).longitude))
                .zoom(17F)
                .bearing(90F)
                .tilt(40F)
                .build()
        mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                    LOCATION_PERMISSION_REQUEST_CODE)
            return
        }
        //2
        mFusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, null /* Looper */)
    }

    private fun createLocationRequest() {
        // 1
        locationRequest = LocationRequest()
        // 2
        locationRequest.interval = 10000
        // 3
        locationRequest.fastestInterval = 5000
        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        val builder = LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest)

        // 4
        val client = LocationServices.getSettingsClient(this)
        val task = client.checkLocationSettings(builder.build())

        // 5
        task.addOnSuccessListener {
            locationUpdateState = true
            startLocationUpdates()
        }
        task.addOnFailureListener { e ->
            // 6
            if (e is ResolvableApiException) {
                // Location settings are not satisfied, but this can be fixed
                // by showing the user a dialog.
                try {
                    // Show the dialog by calling startResolutionForResult(),
                    // and check the result in onActivityResult().
                    e.startResolutionForResult(this@MapsActivity,
                            REQUEST_CHECK_SETTINGS)
                } catch (sendEx: IntentSender.SendIntentException) {
                    // Ignore the error.
                }
            }
        }
    }


    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        getLocationPermission()
        updateLocationUI()
        getDeviceLocation()


        // Add a marker in Sydney and move the camera
        /*val sydney = LatLng(-34.0, 151.0)
        mMap.addMarker(MarkerOptions().position(sydney).title("Marker in Sydney"))
        mMap.moveCamera(CameraUpdateFactory.newLatLng(sydney))*/
    }

    private fun setupPermissions() {
        val permission = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)

        if (permission != PackageManager.PERMISSION_GRANTED) {
            Log.i("TAG", "Permission to record denied")
        }
    }


    //@SuppressLint("MissingPermission")
    private fun updateLocationUI() {
        getLocationPermission()
        if (mMap == null) {
            return;
        }
        try{
            if (mLocationPermissionGranted) {
                mMap.setMyLocationEnabled(true)
                mMap.getUiSettings().setMyLocationButtonEnabled(true)

            } else {
                finish()
            }
        } catch(e: SecurityException){}
    }

    private fun getLocationPermission() {
        /*
    * Request location permission, so that we can get the location of the
    * device. The result of the permission request is handled by a callback,
    * onRequestPermissionsResult.
    */
        if (ContextCompat.checkSelfPermission(this.applicationContext,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mLocationPermissionGranted = true
        } else {
            requestPermissions(this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION)
        }
    }

    private fun getDeviceLocation() {
        /*
    * Get the best and most recent location of the device, which may be null in rare
    * cases when a location is not available.
    */
        try {
            if (mLocationPermissionGranted) {
                val locationResult = mFusedLocationProviderClient.lastLocation
                locationResult.addOnCompleteListener(this, {
                    fun onComplete(task: Task<Location>) {
                        if (task.isSuccessful()) {
                            // Set the map's camera position to the current location of the device.
                            mLastKnownLocation = task.getResult()
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                                    LatLng(mLastKnownLocation.getLatitude(),
                                            mLastKnownLocation.getLongitude()), 1.0F))
                        } else {
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(mDefaultLocation, 1.0F))
                            mMap.uiSettings.isMyLocationButtonEnabled = false
                        }
                    }
                })
            }
        } catch (e: SecurityException) {
            Log.e("Exception: %s", e.message)
        }

    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>,
                                            grantResults: IntArray) {
        mLocationPermissionGranted = false
        when (requestCode) {
            PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION -> {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mLocationPermissionGranted = true
                }
            }
        }
        updateLocationUI()
    }

    // 1
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CHECK_SETTINGS) {
            if (resultCode == Activity.RESULT_OK) {
                locationUpdateState = true
                startLocationUpdates()
            }
        }
    }

    // 2
    override fun onPause() {
        super.onPause()
        mFusedLocationProviderClient.removeLocationUpdates(locationCallback)
    }

    // 3
    public override fun onResume() {
        super.onResume()
        if (!locationUpdateState) {
            startLocationUpdates()
        }
    }

}
