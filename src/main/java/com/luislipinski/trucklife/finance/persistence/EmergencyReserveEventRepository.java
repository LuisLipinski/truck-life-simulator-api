package com.luislipinski.trucklife.finance.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmergencyReserveEventRepository extends JpaRepository<EmergencyReserveEventEntity, UUID> {}
