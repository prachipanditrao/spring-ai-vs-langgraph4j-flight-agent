package com.example.flightagent.langgraph4j;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FlightAgentState extends AgentState {

    public static final String MESSAGES_KEY = "messages";
    public static final String BOOKING_ID_KEY = "booking_id";
    public static final String SELECTED_PRICE_KEY = "selected_price";
    public static final String RETRY_COUNT_KEY = "retry_count";
    public static final String LAST_ERROR_KEY = "last_error";

    // Define schema using Channels factory methods
    public static final Map<String, Channel<?>> SCHEMA = Map.of(
        MESSAGES_KEY, Channels.appender(ArrayList::new),
        BOOKING_ID_KEY, Channels.base(() -> ""),
        SELECTED_PRICE_KEY, Channels.base(() -> 0.0),
        RETRY_COUNT_KEY, Channels.base(() -> 0),
        LAST_ERROR_KEY, Channels.base(() -> "")
    );

    public FlightAgentState(Map<String, Object> initData) {
        super(initData);
    }

    public String bookingId() {
        return this.<String>value(BOOKING_ID_KEY).orElse("");
    }

    public Double selectedPrice() {
        return this.<Double>value(SELECTED_PRICE_KEY).orElse(0.0);
    }

    public Integer retryCount() {
        return this.<Integer>value(RETRY_COUNT_KEY).orElse(0);
    }

    public String lastError() {
        return this.<String>value(LAST_ERROR_KEY).orElse("");
    }

    @SuppressWarnings("unchecked")
    public List<String> messages() {
        return this.<List<String>>value(MESSAGES_KEY).orElseGet(List::of);
    }
}
