package com.infopro.rxdb_backend.dto;

import java.util.List;

public class ReplicationDocumentsDto<T> {
    private List<T> documents;
    private String checkpoint;

    public ReplicationDocumentsDto() {
    }

    public ReplicationDocumentsDto(List<T> documents, String checkpoint) {
        this.documents = documents;
        this.checkpoint = checkpoint;
    }

    public List<T> getDocuments() {
        return documents;
    }

    public void setDocuments(List<T> documents) {
        this.documents = documents;
    }

    public String getCheckpoint() {
        return checkpoint;
    }

    public void setCheckpoint(String checkpoint) {
        this.checkpoint = checkpoint;
    }
}