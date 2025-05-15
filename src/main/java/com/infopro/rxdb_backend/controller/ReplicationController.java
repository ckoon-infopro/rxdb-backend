package com.infopro.rxdb_backend.controller;

import com.infopro.rxdb_backend.dto.CheckpointDto;
import com.infopro.rxdb_backend.dto.ReplicationDocumentsDto;
import com.infopro.rxdb_backend.dto.PushRequestDto;
import com.infopro.rxdb_backend.service.ReplicationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/replicate")
public class ReplicationController {

        @Autowired
    private ReplicationService replicationService;

    /**
     * Endpoint for clients to pull changes from the server.
     * @param lastCheckpoint The last checkpoint from the client.
     * @param limit The maximum number of documents to return.
     * @return A list of documents and the new checkpoint.
     */
    @GetMapping("/pull")
    public ResponseEntity<ReplicationDocumentsDto<Map<String, Object>>> pullChanges(
            @RequestParam(required = false) String updatedAt, // RxDB sends updatedAt as string
            @RequestParam(required = false) String id, // RxDB sends id as string
            @RequestParam(defaultValue = "10") int limit) {
        ReplicationDocumentsDto<Map<String, Object>> result = replicationService.pullFromServer(updatedAt, id, limit);
        return ResponseEntity.ok(result);
    }

    /**
     * Endpoint for clients to push changes to the server.
     * @param documentsWrapper A list of documents from the client.
     * @return A list of conflicts (if any).
     */
    @PostMapping("/push")
    public ResponseEntity<List<Map<String, Object>>> pushChanges(@RequestBody PushRequestDto pushRequest) {
        List<Map<String, Object>> conflicts = replicationService.pushToServer(pushRequest.getChangeRows());
        return ResponseEntity.ok(conflicts);
    }

    /**
     * Endpoint for clients to get the server's current checkpoint.
     * This is often used by RxDB to initialize replication.
     * @return The current server checkpoint.
     */
    @GetMapping("/checkpoint")
    public ResponseEntity<CheckpointDto> getCheckpoint() {
        CheckpointDto checkpoint = replicationService.getCurrentCheckpoint();
        return ResponseEntity.ok(checkpoint);
    }
}