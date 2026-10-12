package com.railmind.ai.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.railmind.ai.provider.AIProvider.ToolSpec;

import java.util.List;

/**
 * Declares the tools the LLM is allowed to call, as provider-agnostic
 * ToolSpecs (name + description + JSON Schema parameters). Each AIProvider
 * implementation is responsible for translating this into its own wire
 * format (Anthropic's input_schema vs OpenAI's function.parameters) - this
 * class does not know or care which provider is in use.
 *
 * The LLM never touches the database directly - it can only ask for one of
 * these named, schema-validated operations, each implemented in ToolExecutor
 * against the real Spring services (TrainSearchService, BookingService, etc).
 */
public class ToolDefinitions {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static List<ToolSpec> buildToolSpecs() {
        return List.of(
                spec("searchTrains",
                        "Search for trains between two stations on a given date. Returns real availability and fares - never invent results.",
                        props(
                                prop("source", "string", "Source station code, e.g. NDLS"),
                                prop("destination", "string", "Destination station code, e.g. BCT"),
                                prop("date", "string", "Travel date in YYYY-MM-DD format"),
                                prop("travelClass", "string", "Optional class filter: SL, AC3, AC2, or AC1"),
                                prop("sortBy", "string", "Optional: recommended, cheapest, fastest, earliest_departure, reliable")
                        ),
                        List.of("source", "destination", "date")),

                spec("getTrainDetails",
                        "Get the full route (stops, distances, offsets) for a specific train.",
                        props(prop("trainId", "integer", "Numeric train id from a prior search result")),
                        List.of("trainId")),

                spec("checkAvailability",
                        "Check live seat availability by class for a specific schedule.",
                        props(prop("scheduleId", "integer", "Numeric schedule id from a prior search result")),
                        List.of("scheduleId")),

                spec("calculateFare",
                        "Calculate the exact fare for a class of travel over a known distance. Never estimate a fare yourself - always call this.",
                        props(
                                prop("trainId", "integer", "Numeric train id"),
                                prop("travelClass", "string", "SL, AC3, AC2, or AC1"),
                                prop("distanceKm", "integer", "Distance in km between source and destination")
                        ),
                        List.of("trainId", "travelClass", "distanceKm")),

                spec("findAlternativeTrains",
                        "Same as searchTrains but explicitly used to propose alternatives (e.g. after a disruption or when the first option doesn't fit constraints).",
                        props(
                                prop("source", "string", "Source station code"),
                                prop("destination", "string", "Destination station code"),
                                prop("date", "string", "Travel date in YYYY-MM-DD format")
                        ),
                        List.of("source", "destination", "date")),

                spec("getBooking",
                        "Get the authenticated user's own booking by booking id. Never returns another user's data.",
                        props(prop("bookingId", "integer", "Numeric booking id")),
                        List.of("bookingId")),

                spec("getPNR",
                        "Look up a booking by its PNR number, scoped to the authenticated user.",
                        props(prop("pnrNumber", "string", "10-digit PNR number")),
                        List.of("pnrNumber")),

                spec("calculateRefund",
                        "Calculate (without cancelling) what refund the user would receive if they cancelled a given booking now. Uses the deterministic backend refund policy - the AI must never state a refund figure that didn't come from this tool.",
                        props(prop("bookingId", "integer", "Numeric booking id")),
                        List.of("bookingId")),

                spec("createBookingDraft",
                        "Build a non-binding booking draft (train, class, passengers, seats, total fare) for the user to review. This does NOT create a real booking, hold a seat, or charge anything - it only echoes back a priced summary using real search/fare data so the frontend can render a confirmation card. The user must separately call the real booking API to actually book.",
                        props(
                                prop("scheduleId", "integer", "Numeric schedule id from a prior search"),
                                prop("trainId", "integer", "Numeric train id"),
                                prop("sourceStationCode", "string", "Source station code"),
                                prop("destinationStationCode", "string", "Destination station code"),
                                prop("travelClass", "string", "SL, AC3, AC2, or AC1"),
                                prop("passengerCount", "integer", "Number of passengers")
                        ),
                        List.of("scheduleId", "trainId", "sourceStationCode", "destinationStationCode", "travelClass", "passengerCount")),

                spec("planJourney",
                        "Get a deterministic, backend-ranked journey plan with categories: Best Overall, Cheapest, Fastest, Best Overnight, Most Reliable. Use this instead of ranking search results yourself when the user asks for a recommendation (e.g. 'best', 'cheapest', 'fastest', 'overnight') rather than a plain list.",
                        props(
                                prop("source", "string", "Source station code"),
                                prop("destination", "string", "Destination station code"),
                                prop("date", "string", "Travel date in YYYY-MM-DD format"),
                                prop("travelClass", "string", "Optional class filter: SL, AC3, AC2, or AC1"),
                                prop("maxFare", "number", "Optional maximum fare per passenger to filter by")
                        ),
                        List.of("source", "destination", "date")),

                spec("createCancellationDraft",
                        "Build a non-binding cancellation preview for the user's own booking: current status plus the exact refund they would receive if they cancelled right now (via the deterministic refund policy). This does NOT cancel anything. The user must separately call POST /api/v1/cancellations to actually cancel.",
                        props(prop("bookingId", "integer", "Numeric booking id")),
                        List.of("bookingId")),

                spec("getNotifications",
                        "Get the authenticated user's own in-app notifications (booking confirmations, payment failures, cancellations, refunds) and unread count.",
                        props(),
                        List.of()),

                spec("getTripStatus",
                        "Get the current schedule status (on time / delayed / cancelled / platform / departure time) for the authenticated user's own booking, plus its Trip Guardian alert timeline if any disruption has occurred. Use this instead of guessing when the user asks 'is my train on time' or 'what's the status of my trip'.",
                        props(prop("bookingId", "integer", "Numeric booking id")),
                        List.of("bookingId")),

                spec("getUserPreferences",
                        "Get the authenticated user's saved travel preferences (preferred class/berth, budget, fastest/cheapest/overnight preference). Call this before planJourney when the user hasn't stated a preference explicitly, so recommendations reflect their standing preferences instead of asking them to repeat themselves.",
                        props(),
                        List.of()),

                spec("policySearch",
                        "Search RailMind's official policy documents (cancellation, refunds, booking, waiting list, quota, passenger, baggage, general travel rules) for passages relevant to a policy question. Returns the actual retrieved excerpts with their source document and a similarity score - NEVER answer a policy question from general knowledge, always call this first and base your answer only on what it returns. If it returns no results, tell the user you don't have enough verified information rather than guessing.",
                        props(prop("query", "string", "The policy question or topic to search for, e.g. 'what happens if I cancel 2 hours before departure'")),
                        List.of("query"))
        );
    }

    private static ToolSpec spec(String name, String description, ObjectNode properties, List<String> required) {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        schema.set("properties", properties);
        ArrayNode req = MAPPER.createArrayNode();
        required.forEach(req::add);
        schema.set("required", req);
        return new ToolSpec(name, description, schema);
    }

    private static ObjectNode props(ObjectNode... entries) {
        ObjectNode props = MAPPER.createObjectNode();
        for (ObjectNode e : entries) {
            props.setAll(e);
        }
        return props;
    }

    private static ObjectNode prop(String name, String type, String description) {
        ObjectNode field = MAPPER.createObjectNode();
        ObjectNode inner = MAPPER.createObjectNode();
        inner.put("type", type);
        inner.put("description", description);
        field.set(name, inner);
        return field;
    }
}
