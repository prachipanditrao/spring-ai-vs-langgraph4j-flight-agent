package com.example.flightagent.langgraph4j;

import com.example.flightagent.domain.Booking;
import com.example.flightagent.domain.Flight;
import com.example.flightagent.tools.ChaosFlightTools;
import com.example.flightagent.tools.FlightReschedulingTools;
import org.bsc.langgraph4j.CompiledGraph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class ChaosResilienceGraphTest {

    @Test
    @DisplayName("Self-heal: Graph retries search on transient 504 timeout and completes rebooking")
    void testTransientFailure_RecoversAndCompletes() throws Exception {
        AtomicBoolean rebookCalled = new AtomicBoolean(false);

        FlightReschedulingTools healthyTools = new FlightReschedulingTools() {
            @Override
            public Function<BookingRequest, Booking> getBookingStatus() {
                return req -> new Booking(req.bookingId(), "Prachi Panditrao", "AC-402", 450.00);
            }

            @Override
            public Function<FlightSearchRequest, List<Flight>> searchFlights() {
                return req -> List.of(
                    new Flight("AC-501", "YYZ", "SEA", 380.00),
                    new Flight("AC-509", "YYZ", "SEA", 490.00)
                );
            }

            @Override
            public Function<RebookRequest, String> rebookAndRefund() {
                return req -> {
                    rebookCalled.set(true);
                    return "SUCCESS: Rebooked " + req.bookingId() + " to " + req.newFlightId();
                };
            }
        };

        // Downstream search fails once with 504 Timeout, then succeeds on retry #2
        ChaosFlightTools chaosTools = new ChaosFlightTools(healthyTools, 1, false);

        LangGraphAgentConfig agentConfig = new LangGraphAgentConfig(chaosTools);
        CompiledGraph<FlightAgentState> graph = agentConfig.buildGraph().compile();

        Map<String, Object> input = Map.of(FlightAgentState.BOOKING_ID_KEY, "BKG-CHAOS-001");
        var finalStateOpt = graph.invoke(input);

        assertTrue(finalStateOpt.isPresent(), "Graph must complete execution");
        FlightAgentState state = finalStateOpt.get();

        // 1. Verify transient retry was tracked in state
        assertEquals(1, state.retryCount(), "Graph should execute and record exactly one retry");

        // 2. Verify subsequent recovery proceeded to compliant rebooking
        assertEquals(380.00, state.selectedPrice());
        assertTrue(rebookCalled.get(), "Rebook tool must execute after successful retry recovery");
    }

    @Test
    @DisplayName("Circuit trip: Fallback to cached/safe state when search tool repeatedly fails")
    void testPersistentFailure_TripsCircuitBreaker() throws Exception {
        AtomicBoolean rebookCalled = new AtomicBoolean(false);

        FlightReschedulingTools failingTools = new FlightReschedulingTools() {
            @Override
            public Function<BookingRequest, Booking> getBookingStatus() {
                return req -> new Booking(req.bookingId(), "Prachi Panditrao", "AC-402", 450.00);
            }

            @Override
            public Function<FlightSearchRequest, List<Flight>> searchFlights() {
                return req -> List.of(new Flight("AC-501", "YYZ", "SEA", 380.00));
            }

            @Override
            public Function<RebookRequest, String> rebookAndRefund() {
                return req -> {
                    rebookCalled.set(true);
                    return "REBOOKED";
                };
            }
        };

        // Fail 5 times (persistently broken downstream service)
        ChaosFlightTools chaosTools = new ChaosFlightTools(failingTools, 5, false);

        LangGraphAgentConfig agentConfig = new LangGraphAgentConfig(chaosTools);
        CompiledGraph<FlightAgentState> graph = agentConfig.buildGraph().compile();

        Map<String, Object> input = Map.of(FlightAgentState.BOOKING_ID_KEY, "BKG-CHAOS-002");
        var finalStateOpt = graph.invoke(input);

        assertTrue(finalStateOpt.isPresent(), "Graph must terminate safely");
        FlightAgentState state = finalStateOpt.get();

        // 1. Circuit breaker must trip after reaching max retry ceiling (2)
        assertEquals(2, state.retryCount(), "Graph must cap retries at threshold 2");

        // 2. Guardrail assertion: never attempt rebooking when search state failed
        assertFalse(rebookCalled.get(), "Mutating rebook tool must NEVER execute when dependency is down");

        // 3. Confirm fallback/circuit-breaker message recorded
        boolean circuitTripped = state.messages().stream()
                .anyMatch(msg -> msg.contains("CIRCUIT_BREAKER") || msg.contains("FAILED"));
        assertTrue(circuitTripped, "Fallback message should be written to state messages channel");
    }
}
