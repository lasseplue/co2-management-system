package com.iu.co2management.co2_management_system.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.time.LocalDate;

@Entity
public class EmissionEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String category;

    private double amountKgCo2e;

    private LocalDate date;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser recordedBy;

    protected EmissionEntry() {
    }

    public EmissionEntry(String category, double amountKgCo2e, LocalDate date,
                         Location location, AppUser recordedBy) {
        this.category = category;
        this.amountKgCo2e = amountKgCo2e;
        this.date = date;
        this.location = location;
        this.recordedBy = recordedBy;
    }

    public Long getId() {
        return id;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public double getAmountKgCo2e() {
        return amountKgCo2e;
    }

    public void setAmountKgCo2e(double amountKgCo2e) {
        this.amountKgCo2e = amountKgCo2e;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public AppUser getRecordedBy() {
        return recordedBy;
    }
}