package com.railmind.ai.agents;

import com.railmind.ai.tools.ToolExecutor;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Responsibility: everything about an existing or proposed booking -
 * looking up a booking or PNR (ownership-scoped inside ToolExecutor),
 * pricing a non-binding booking draft, and quoting a non-binding
 * cancellation/refund preview. This agent can never actually book, cancel,
 * or refund anything - createBookingDraft and createCancellationDraft only
 * ever produce a "draft": true preview; committing either one is a
 * separate, explicit action the user takes directly against the real REST
 * API (POST /api/v1/bookings, POST /api/v1/cancellations), never something
 * this agent (or any tool call) can trigger on its own.
 */
@Component
public class BookingAgent extends AbstractToolAgent {

    private static final Set<String> ALLOWED_TOOLS = Set.of(
            "createBookingDraft", "getBooking", "getPNR", "createCancellationDraft", "calculateRefund");

    public BookingAgent(ToolExecutor toolExecutor) {
        super(toolExecutor);
    }

    @Override
    public String name() {
        return "BookingAgent";
    }

    @Override
    public Set<String> allowedTools() {
        return ALLOWED_TOOLS;
    }
}
