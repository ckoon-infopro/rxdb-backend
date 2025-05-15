package com.infopro.rxdb_backend.dto;

public class CheckpointDto {
    private String checkpoint;

    public CheckpointDto() {
    }

    public CheckpointDto(String checkpoint) {
        this.checkpoint = checkpoint;
    }

    public String getCheckpoint() {
        return checkpoint;
    }

    public void setCheckpoint(String checkpoint) {
        this.checkpoint = checkpoint;
    }
}