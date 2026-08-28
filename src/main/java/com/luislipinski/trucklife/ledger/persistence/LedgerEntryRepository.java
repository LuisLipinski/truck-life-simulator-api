package com.luislipinski.trucklife.ledger.persistence;

import com.luislipinski.trucklife.ledger.domain.LedgerEntryType;
import com.luislipinski.trucklife.ledger.domain.LedgerSourceType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntryEntity, UUID> {
    List<LedgerEntryEntity> findAllByCareerIdOrderByRecordedAtDescEntryOrderDescIdDesc(UUID careerId, Pageable pageable);
    Optional<LedgerEntryEntity> findBySourceTypeAndSourceIdAndEntryType(LedgerSourceType sourceType, UUID sourceId, LedgerEntryType entryType);
    long countByCareerId(UUID careerId);
}
