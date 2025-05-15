package com.infopro.rxdb_backend.service;

import com.infopro.rxdb_backend.dto.CheckpointDto;
import com.infopro.rxdb_backend.dto.ReplicationDocumentsDto;
import com.infopro.rxdb_backend.dto.ChangeRowDto;
import com.infopro.rxdb_backend.model.Document; 
import com.infopro.rxdb_backend.repository.DocumentRepository; 
import org.springframework.beans.factory.annotation.Autowired; 
import org.springframework.data.domain.PageRequest; 
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors; 

@Service
public class ReplicationService {

    private final DocumentRepository documentRepository;
    private final AtomicLong serverCheckpointTimestamp = new AtomicLong(0); // Stores the last known server timestamp
                                                                            // for checkpointing

    @Autowired
    public ReplicationService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
        // Initialize checkpoint from DB or set to a sensible default if DB is empty
        updateServerCheckpointTimestamp();
    }

    private void updateServerCheckpointTimestamp() {
        // This could be more sophisticated, e.g., storing a dedicated checkpoint
        // document
        // For now, use the latest updatedAt from any document or current time if no
        // documents
        documentRepository
                .findAll(PageRequest.of(0, 1,
                        org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC,
                                "updatedAt")))
                .stream()
                .findFirst()
                .ifPresentOrElse(
                        doc -> serverCheckpointTimestamp.set(doc.getUpdatedAt()),
                        () -> serverCheckpointTimestamp.set(System.currentTimeMillis()) // Fallback if no documents
                );
    }

    /**
     * Handles pulling changes from the server.
     * 
     * @param lastClientCheckpoint The last checkpoint received from the client.
     * @param limit                The maximum number of documents to return.
     * @return ReplicationDocumentsDto containing documents and the new server
     *         checkpoint.
     */
    @Transactional(readOnly = true)
    public ReplicationDocumentsDto<Map<String, Object>> pullFromServer(String clientUpdatedAtStr, String lastDocId,
            int limit) {
        System.out.println("Service: Pulling changes. ClientUpdatedAt: " + clientUpdatedAtStr + ", LastDocId: "
                + lastDocId + ", Limit: " + limit);

        long clientUpdatedAt = 0L;
        if (clientUpdatedAtStr != null && !clientUpdatedAtStr.isEmpty()) {
            try {
                clientUpdatedAt = Long.parseLong(clientUpdatedAtStr);
            } catch (NumberFormatException e) {
                System.err.println("Invalid clientUpdatedAtStr format: " + clientUpdatedAtStr + ". Defaulting to 0.");
            }
        }
        // If lastDocId is null or empty, it implies the client is asking for documents
        // strictly newer than clientUpdatedAt.
        // If client sends "0" or an empty string for lastDocId, we might need a
        // specific sentinel value for the query if the ID column cannot be null.
        // For simplicity, if lastDocId is empty, we can use a value that's
        // lexicographically smaller than any possible ID (e.g., empty string if IDs are
        // non-empty).
        // Or, adjust the query in DocumentRepository to handle null/empty lastDocId
        // appropriately.
        String effectiveLastDocId = (lastDocId == null || lastDocId.isEmpty()) ? "" : lastDocId; // Adjust if your IDs
                                                                                                 // have specific
                                                                                                 // constraints

        Pageable pageable = PageRequest.of(0, limit);
        List<Document> pulledDocs;

        if (clientUpdatedAt == 0 && (effectiveLastDocId == null || effectiveLastDocId.isEmpty())) {
            // Client has no checkpoint or a very old one, send all documents up to limit
            // Assuming findAllByOrderByUpdatedAtAscIdAsc will be changed or
            // replaced
            // For now, let's use a general findAll or adjust repository method
            pulledDocs = documentRepository.findAllByOrderByUpdatedAtAscIdAsc(pageable); // Placeholder, needs
                                                                                         // repository adjustment
        } else {
            // Client has a checkpoint, fetch documents newer than it
            pulledDocs = documentRepository.findNewerThanCheckpoint(clientUpdatedAt, effectiveLastDocId, pageable);
        }

        List<Map<String, Object>> documentsToReplicate = pulledDocs.stream()
                .map(doc -> {
                    Map<String, Object> docMap = new HashMap<>(doc.getData()); // Start with the JSON data
                    docMap.put("id", doc.getId());
                    docMap.put("updatedAt", doc.getUpdatedAt()); // Ensure this is part of the replicated doc if client
                                                                 // expects it
                    return docMap;
                })
                .collect(Collectors.toList());

        // Determine the new server checkpoint based on the last document sent, or
        // current server checkpoint if no documents sent
        String newServerCheckpointStr;
        if (!documentsToReplicate.isEmpty()) {
            Map<String, Object> lastSentDoc = documentsToReplicate.get(documentsToReplicate.size() - 1);
            // RxDB expects checkpoint as an object { lastDocId: string, lastUpdatedAt:
            // number } or similar
            // Here, we'll simplify to a string concatenation for demonstration, but a
            // structured object is better.
            // The checkpoint should represent the state *after* these documents.
            // For RxDB, the checkpoint is typically the *last returned document's*
            // (updatedAt, id).
            newServerCheckpointStr = lastSentDoc.get("updatedAt") + "_" + lastSentDoc.get("id");
        } else {
            // No documents to send, server checkpoint remains its current latest known
            // state or a minimal value if nothing exists
            // This needs to align with how client handles empty pulls and subsequent
            // requests.
            // A common strategy is to return the client's own checkpoint if nothing newer,
            // or a server's 'current' minimal checkpoint.
            updateServerCheckpointTimestamp(); // Refresh current server timestamp
            newServerCheckpointStr = String.valueOf(serverCheckpointTimestamp.get()) + "_z"; // 'z' to be
                                                                                             // lexicographically large
                                                                                             // for ID part
        }

        System.out.println("Service: Sending " + documentsToReplicate.size() + " documents. New server checkpoint: "
                + newServerCheckpointStr);
        return new ReplicationDocumentsDto<>(documentsToReplicate, newServerCheckpointStr);
    }

    /**
     * Handles pushing changes from the client to the server.
     * 
     * @param clientDocuments List of documents from the client.
     * @return List of conflicts (empty in this basic example).
     */
    @Transactional
    public List<Map<String, Object>> pushToServer(List<ChangeRowDto> changeRows) {
        System.out.println("Service: Receiving " + changeRows.size() + " change rows from client.");
        List<Map<String, Object>> conflicts = new ArrayList<>();
        long currentProcessingTime = System.currentTimeMillis(); // Use a consistent timestamp for all docs in this
                                                                 // batch

        for (ChangeRowDto changeRow : changeRows) {
            Map<String, Object> newDocClientState = changeRow.getNewDocumentState();
            Map<String, Object> assumedMasterStateClient = changeRow.getAssumedMasterState();

            if (newDocClientState == null || newDocClientState.get("id") == null) {
                conflicts.add(createErrorEntry("Malformed change row",
                        newDocClientState != null ? newDocClientState.toString() : "null"));
                continue;
            }
            String docId = (String) newDocClientState.get("id");

            Document existingDoc = documentRepository.findById(docId).orElse(null);
            boolean conflictDetected = false;

            if (existingDoc != null) { // Document exists on server
                if (assumedMasterStateClient == null) {
                    // Client thinks it's a new doc, but it exists. This is a conflict.
                    conflictDetected = true;
                    System.out.println(
                            "Service: Conflict for docId " + docId + ": Client assumed new, but document exists.");
                } else {
                    // Client provided an assumed state. Compare its updatedAt with server's
                    // updatedAt.
                    Long serverUpdatedAt = existingDoc.getUpdatedAt();
                    
                    Object clientAssumedUpdatedAtObj = assumedMasterStateClient.get("updatedAt");
                    Long clientAssumedUpdatedAt = null;
                    if (clientAssumedUpdatedAtObj instanceof Number) {
                        clientAssumedUpdatedAt = ((Number) clientAssumedUpdatedAtObj).longValue();
                    } else if (clientAssumedUpdatedAtObj instanceof String) {
                        try {
                            clientAssumedUpdatedAt = Long.parseLong((String) clientAssumedUpdatedAtObj);
                        } catch (NumberFormatException e) {
                            System.err.println("Invalid updatedAt format: " + clientAssumedUpdatedAtObj);
                        }
                    }


                    // Conflict if server's updatedAt does not match client's assumed updatedAt.
                    // (Handles cases where one is null and the other isn't, or both non-null but
                    // different)
                    if (serverUpdatedAt == null && clientAssumedUpdatedAt != null)
                        conflictDetected = true;
                    else if (serverUpdatedAt != null && clientAssumedUpdatedAt == null)
                        conflictDetected = true;
                    else if (serverUpdatedAt != null && !serverUpdatedAt.equals(clientAssumedUpdatedAt))
                        conflictDetected = true;

                    if (conflictDetected) {
                        System.out.println("Service: Conflict for docId " + docId + ". Server updatedAt: "
                                + serverUpdatedAt + ", Client's assumed updatedAt: " + clientAssumedUpdatedAt);
                    }
                }
            } else { // Document does not exist on server
                if (assumedMasterStateClient != null) {
                    // Client thinks doc exists (and provided assumed state), but it doesn't. This
                    // is a conflict.
                    // This scenario might indicate the doc was deleted on the server after client's
                    // last pull.
                    conflictDetected = true;
                    System.out.println("Service: Conflict for docId " + docId
                            + ": Client assumed existing, but document not found.");
                }

            }

            if (conflictDetected) {
                // Add server's current version to conflicts list
                Map<String, Object> serverStateForConflict = new HashMap<>();
                if (existingDoc != null) {
                    serverStateForConflict.putAll(existingDoc.getData()); // Assumes getData() returns the main content
                    serverStateForConflict.put("id", existingDoc.getId());
                    serverStateForConflict.put("updatedAt", existingDoc.getUpdatedAt());
                }
                conflicts.add(serverStateForConflict);
            } else {
                // No conflict, proceed with write/update
                Document docToSave = existingDoc != null ? existingDoc : new Document();
                docToSave.setId(docId);

                // Extract data, updatedAt, and deleted status from newDocClientState
                // RxDB typically sends the full document state, including metadata like
                // _deleted and _rev (which we might ignore or handle)
                // The 'data' field in our Document entity should store the actual application
                // data, excluding RxDB metadata if possible.
                Map<String, Object> appData = new HashMap<>(newDocClientState);
                appData.remove("id"); // id is a separate field
                appData.remove("updatedAt"); // updatedAt is a separate field
                appData.remove("_rev"); // RxDB revision, server might manage its own versioning or ignore
                appData.remove("_attachments"); // Handle if you support attachments

                docToSave.setData(appData);
                docToSave.setUpdatedAt(currentProcessingTime); // Server sets the authoritative update timestamp

                documentRepository.save(docToSave);
                System.out.println("Service: Processed document ID: " + docId + ".");
            }
        }

        if (!conflicts.isEmpty()) {
            System.out.println("Service: Push processing complete with " + conflicts.size() + " conflicts.");
        } else {
            System.out.println("Service: Push processing complete. All changes applied successfully.");
        }
        updateServerCheckpointTimestamp(); // Update server's latest known timestamp after all pushes
        return conflicts;
    }

    private Map<String, Object> createErrorEntry(String errorMessage, String details) {
        Map<String, Object> errorEntry = new HashMap<>();
        errorEntry.put("error", errorMessage);
        errorEntry.put("details", details);
        return errorEntry;
    }

    /**
     * Gets the current server checkpoint.
     * 
     * @return CheckpointDto containing the current server checkpoint.
     */
    @Transactional(readOnly = true)
    public CheckpointDto getCurrentCheckpoint() {
        // This method is for the /checkpoint endpoint.
        // The checkpoint should represent the latest state of the database that clients
        // can sync from.
        // A simple approach is the timestamp of the most recently updated document.
        // A more robust RxDB checkpoint is an object like {lastDocId, lastUpdatedAt}.
        // For now, let's use the serverCheckpointTimestamp which is updated after
        // pulls/pushes.
        updateServerCheckpointTimestamp(); // Ensure it's fresh
        String currentCpVal = String.valueOf(serverCheckpointTimestamp.get());
        // To be more RxDB compliant, this should ideally be the {updatedAt, id} of the
        // latest document.
        // Or, if no documents, a minimal value like {updatedAt: 0, id: ""}.
        // For simplicity, returning just the timestamp string. Client-side adapter
        // might need to parse this.
        System.out.println("Service: Providing current server checkpoint value: " + currentCpVal);
        return new CheckpointDto(currentCpVal); // Client might expect a more complex object
    }

    // Helper to get the latest document's ID for checkpointing, could be more
    // sophisticated
    private String getLastDocumentId() {
        Pageable pageable = PageRequest.of(0, 1, org.springframework.data.domain.Sort
                .by(org.springframework.data.domain.Sort.Direction.DESC, "updatedAt", "id"));
        List<Document> lastDocs = documentRepository.findAll(pageable).getContent();
        return lastDocs.isEmpty() ? "" : lastDocs.get(0).getId();
    }
}