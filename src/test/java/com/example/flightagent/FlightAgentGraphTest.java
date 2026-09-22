package com.example.flightagent.langgraph4j;

import com.example.flightagent.domain.Booking;
import com.example.flightagent.domain.Flight;
import com.example.flightagent.tools.FlightReschedulingTools;
import org.bsc.langgraph4j.CompiledGraph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class FlightAgentGraphTest {

    @Test
    @DisplayName("Route to Policy Fallback when candidate flight exceeds $500 threshold")
    void testGraphExecution_PolicyInterceptTriggered() throws Exception {
        // Arrange stub tools
        AtomicBoolean rebookCalled = new AtomicBoolean(false);

        FlightReschedulingTools stubTools = new FlightReschedulingTools() {
            @Override
            public Function<BookingRequest, Booking> getBookingStatus() {
                return req -> new Booking(req.bookingId(), "Jane Doe", "AC-100", 350.0);
            }

            @Override
            public Function<FlightSearchRequest, List<Flight>> searchFlights() {
                // AC-999 priced at $620 (exceeds $500 threshold)
                return req -> List.of(
                    new Flight("AC-999", "SEA", "2026-10-16 09:00", 620.0),
                    new Flight("AC-101", "SEA", "2026-10-16 11:00", 450.0)
                );
            }

            @Override
            public Function<RebookRequest, String> rebookAndRefund() {
                return req -> {
                    rebookCalled.set(true);
                    return "REBOOKED";
                };
            }
        };

        LangGraphAgentConfig agentConfig = new LangGraphAgentConfig(stubTools);
        CompiledGraph<FlightAgentState> graph = agentConfig.buildGraph().compile();

        Map<String, Object> input = Map.of(FlightAgentState.BOOKING_ID_KEY, "BKG-101");

        // Act
        var finalStateOpt = graph.invoke(input);

        // Assert
        assertTrue(finalStateOpt.isPresent(), "Graph should produce a final state");
        FlightAgentState state = finalStateOpt.get();

        assertEquals(620.0, state.selectedPrice());
        List<String> messages = state.messages();

        // Verify policy intercept message is present in state
        boolean intercepted = messages.stream().anyMatch(m -> m.contains("POLICY INTERCEPT"));
        assertTrue(intercepted, "Should route to policy_fallback and record intercept message");

        // Verify rebook tool was never executed
        assertFalse(rebookCalled.get(), "Rebook tool should not have been called when policy intercept triggers");
    }

    @Test
    @DisplayName("Route to Rebook Node when candidate flight is <= $500 threshold")
    void testGraphExecution_SuccessfulRebooking() throws Exception {
        // Arrange stub tools
        AtomicBoolean rebookCalled = new AtomicBoolean(false);

        FlightReschedulingTools stubTools = new FlightReschedulingTools() {
            @Override
            public Function<BookingRequest, Booking> getBookingStatus() {
                return req -> new Booking(req.bookingId(), "John Smith", "AC-100", 400.0);
            }

            @Override
            public Function<FlightSearchRequest, List<Flight>> searchFlights() {
                // Price <= $500
                return req -> List.of(
                    new Flight("AC-501", "SEA", "2026-10-16 14:00", 480.0)
                );
            }

            @Override
            public Function<RebookRequest, String> rebookAndRefund() {
                return req -> {
                    rebookCalled.set(true);
                    return "SUCCESS: Rebooked on AC-501";
                };
            }
        };

        LangGraphAgentConfig agentConfig = new LangGraphAgentConfig(stubTools);
        CompiledGraph<FlightAgentState> graph = agentConfig.buildGraph().compile();

        Map<String, Object> input = Map.of(FlightAgentState.BOOKING_ID_KEY, "BKG-102");

        // Act
        var finalStateOpt = graph.invoke(input);

        // Assert
        assertTrue(finalStateOpt.isPresent(), "Graph should produce a final state");
        FlightAgentState state = finalStateOpt.get();

        assertEquals(480.0, state.selectedPrice());
        assertTrue(state.messages().contains("SUCCESS: Rebooked on AC-501"));

        // Verify rebook tool was executed
        assertTrue(rebookCalled.get(), "Rebook tool must be invoked when price <= $500");
    }
}
