package com.powerbank.station.repository;

import com.powerbank.station.entity.Powerbank;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PowerbankRepository extends JpaRepository<Powerbank, UUID> {
    List<Powerbank> findByStationIdAndStatus(UUID stationId, Powerbank.PowerbankStatus status);
    Optional<Powerbank> findByStationIdAndSlotNumber(UUID stationId, Integer slotNumber);
    List<Powerbank> findByStationId(UUID stationId);
}
