package com.infopro.rxdb_backend.dto;

import java.util.List;

public class PushRequestDto {
    private List<ChangeRowDto> changeRows;

    // Default constructor
    public PushRequestDto() {
    }

    // Constructor with all fields
    public PushRequestDto(List<ChangeRowDto> changeRows) {
        this.changeRows = changeRows;
    }

    // Getters and Setters
    public List<ChangeRowDto> getChangeRows() {
        return changeRows;
    }

    public void setChangeRows(List<ChangeRowDto> changeRows) {
        this.changeRows = changeRows;
    }

    @Override
    public String toString() {
        return "PushRequestDto{" +
                "changeRows=" + changeRows +
                '}';
    }
}