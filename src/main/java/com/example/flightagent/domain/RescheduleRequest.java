package com.example.flightagent.domain;

public record RescheduleRequest(
    String bookingId,
    String preferredDestination,
    double maxBudget
) {}