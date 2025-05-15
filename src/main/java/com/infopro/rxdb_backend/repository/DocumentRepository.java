package com.infopro.rxdb_backend.repository;

import com.infopro.rxdb_backend.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentRepository extends JpaRepository<Document, String> {

    // Find documents updated after a certain timestamp, optionally for a specific ID, ordered by updatedAt and then id
    // This query is a starting point and might need adjustments based on exact checkpoint logic.
    // RxDB often uses a composite checkpoint (e.g., last_updated_at + primary_key_of_last_doc).
    // If `updatedAt` is a timestamp from the client, ensure it's handled correctly regarding timezones.
    @Query("SELECT d FROM Document d WHERE d.updatedAt > :updatedAt OR (d.updatedAt = :updatedAt AND d.id > :id) ORDER BY d.updatedAt ASC, d.id ASC")
    List<Document> findNewerThanCheckpoint(@Param("updatedAt") Long updatedAt, @Param("id") String id, org.springframework.data.domain.Pageable pageable);

    // A simpler version if you only checkpoint by updatedAt timestamp
    // List<Document> findByUpdatedAtGreaterThanOrderByUpdatedAtAscIdAsc(Long updatedAt, org.springframework.data.domain.Pageable pageable);

    // Find all documents, ordered by update timestamp and ID
    // Useful for initial sync or if client checkpoint is very old/null
    List<Document> findAllByOrderByUpdatedAtAscIdAsc(org.springframework.data.domain.Pageable pageable);

}