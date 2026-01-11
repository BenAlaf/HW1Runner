package com.example.hw1runner

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.hw1runner.data.HighscoreEntry
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

class ScoreMapFragment : Fragment(), OnMapReadyCallback {

    private var googleMap: GoogleMap? = null
    private var noSelectionState: View? = null
    private var locationInfo: LinearLayout? = null
    private var locationScoreText: TextView? = null
    private var locationCoordsText: TextView? = null

    private var pendingEntry: HighscoreEntry? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_score_map, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        noSelectionState = view.findViewById(R.id.noSelectionState)
        locationInfo = view.findViewById(R.id.locationInfo)
        locationScoreText = view.findViewById(R.id.locationScoreText)
        locationCoordsText = view.findViewById(R.id.locationCoordsText)

        // Create and add the map fragment
        val mapFragment = SupportMapFragment.newInstance()
        childFragmentManager.beginTransaction()
            .replace(R.id.mapFrame, mapFragment)
            .commit()

        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        // Configure map style for dark theme
        try {
            map.uiSettings.isZoomControlsEnabled = true
            map.uiSettings.isMapToolbarEnabled = false
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // If there was a pending entry to show, show it now
        pendingEntry?.let {
            showScoreLocation(it)
            pendingEntry = null
        }
    }

    fun showScoreLocation(entry: HighscoreEntry) {
        // If map isn't ready yet, save for later
        if (googleMap == null) {
            pendingEntry = entry
            return
        }

        // Check if location is valid (not 0,0)
        if (entry.latitude == 0.0 && entry.longitude == 0.0) {
            noSelectionState?.visibility = View.VISIBLE
            locationInfo?.visibility = View.GONE
            return
        }

        noSelectionState?.visibility = View.GONE
        locationInfo?.visibility = View.VISIBLE

        val location = LatLng(entry.latitude, entry.longitude)

        // Update info overlay
        locationScoreText?.text = "Score: ${entry.score}"
        locationCoordsText?.text = String.format("%.4f, %.4f", entry.latitude, entry.longitude)

        // Clear previous markers and add new one
        googleMap?.clear()
        googleMap?.addMarker(
            MarkerOptions()
                .position(location)
                .title("Score: ${entry.score}")
                .snippet("${entry.getFormattedDate()} - ${entry.getFormattedDistance()}")
        )

        // Animate camera to location
        googleMap?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(location, 15f),
            1000,
            null
        )
    }

    fun clearSelection() {
        noSelectionState?.visibility = View.VISIBLE
        locationInfo?.visibility = View.GONE
        googleMap?.clear()
    }

    companion object {
        fun newInstance(): ScoreMapFragment {
            return ScoreMapFragment()
        }
    }
}
