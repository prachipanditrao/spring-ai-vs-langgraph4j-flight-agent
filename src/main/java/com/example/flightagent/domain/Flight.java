package com.example.flightagent.domain;

public record Flight(
    String flightId,
    String origin,
    String destination,
    double price
) {}