package com.pm.patientservice.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "patient")
public class Patient {

    @Id
    @Column(columnDefinition = "CHAR(36)")
    @GeneratedValue
    private String id;

    @NotNull
    private String name;

    @NotNull
    @Email
    @Column(unique = true)
    private String email;

    @NotNull
    private String address;

    @NotNull
    private LocalDate dateOfBirth;

    @NotNull
    private LocalDate registeredDate;

    // 1. PHẢI CÓ Constructor không tham số
    public Patient() {
    }

    // 2. Constructor có tham số (tùy chọn, giúp bạn tạo object nhanh hơn)
    public Patient(String name, String email, String address, LocalDate dateOfBirth) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.email = email;
        this.address = address;
        this.dateOfBirth = dateOfBirth;
        this.registeredDate = LocalDate.now();
    }

    // 3. Tự động sinh ID nếu chưa có trước khi lưu vào DB
    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
        if (this.registeredDate == null) {
            this.registeredDate = LocalDate.now();
        }
    }

    // --- GETTERS & SETTERS (Giữ nguyên của bạn) ---
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public LocalDate getRegisteredDate() { return registeredDate; }
    public void setRegisteredDate(LocalDate registeredDate) { this.registeredDate = registeredDate; }
}