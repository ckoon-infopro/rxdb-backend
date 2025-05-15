package com.infopro.rxdb_backend.service;

import com.infopro.rxdb_backend.dto.CheckpointDto;
import com.infopro.rxdb_backend.dto.ReplicationDocumentsDto;
import com.infopro.rxdb_backend.dto.ChangeRowDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ReplicationService {

    // In-memory storage for documents and checkpoints for demonstration purposes.
    // In a real application, you would use a persistent database.
    private final Map<String, Map<String, Object>> documentsStore = new ConcurrentHashMap<>();
    private final AtomicLong serverCheckpoint = new AtomicLong(System.currentTimeMillis());
    private final List<Map<String, Object>> serverDocuments = new ArrayList<>(); // Simulates a DB

    /**
     * Handles pulling changes from the server.
     * @param lastClientCheckpoint The last checkpoint received from the client.
     * @param limit The maximum number of documents to return.
     * @return ReplicationDocumentsDto containing documents and the new server checkpoint.
     */
    public ReplicationDocumentsDto<Map<String, Object>> pullFromServer(String updatedAt, String id, int limit) {
        System.out.println("Service: Pulling changes. UpdatedAt: " + updatedAt + ", ID: " + id + ", Limit: " + limit);

        // Simulate fetching documents newer than the client's checkpoint
        // This is a very basic simulation. A real implementation would query a database based on the checkpoint.
        List<Map<String, Object>> newDocuments = new ArrayList<>();
        long clientCp = 0;
        if (updatedAt != null && !updatedAt.isEmpty()) {
            try {
                clientCp = Long.parseLong(updatedAt);
            } catch (NumberFormatException e) {
                System.err.println("Invalid updatedAt format: " + updatedAt);
                // Potentially handle error or default to 0
            }
        }

        synchronized (serverDocuments) {
            for (Map<String, Object> doc : serverDocuments) {
                // Assuming documents have a 'updated_at' field that's a long timestamp
                // Or, if checkpoints are sequential, compare directly.
                // For this example, let's assume checkpoint is a timestamp and documents have 'updated_at'
                // This logic needs to be robust based on your checkpoint strategy.
                // Here, we'll just send all documents if clientCp is 0, or newer ones.
                // This is a placeholder for actual checkpoint logic.
                if (newDocuments.size() < limit) {
                    // Simple example: if doc has 'id' and 'updated_at', and 'updated_at' > clientCp
                    // For now, let's just add from the stored documents if they are "newer"
                    // This part needs to be adapted to how you manage document versions and checkpoints.
                    newDocuments.add(new HashMap<>(doc)); // Send a copy
                }
            }
        }

        String newServerCheckpoint = String.valueOf(serverCheckpoint.get());
        System.out.println("Service: Sending " + newDocuments.size() + " documents. New server checkpoint: " + newServerCheckpoint);
        return new ReplicationDocumentsDto<>(newDocuments, newServerCheckpoint);
    }

    /**
     * Handles pushing changes from the client to the server.
     * @param clientDocuments List of documents from the client.
     * @return List of conflicts (empty in this basic example).
     */
    public List<Map<String, Object>> pushToServer(List<ChangeRowDto> changeRows) {
        System.out.println("Service: Receiving " + changeRows.size() + " change rows from client.");
        List<Map<String, Object>> conflicts = new ArrayList<>();

        synchronized (serverDocuments) { // Synchronizes access to documentsStore and serverDocuments
            for (ChangeRowDto changeRow : changeRows) {
                Map<String, Object> newDocumentState = changeRow.getNewDocumentState();
                Map<String, Object> assumedMasterState = changeRow.getAssumedMasterState();

                if (newDocumentState == null || newDocumentState.get("id") == null) {
                    System.err.println("Service: Change row with invalid newDocumentState received, skipping: " + changeRow);
                    // Consider adding malformed input to a special error list to return to client
                    Map<String, Object> errorEntry = new HashMap<>();
                    errorEntry.put("error", "Malformed change row");
                    errorEntry.put("changeRow", changeRow.toString()); // Or a more structured representation
                    conflicts.add(errorEntry); // Or a dedicated error list
                    continue;
                }
                String docId = (String) newDocumentState.get("id");

                // Fetch the current state of the document from the server's primary store
                Map<String, Object> realMasterState = documentsStore.get(docId);

                boolean conflictDetected = false;
                if (realMasterState != null) { // Document exists on server
                    if (assumedMasterState == null) {
                        // Client assumes document is new (no assumedMasterState), but it exists on server.
                        conflictDetected = true;
                        System.out.println("Service: Conflict for docId " + docId + ": Client assumed new, but document exists.");
                    } else {
                        // Client provided an assumed state, compare its 'updatedAt' with server's 'updatedAt'.
                        Object realUpdatedAt = realMasterState.get("updatedAt");
                        Object assumedUpdatedAt = assumedMasterState.get("updatedAt");

                        if (realUpdatedAt == null && assumedUpdatedAt != null) {
                            conflictDetected = true; // Server has no updatedAt, client assumes one.
                        } else if (realUpdatedAt != null && assumedUpdatedAt == null) {
                            conflictDetected = true; // Server has updatedAt, client assumes none.
                        } else if (realUpdatedAt != null && !realUpdatedAt.equals(assumedUpdatedAt)) {
                            // Both have updatedAt, but they are different.
                            conflictDetected = true;
                        } // If both are null, no conflict based on updatedAt.

                        if (conflictDetected) {
                             System.out.println("Service: Conflict for docId " + docId + ". Server updatedAt: " + realUpdatedAt + ", Client's assumed updatedAt: " + assumedUpdatedAt);
                        }
                    }
                }
                // If realMasterState is null, client is creating a new document. No conflict based on existing data.

                if (conflictDetected) {
                    conflicts.add(new HashMap<>(realMasterState)); // Add a copy of the server's version to conflicts
                } else {
                    // No conflict, write/update the document.
                    // Ensure 'updatedAt' is current. If client is expected to set it, this is fine.
                    // Otherwise, server should set/update it: newDocumentState.put("updatedAt", System.currentTimeMillis());
                    // For this example, we assume newDocumentState contains the correct updatedAt if applicable.

                    documentsStore.put(docId, new HashMap<>(newDocumentState)); // Update/insert in map store

                    // Keep serverDocuments list consistent with documentsStore
                    final String finalDocId = docId; // Required for lambda expression
                    serverDocuments.removeIf(doc -> finalDocId.equals(doc.get("id")));
                    serverDocuments.add(new HashMap<>(newDocumentState)); // Store a copy

                    System.out.println("Service: Processed document ID: " + docId + " (no conflict detected).");
                }
            }
        }

        // Update server checkpoint after processing all client changes
        long newCp = System.currentTimeMillis();
        serverCheckpoint.set(newCp);
        System.out.println("Service: Push processing complete. New server checkpoint: " + newCp);

        return conflicts;
    }

    /**
     * Gets the current server checkpoint.
     * @return CheckpointDto containing the current server checkpoint.
     */
    public CheckpointDto getCurrentCheckpoint() {
        String currentCp = String.valueOf(serverCheckpoint.get());
        System.out.println("Service: Providing current checkpoint: " + currentCp);
        return new CheckpointDto(currentCp);
    }
}