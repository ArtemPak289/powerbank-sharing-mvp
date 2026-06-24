package com.powerbank.station.repository;

import com.powerbank.station.entity.Station;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface StationRepository extends JpaRepository<Station, UUID> {
    
    // Very basic distance approximation for MVP. Real app would use PostGIS.
    @Query("SELECT s FROM Station s WHERE " +
           "(6371 * acos(cos(radians(:lat)) * cos(radians(s.latitude)) * cos(radians(s.longitude) - radians(:lng)) + sin(radians(:lat)) * sin(radians(s.latitude)))) < :radiusKm")
    List<Station> findNearbyStations(@Param("lat") double lat, @Param("lng") double lng, @Param("radiusKm") double radiusKm);
}
