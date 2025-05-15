package com.infopro.rxdb_backend.dto;

import java.util.Map;

public class ChangeRowDto {
    private Map<String, Object> newDocumentState;
    private Map<String, Object> assumedMasterState;

    // Default constructor
    public ChangeRowDto() {
    }

    // Constructor with all fields
    public ChangeRowDto(Map<String, Object> newDocumentState, Map<String, Object> assumedMasterState) {
        this.newDocumentState = newDocumentState;
        this.assumedMasterState = assumedMasterState;
    }

    // Getters and Setters
    public Map<String, Object> getNewDocumentState() {
        return newDocumentState;
    }

    public void setNewDocumentState(Map<String, Object> newDocumentState) {
        this.newDocumentState = newDocumentState;
    }

    public Map<String, Object> getAssumedMasterState() {
        return assumedMasterState;
    }

    public void setAssumedMasterState(Map<String, Object> assumedMasterState) {
        this.assumedMasterState = assumedMasterState;
    }

    @Override
    public String toString() {
        return "ChangeRowDto{" +
                "newDocumentState=" + newDocumentState +
                ", assumedMasterState=" + assumedMasterState +
                '}';
    }
}