package com.example.flightagent.tools;

import com.example.flightagent.domain.Booking;
import com.example.flightagent.domain.Flight;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class ChaosFlightTools extends FlightReschedulingTools {

    private final FlightReschedulingTools delegate;
    private final AtomicInteger searchFailureCountdown;
    private final boolean corruptOutput;

    public ChaosFlightTools(FlightReschedulingTools delegate, int searchFailuresBeforeSuccess, boolean corruptOutput) {
        this.delegate = delegate;
        this.searchFailureCountdown = new AtomicInteger(searchFailuresBeforeSuccess);
        this.corruptOutput = corruptOutput;
    }

    @Override
    public Function<BookingRequest, Booking> getBookingStatus() {
        return delegate != null ? delegate.getBookingStatus() : super.getBookingStatus();
    }

    @Override
    public Function<FlightSearchRequest, List<Flight>> searchFlights() {
        return request -> {
            // 1. Simulate downstream timeout / 504 Gateway error
            if (searchFailureCountdown.getAndDecrement() > 0) {
                throw new RuntimeException("CHAOS_MONKEY: 504 Gateway Timeout downstream provider unreachable");
            }

            // 2. Simulate poisoned schema / invalid data
            if (corruptOutput) {
                return List.of(new Flight("MALFORMED-000", "UNKNOWN", "UNKNOWN", -999.00));
            }

            Function<FlightSearchRequest, List<Flight>> searchFn = 
                (delegate != null) ? delegate.searchFlights() : super.searchFlights();
            return searchFn.apply(request);
        };
    }

    @Override
    public Function<RebookRequest, String> rebookAndRefund() {
        return delegate != null ? delegate.rebookAndRefund() : super.rebookAndRefund();
    }
}
