package com.example.flightagent.springai;

import com.example.flightagent.tools.FlightReschedulingTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallbackWrapper;
import org.springframework.stereotype.Service;

@Service
public class SpringAiAgentService {

    private final ChatClient chatClient;

    public SpringAiAgentService(ChatClient.Builder chatClientBuilder, FlightReschedulingTools tools) {

        // Explicitly specify .withInputType(...) so Spring AI deserializes JSON into records instead of LinkedHashMap
        FunctionCallback getBookingStatusCallback = FunctionCallbackWrapper.builder(tools.getBookingStatus())
                .withName("getBookingStatus")
                .withDescription("Fetches booking record details for a passenger by booking ID.")
                .withInputType(FlightReschedulingTools.BookingRequest.class)
                .build();

        FunctionCallback searchFlightsCallback = FunctionCallbackWrapper.builder(tools.searchFlights())
                .withName("searchFlights")
                .withDescription("Searches candidate replacement flights matching destination route.")
                .withInputType(FlightReschedulingTools.FlightSearchRequest.class)
                .build();

        FunctionCallback rebookAndRefundCallback = FunctionCallbackWrapper.builder(tools.rebookAndRefund())
                .withName("rebookAndRefund")
                .withDescription("Rebooks passenger to a new flight and issues a refund for any price difference.")
                .withInputType(FlightReschedulingTools.RebookRequest.class)
                .build();

        this.chatClient = chatClientBuilder
                .defaultSystem("""
                    You are an enterprise travel assistant.
                    1. Retrieve the user's booking details using getBookingStatus.
                    2. Search for available flights using searchFlights.
                    3. If a flight is strictly <= $500, rebook using rebookAndRefund.
                    4. If the selected flight is > $500, DO NOT invoke rebookAndRefund. Inform the user of budget policy violation.
                    """)
                .defaultFunctions(getBookingStatusCallback, searchFlightsCallback, rebookAndRefundCallback)
                .defaultAdvisors(new MessageChatMemoryAdvisor(new InMemoryChatMemory()))
                .build();
    }

    public String processRequest(String conversationId, String userPrompt) {
        return chatClient.prompt()
                .user(userPrompt)
                .advisors(a -> a.param(MessageChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY, conversationId))
                .call()
                .content();
    }
}