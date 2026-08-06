package com.example.flightagent.tools;

import com.example.flightagent.domain.Booking;
import com.example.flightagent.domain.Flight;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.List;
import java.util.function.Function;

@Configuration
public class FlightReschedulingTools {

    @Bean
    @Description("Fetches booking record details for a passenger by booking ID.")
    public Function<BookingRequest, Booking> getBookingStatus() {
        return request -> new Booking(request.bookingId(), "Prachi Panditrao", "AC-402", 450.00);
    }

    @Bean
    @Description("Searches candidate replacement flights matching destination route.")
    public Function<FlightSearchRequest, List<Flight>> searchFlights() {
        return request -> List.of(
            new Flight("AC-501", "YYZ", "SEA", 380.00),
            new Flight("AC-509", "YYZ", "SEA", 490.00),
            new Flight("AC-999", "YYZ", "SEA", 620.00)
        );
    }

    @Bean
    @Description("Rebooks passenger to a new flight and issues a refund for any price difference.")
    public Function<RebookRequest, String> rebookAndRefund() {
        return request -> {
            if (request.newPrice() > 500.00) {
                throw new IllegalArgumentException("Policy Violation: Selected flight price ($" + request.newPrice() + ") exceeds $500 threshold.");
            }
            return "SUCCESS: Rebooked " + request.bookingId() + " to flight " + request.newFlightId() + ". Refund difference processed.";
        };
    }

    public record BookingRequest(String bookingId) {}
    public record FlightSearchRequest(String destination) {}
    public record RebookRequest(String bookingId, String newFlightId, double newPrice) {}
}