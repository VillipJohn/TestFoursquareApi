/**
 * Filename: FoursquareService.java
 * Author: Matthew Huie
 *
 * FoursquareService provides a Retrofit interface for the Foursquare API.
 */

package com.villipapp.testfoursquareapi;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface FoursquareService {

    // A request to search for nearby coffee shop recommendations via the Foursquare API.
    @GET("search/recommendations?v=20161101&intent=coffee")
    Call<FoursquareJSON> searchCoffee(@Query("client_id") String clientID,
                                      @Query("client_secret") String clientSecret,
                                      @Query("ll") String ll,
                                      @Query("llAcc") double llAcc);
}