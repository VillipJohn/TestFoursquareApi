package com.villipapp.testfoursquareapi;

import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.villipapp.testfoursquareapi.utility.RecyclerItemTouchHelper;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener,
        RecyclerItemTouchHelper.RecyclerItemTouchHelperListener{

    private GoogleMap mMap;

    // The app-specific constant when requesting the location permission
    private static final int PERMISSION_ACCESS_FINE_LOCATION = 1;

    // The client object for connecting to the Google API
    private GoogleApiClient googleApiClient;

    // The base URL for the Foursquare API
    private String foursquareBaseURL = "https://api.foursquare.com/v2/";

    // The client ID and client secret for authenticating with the Foursquare API
    private String foursquareClientID;
    private String foursquareClientSecret;

    private RecyclerView recyclerView;
    private List<FoursquareResults> foursquareResultsList;

    private RecyclerView.LayoutManager layoutManager;

    boolean isSmallView = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        // Requests for location permissions at runtime (required for API >= 23)
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_ACCESS_FINE_LOCATION);
        }

        // Creates a connection to the Google API for location services
        googleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();

        // Gets the stored Foursquare API client ID and client secret from XML
        foursquareClientID = getResources().getString(R.string.foursquare_client_id);
        foursquareClientSecret = getResources().getString(R.string.foursquare_client_secret);


        recyclerView = findViewById(R.id.recycler);

        layoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        recyclerView.setLayoutManager(layoutManager);

        // attaching the touch helper to recycler view
        ItemTouchHelper.SimpleCallback itemTouchHelperCallback = new RecyclerItemTouchHelper(0, ItemTouchHelper.UP | ItemTouchHelper.DOWN, this);
        new ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(recyclerView);
    }

    @Override
    public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction, int position) {
        if (viewHolder instanceof RecyclerAdapter.ViewHolder && direction == ItemTouchHelper.UP) {

            Log.i("MyLog", "Up");

            ViewGroup.LayoutParams params = recyclerView.getLayoutParams();
            params.height = dpToPx(400);
            recyclerView.setLayoutParams(params);

            isSmallView = false;
        }

        if (viewHolder instanceof RecyclerAdapter.ViewHolder && direction == ItemTouchHelper.DOWN) {

            Log.i("MyLog", "DOWN");

            ViewGroup.LayoutParams params = recyclerView.getLayoutParams();
            params.height = dpToPx(200);
            recyclerView.setLayoutParams(params);

            isSmallView = true;
        }

        Parcelable recyclerViewState = layoutManager.onSaveInstanceState();

        RecyclerAdapter adapter = new RecyclerAdapter(foursquareResultsList);
        recyclerView.setAdapter(adapter);

        recyclerView.getLayoutManager().onRestoreInstanceState(recyclerViewState);
    }

    //перевод dp в пиксели
    private int dpToPx(int dp) {
        float density = this.getResources()
                .getDisplayMetrics()
                .density;
        return Math.round((float) dp * density);
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        // Checks for location permissions at runtime (required for API >= 23)
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            // Makes a Google API request for the user's last known location
            Location mLastLocation = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);

            if (mLastLocation != null) {
                // The user's current latitude, longitude, and location accuracy
                String userLL = mLastLocation.getLatitude() + "," +  mLastLocation.getLongitude();
                double userLLAcc = mLastLocation.getAccuracy();

                Log.d("MyLog", "широта и долгота - " + userLL + "  точность - " + userLLAcc);

                // Builds Retrofit and FoursquareService objects for calling the Foursquare API and parsing with GSON
                Retrofit retrofit = new Retrofit.Builder()
                        .baseUrl(foursquareBaseURL)
                        .addConverterFactory(GsonConverterFactory.create())
                        .build();
                FoursquareService foursquare = retrofit.create(FoursquareService.class);

                // Calls the Foursquare API to explore nearby coffee spots
                Call<FoursquareJSON> coffeeCall = foursquare.searchCoffee(
                        foursquareClientID,
                        foursquareClientSecret,
                        userLL,
                        userLLAcc);
                coffeeCall.enqueue(new Callback<FoursquareJSON>() {
                    @Override
                    public void onResponse(Call<FoursquareJSON> call, Response<FoursquareJSON> response) {

                        // Gets the venue object from the JSON response
                        FoursquareJSON fjson = response.body();
                        FoursquareResponse fr = fjson.response;
                        FoursquareGroup fg = fr.group;
                        foursquareResultsList = fg.results;

                        for(FoursquareResults foursquareResults : foursquareResultsList) {
                            Log.d("MyLog", "Название - " + foursquareResults.venue.name);
                            // Add a marker in Sydney and move the camera
                            LatLng cafe = new LatLng(foursquareResults.venue.location.lat, foursquareResults.venue.location.lng);

                            mMap.addMarker(new MarkerOptions().position(cafe).title(foursquareResults.venue.name));

                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(cafe, 11));
                        }

                        RecyclerAdapter adapter = new RecyclerAdapter(foursquareResultsList);
                        recyclerView.setAdapter(adapter);
                    }

                    @Override
                    public void onFailure(Call<FoursquareJSON> call, Throwable t) {
                        Toast.makeText(getApplicationContext(), "Не возможно подключиться к сервису", Toast.LENGTH_LONG).show();
                        finish();
                    }
                });
            } else {
                Toast.makeText(getApplicationContext(), "Не определено точное место положения", Toast.LENGTH_LONG).show();
                finish();
            }
        }

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

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
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
    }


    private class RecyclerAdapter extends RecyclerView.Adapter<RecyclerAdapter.ViewHolder> {
        // The list of results from the Foursquare API
        private List<FoursquareResults> results;

        public RecyclerAdapter(List<FoursquareResults> results) {
            this.results = results;
        }


        private class ViewHolder extends RecyclerView.ViewHolder {
            LinearLayout container;

            TextView nameTextView;
            TextView adressTextView;
            TextView distanceTextView;
            TextView ratingTextView;
            TextView cityTextViewOne;
            TextView countryTextViewTwo;
            TextView postalCodeTextViewTwo;
            TextView latitudeTextView;
            TextView longitudeTextView;

            public ViewHolder(View view) {
                super(view);

                container = view.findViewById(R.id.container);

                // Gets the appropriate view for each venue detail
                nameTextView = view.findViewById(R.id.nameTextView);
                adressTextView = view.findViewById(R.id.addressTextView);
                distanceTextView = view.findViewById(R.id.distanceTextView);
                ratingTextView = view.findViewById(R.id.ratingTextView);
                cityTextViewOne = view.findViewById(R.id.cityTextViewOne);
                countryTextViewTwo = view.findViewById(R.id.countryTextViewTwo);
                postalCodeTextViewTwo = view.findViewById(R.id.postalCodeTextViewTwo);
                latitudeTextView = view.findViewById(R.id.latitudeTextView);
                longitudeTextView = view.findViewById(R.id.longitudeTextView);
            }
        }


        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
            View view = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.item_card, viewGroup, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, final int position) {
            // Sets each view with the appropriate venue details
            holder.nameTextView.setText(results.get(position).venue.name);
            holder.adressTextView.setText(results.get(position).venue.location.address);
            holder.distanceTextView.setText("Расстояние " + results.get(position).venue.location.distance + " метров");
            holder.ratingTextView.setText("Рейтинг - " + results.get(position).venue.rating);
            holder.cityTextViewOne.setText(results.get(position).venue.location.city);
            holder.countryTextViewTwo.setText(results.get(position).venue.location.country);
            holder.postalCodeTextViewTwo.setText("Почтовый индекс - " + results.get(position).venue.location.postalCode);
            holder.latitudeTextView.setText("Широта - " + results.get(position).venue.location.lat);
            holder.longitudeTextView.setText("Долгота - " + results.get(position).venue.location.lng);

            if(!isSmallView) {
                holder.cityTextViewOne.setVisibility(View.VISIBLE);
                holder.countryTextViewTwo.setVisibility(View.VISIBLE);
                holder.postalCodeTextViewTwo.setVisibility(View.VISIBLE);
                holder.latitudeTextView.setVisibility(View.VISIBLE);
                holder.longitudeTextView.setVisibility(View.VISIBLE);
            } else {
                holder.cityTextViewOne.setVisibility(View.GONE);
                holder.countryTextViewTwo.setVisibility(View.GONE);
                holder.postalCodeTextViewTwo.setVisibility(View.GONE);
                holder.latitudeTextView.setVisibility(View.GONE);
                holder.longitudeTextView.setVisibility(View.GONE);
            }

            holder.container.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setMarker(position);
                }
            });

            setMarker(position);
        }

        private void setMarker(int position) {
            LatLng cafe = new LatLng(results.get(position).venue.location.lat, results.get(position).venue.location.lng);
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(cafe, 16));
        }

        @Override
        public int getItemCount() {
            return results.size();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Reconnects to the Google API
        googleApiClient.connect();
    }
}
