package com.infopro.rxdb_backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import org.hibernate.annotations.Type;

import java.util.Map;

@Entity
@Table(name = "documents") // You can choose your table name
public class Document {

    @Id
    private String id;

    // Using String to store JSON data. For more complex JSON handling,
    // you might consider a custom type or a library like Jackson with appropriate converters.
    // Or, if your PostgreSQL version supports JSONB and you have the right Hibernate types,
    // you could map directly to a Map<String, Object> or a custom JSON object type.
    @Type(JsonType.class) // Requires hypersistence-utils-hibernate-6x dependency for Hibernate 6
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> data; // Stores the actual document content as JSON

    @Column(name = "updated_at")
    private Long updatedAt; // Timestamp of the last update

    // Constructors
    public Document() {
    }

    public Document(String id, Map<String, Object> data, Long updatedAt) {
        this.id = id;
        this.data = data;
        this.updatedAt = updatedAt;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    public Long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Long updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "Document{" +
                "id='" + id + '\'' +
                ", data=" + data +
                ", updatedAt=" + updatedAt +
                '}';
    }
}