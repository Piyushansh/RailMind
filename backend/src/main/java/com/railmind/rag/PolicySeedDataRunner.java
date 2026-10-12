package com.railmind.rag;

import com.railmind.rag.PolicyRepositories.PolicyDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runs the REAL ingestion pipeline (chunk -> embed -> store) at startup for
 * one baseline document per required category, only if the corpus is empty.
 * This is what makes the AI assistant able to answer policy questions out of
 * the box without an admin manually uploading anything first - the content
 * below is original demo copy written for this project, not sourced from any
 * real operator's actual policy text.
 */
@Component
public class PolicySeedDataRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PolicySeedDataRunner.class);

    private final PolicyDocumentRepository documentRepository;
    private final PolicyIngestionService ingestionService;

    public PolicySeedDataRunner(PolicyDocumentRepository documentRepository, PolicyIngestionService ingestionService) {
        this.documentRepository = documentRepository;
        this.ingestionService = ingestionService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (documentRepository.count() > 0) {
            log.info("Policy corpus already populated ({} documents) - skipping seed ingestion.", documentRepository.count());
            return;
        }

        log.info("Seeding baseline policy corpus via the real ingestion pipeline...");
        seedDocuments().forEach((name, entry) ->
                ingestionService.ingest(name, entry.category(), "SEED", entry.text(), null));
        log.info("Policy corpus seeded: {} documents.", documentRepository.count());
    }

    private record SeedEntry(PolicyCategory category, String text) {}

    private Map<String, SeedEntry> seedDocuments() {
        Map<String, SeedEntry> docs = new LinkedHashMap<>();

        docs.put("Cancellation Policy", new SeedEntry(PolicyCategory.CANCELLATION, """
                A confirmed ticket can be cancelled any time before the scheduled departure of the train.
                Cancellations made more than 48 hours before departure are eligible for a full refund of
                the fare, minus any applicable cancellation fee shown at the time of booking. Cancellations
                made between 4 and 48 hours before departure are eligible for a partial refund, calculated
                by deducting 25 percent of the fare as a late-cancellation charge. Cancellations made less
                than 4 hours before departure, or after the scheduled departure time has passed, are
                eligible for a reduced refund with a 50 percent deduction, reflecting the limited ability to
                resell the seat on short notice. Once a booking's status has already moved to COMPLETED
                (the journey has taken place), it can no longer be cancelled. A cancellation cannot be
                reversed once processed; if a passenger wants to travel again after cancelling, they must
                make a new booking subject to then-current availability and fares.
                """));

        docs.put("Refund Policy", new SeedEntry(PolicyCategory.REFUNDS, """
                Refunds are calculated automatically at the moment of cancellation using a deterministic
                formula based on how many hours remain before the train's scheduled departure - the exact
                same calculation the AI assistant's calculateRefund tool uses, so a quote from the assistant
                will always match what actually happens if you confirm the cancellation. Refunds are issued
                back to the original payment method used for the booking. Processing of the refund back to
                the passenger's account can take a few business days depending on the payment provider,
                even though RailMind's internal refund record is created immediately upon cancellation.
                Refunds are only issued for the fare actually paid; any promotional discount applied at
                booking time is not refunded separately, since it was already reflected in the fare charged.
                If a booking is cancelled due to a RailMind-initiated disruption (train cancellation), a full
                refund is issued regardless of how close to departure the cancellation occurs, since the
                passenger did not choose to cancel voluntarily.
                """));

        docs.put("Booking Policy", new SeedEntry(PolicyCategory.BOOKING, """
                A booking can include between 1 and 6 passengers in a single transaction. Each passenger
                must have a full name, age, and gender recorded at the time of booking; these details appear
                on the digital ticket and cannot be edited after the booking is confirmed - if a passenger's
                name was entered incorrectly, the existing booking must be cancelled (subject to the standard
                cancellation/refund policy) and a new booking made with the correct details. Seats are held
                temporarily while a booking is being created and are only guaranteed once payment completes
                and the booking status becomes CONFIRMED; if payment is not completed within the seat-hold
                window, the held seats are released back into general availability automatically. Every
                booking request must include a client-generated idempotency key, which guarantees that
                retrying a request after a network timeout will never create a duplicate booking or charge.
                A booking's fare is calculated from the exact distance between the selected source and
                destination stations and the selected class of travel, using the same fare table the
                calculateFare tool reads from - fares are never estimated or negotiated.
                """));

        docs.put("Waiting List Policy", new SeedEntry(PolicyCategory.WAITING_LIST, """
                RailMind's current booking engine only offers seats that show as AVAILABLE at the time of
                booking; it does not currently support placing a booking on a waiting list for a fully-booked
                class. If a class shows zero availability, passengers should check the findAlternativeTrains
                results for a different train or class with open seats, or check back closer to departure, as
                cancellations by other passengers can free up seats. There is no charge or fee for checking
                availability multiple times. If waiting-list booking is introduced in a future version, this
                policy document will be updated accordingly rather than the assistant guessing at rules that
                don't yet exist in the system.
                """));

        docs.put("Quota Policy", new SeedEntry(PolicyCategory.QUOTA, """
                Seat inventory for each class on each scheduled train is fixed and tracked per class per
                schedule; RailMind does not currently subdivide that inventory into separate sub-quotas (such
                as a reserved quota for a specific passenger category). All passengers booking a given class
                on a given schedule draw from the same shared pool of available seats, allocated strictly on
                a first-confirmed basis. Administrators can adjust the total seat count for a class on a
                schedule through the admin fare/schedule management tools, which changes the pool available
                to all bookers equally rather than creating a separate reserved quota.
                """));

        docs.put("Passenger Policy", new SeedEntry(PolicyCategory.PASSENGER, """
                Passenger age must be between 0 and 120 and gender must be provided for every passenger on a
                booking; there is no separate reduced fare for children in the current fare model, all
                passengers on a booking are charged the same per-passenger fare for their class. A user may
                save up to 10 reusable passenger profiles to their account (name, age, gender, and an optional
                seat preference) so they don't need to re-enter the same details on every future booking; a
                saved passenger can be selected during checkout instead of typing their details again. Saving
                a passenger does not affect any of that passenger's existing past or current bookings, since
                each booking stores its own independent copy of the passenger's details at the time of
                booking. Removing a saved passenger from your list does not cancel or alter any booking they
                were already added to.
                """));

        docs.put("Baggage Policy", new SeedEntry(PolicyCategory.BAGGAGE, """
                RailMind does not currently charge a separate baggage fee or enforce a specific weight limit
                through the booking system - baggage allowance and any weight restrictions are governed by
                the operating railway's own general conditions of carriage, which passengers should check
                directly with the train operator for specifics on their route. Passengers are responsible for
                their own baggage at all times; RailMind's platform handles booking, ticketing, and journey
                management only, and does not provide baggage tracking, insurance, or liability coverage for
                lost or damaged items.
                """));

        docs.put("General Travel Policy", new SeedEntry(PolicyCategory.GENERAL_TRAVEL, """
                Passengers should arrive at the departure station with enough time to locate their platform
                and coach before the scheduled departure time; RailMind's Trip Guardian feature, if enabled
                on a booking, will proactively alert passengers to delays, cancellations, or platform changes
                affecting their journey as soon as the schedule is updated. A valid, confirmed digital ticket
                (showing PNR, train, date, class, coach, and seat) generated through the app is sufficient for
                travel; passengers do not need a separate printed ticket, though the app does support printing
                or downloading one if preferred. If a passenger's confirmed booking is affected by a
                RailMind-initiated schedule change (delay, cancellation, or platform change), they will be
                offered the option to accept a suggested alternative or to cancel for a full refund as
                described in the cancellation policy's disruption clause.
                """));

        return docs;
    }
}
