package com.example.flightagent;

import com.example.flightagent.langgraph4j.LangGraphAgentConfig;
import com.example.flightagent.langgraph4j.FlightAgentState;
import com.example.flightagent.springai.SpringAiAgentService;
import com.example.flightagent.tools.FlightReschedulingTools;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AgentComparisonRunner implements CommandLineRunner {

    private final SpringAiAgentService springAiAgentService;
    private final FlightReschedulingTools tools;

    public AgentComparisonRunner(SpringAiAgentService springAiAgentService, FlightReschedulingTools tools) {
        this.springAiAgentService = springAiAgentService;
        this.tools = tools;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("==================================================");
        System.out.println("1. RUNNING LANGGRAPH4J AGENT (DETERMINISTIC GRAPH)");
        System.out.println("==================================================");
        
        var langGraphConfig = new LangGraphAgentConfig(tools);
        var stateGraph = langGraphConfig.buildGraph();
        var compiledGraph = stateGraph.compile();

        var initialState = Map.<String, Object>of(
            FlightAgentState.BOOKING_ID_KEY, "BK-9021"
        );

        var finalState = compiledGraph.invoke(initialState);
        finalState.ifPresent(state -> System.out.println("LangGraph4j Final Log: " + state.messages()));

        System.out.println("\n==================================================");
        System.out.println("2. RUNNING SPRING AI AGENT (IMPLICIT TOOL LOOP)");
        System.out.println("==================================================");
        
        try {
            String prompt = "Please check booking BK-9021, search replacement flights to Seattle, and rebook me.";
            String response = springAiAgentService.processRequest("session-123", prompt);
            System.out.println("Spring AI Response:\n" + response);
        } catch (Exception e) {
            System.err.println("Spring AI Error during execution loop: " + e.getMessage());
        }
    }
}