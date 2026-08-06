package com.example.flightagent.domain;

public record Booking(
    String bookingId,
    String passengerName,
    String currentFlightId,
    double pricePaid
) {}