package com.example.flightagent.langgraph4j;

import com.example.flightagent.tools.FlightReschedulingTools;
import com.example.flightagent.tools.FlightReschedulingTools.BookingRequest;
import com.example.flightagent.tools.FlightReschedulingTools.FlightSearchRequest;
import com.example.flightagent.tools.FlightReschedulingTools.RebookRequest;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Component
public class LangGraphAgentConfig {

    private final FlightReschedulingTools tools;

    public LangGraphAgentConfig(FlightReschedulingTools tools) {
        this.tools = tools;
    }

    public StateGraph<FlightAgentState> buildGraph() throws Exception {

        // Node 1: Fetch current booking details via Function.apply(...)
        NodeAction<FlightAgentState> fetchBookingNode = state -> {
            var booking = tools.getBookingStatus().apply(new BookingRequest(state.bookingId()));
            return Map.of(
                FlightAgentState.MESSAGES_KEY, List.of("Fetched booking record for passenger: " + booking.passengerName())
            );
        };

        // Node 2: Search flights & evaluate candidates via Function.apply(...)
        NodeAction<FlightAgentState> searchFlightsNode = state -> {
            var flights = tools.searchFlights().apply(new FlightSearchRequest("SEA"));
            // Simulate agent picking the $620 option (AC-999) to test policy intercept
            var selectedFlight = flights.stream()
                    .filter(f -> f.flightId().equals("AC-999"))
                    .findFirst()
                    .orElse(flights.getFirst());

            return Map.of(
                FlightAgentState.SELECTED_PRICE_KEY, selectedFlight.price(),
                FlightAgentState.MESSAGES_KEY, List.of("Selected candidate flight " + selectedFlight.flightId() + " priced at $" + selectedFlight.price())
            );
        };

        // Node 3: Rebook flight (Only reached if price <= $500) via Function.apply(...)
        NodeAction<FlightAgentState> rebookNode = state -> {
            String result = tools.rebookAndRefund().apply(new RebookRequest(state.bookingId(), "AC-501", state.selectedPrice()));
            return Map.of(
                FlightAgentState.MESSAGES_KEY, List.of(result)
            );
        };

        // Node 4: Policy Fallback Node (Executed if price > $500)
        NodeAction<FlightAgentState> policyFallbackNode = state -> Map.of(
            FlightAgentState.MESSAGES_KEY, List.of(
                "POLICY INTERCEPT: Selected flight price ($" + state.selectedPrice() + 
                ") exceeds $500 threshold. Execution bypassed rebook tool and routed to manual approval."
            )
        );

        return new StateGraph<>(FlightAgentState.SCHEMA, FlightAgentState::new)
            .addNode("fetch_booking", node_async(fetchBookingNode))
            .addNode("search_flights", node_async(searchFlightsNode))
            .addNode("rebook_flight", node_async(rebookNode))
            .addNode("policy_fallback", node_async(policyFallbackNode))

            // Define sequential transitions
            .addEdge(StateGraph.START, "fetch_booking")
            .addEdge("fetch_booking", "search_flights")

            // Conditional Edge enforcing policy at the JVM level
            .addConditionalEdges(
                "search_flights",
                edge_async(state -> state.selectedPrice() <= 500.00 ? "rebook_flight" : "policy_fallback"),
                Map.of(
                    "rebook_flight", "rebook_flight",
                    "policy_fallback", "policy_fallback"
                )
            )

            // Graph termination points
            .addEdge("rebook_flight", StateGraph.END)
            .addEdge("policy_fallback", StateGraph.END);
    }
}