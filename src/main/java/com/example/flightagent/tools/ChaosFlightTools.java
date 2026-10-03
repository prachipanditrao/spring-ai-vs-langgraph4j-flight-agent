package com.example.flightagent.tools;

import com.example.flightagent.domain.Booking;
import com.example.flightagent.domain.Flight;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class ChaosFlightTools implements FlightReschedulingTools {

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
        return delegate.getBookingStatus();
    }

    @Override
    public Function<FlightSearchRequest, List<Flight>> searchFlights() {
        return req -> {
            // Simulate transient downstream outage or API timeout
            if (searchFailureCountdown.getAndDecrement() > 0) {
                throw new RuntimeException("CHAOS_MONKEY: 504 Gateway Timeout downstream provider unreachable");
            }
            
            // Simulate poison-pill schema mutation / data corruption
            if (corruptOutput) {
                return List.of(new Flight("MALFORMED-000", null, "INVALID_DATE", -999.0));
            }

            return delegate.searchFlights().apply(req);
        };
    }

    @Override
    public Function<RebookRequest, String> rebookAndRefund() {
        return delegate.rebookAndRefund();
    }
}
