package com.pki.ra.common.model.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Set;
import java.util.stream.Stream;

import static com.pki.ra.common.model.enums.CsrStatus.*;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CsrStatusTransition — State Machine")
class CsrStatusTransitionTest {

    // =========================================================================
    // VALID TRANSITIONS — must all pass
    // =========================================================================

    @Nested
    @DisplayName("Valid transitions")
    class ValidTransitions {

        static Stream<Arguments> validTransitions() {
            return Stream.of(
                    // Pre-approval
                    Arguments.of(RECEIVED, VALIDATED),
                    Arguments.of(RECEIVED, VALIDATION_FAILED),
                    Arguments.of(VALIDATED, SUBMITTED),

                    // Approval workflow
                    Arguments.of(SUBMITTED, IN_REVIEW),
                    Arguments.of(IN_REVIEW, APPROVED),
                    Arguments.of(IN_REVIEW, REJECTED),
                    Arguments.of(IN_REVIEW, REVIEWED),
                    Arguments.of(IN_REVIEW, RETURNED),
                    Arguments.of(REVIEWED, APPROVED),
                    Arguments.of(REVIEWED, REJECTED),
                    Arguments.of(RETURNED, IN_REVIEW),
                    Arguments.of(RETURNED, CLOSED),
                    Arguments.of(REJECTED, IN_REVIEW),
                    Arguments.of(REJECTED, CLOSED),

                    // Post-approval
                    Arguments.of(APPROVED, ISSUED),
                    Arguments.of(APPROVED, FAILED),
                    Arguments.of(FAILED, APPROVED)
            );
        }

        @ParameterizedTest(name = "{0} → {1}")
        @MethodSource("validTransitions")
        void shouldAllowValidTransition(CsrStatus from, CsrStatus to) {
            assertTrue(CsrStatusTransition.canTransition(from, to),
                    from + " → " + to + " should be allowed");
        }

        @ParameterizedTest(name = "{0} → {1}")
        @MethodSource("validTransitions")
        void requireValidShouldNotThrow(CsrStatus from, CsrStatus to) {
            assertDoesNotThrow(() -> CsrStatusTransition.requireValid(from, to));
        }
    }

    // =========================================================================
    // INVALID TRANSITIONS — must all be blocked
    // =========================================================================

    @Nested
    @DisplayName("Invalid transitions")
    class InvalidTransitions {

        static Stream<Arguments> invalidTransitions() {
            return Stream.of(
                    // Cannot skip assignment
                    Arguments.of(SUBMITTED, REVIEWED),
                    Arguments.of(SUBMITTED, APPROVED),
                    Arguments.of(SUBMITTED, REJECTED),

                    // Cannot skip Maker review
                    Arguments.of(IN_REVIEW, ISSUED),
                    Arguments.of(IN_REVIEW, CLOSED),

                    // Cannot go backwards
                    Arguments.of(REVIEWED, IN_REVIEW),
                    Arguments.of(REVIEWED, SUBMITTED),
                    Arguments.of(APPROVED, IN_REVIEW),
                    Arguments.of(APPROVED, SUBMITTED),

                    // RETURNED cannot skip reassignment
                    Arguments.of(RETURNED, REVIEWED),
                    Arguments.of(RETURNED, APPROVED),

                    // Terminal states — no transitions out
                    Arguments.of(CLOSED, IN_REVIEW),
                    Arguments.of(CLOSED, SUBMITTED),
                    Arguments.of(CLOSED, APPROVED),
                    Arguments.of(ISSUED, REJECTED),
                    Arguments.of(ISSUED, CLOSED),
                    Arguments.of(ISSUED, IN_REVIEW),
                    Arguments.of(VALIDATION_FAILED, SUBMITTED),
                    Arguments.of(VALIDATION_FAILED, IN_REVIEW),

                    // Cannot self-transition
                    Arguments.of(SUBMITTED, SUBMITTED),
                    Arguments.of(IN_REVIEW, IN_REVIEW),
                    Arguments.of(APPROVED, APPROVED),

                    // Random invalid
                    Arguments.of(RECEIVED, IN_REVIEW),
                    Arguments.of(RECEIVED, APPROVED),
                    Arguments.of(VALIDATED, IN_REVIEW),
                    Arguments.of(FAILED, ISSUED),
                    Arguments.of(FAILED, REJECTED)
            );
        }

        @ParameterizedTest(name = "{0} → {1} should be BLOCKED")
        @MethodSource("invalidTransitions")
        void shouldBlockInvalidTransition(CsrStatus from, CsrStatus to) {
            assertFalse(CsrStatusTransition.canTransition(from, to),
                    from + " → " + to + " should be blocked");
        }

        @ParameterizedTest(name = "{0} → {1} should throw")
        @MethodSource("invalidTransitions")
        void requireValidShouldThrow(CsrStatus from, CsrStatus to) {
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> CsrStatusTransition.requireValid(from, to));
            assertTrue(ex.getMessage().contains("Invalid status transition"));
        }
    }

    // =========================================================================
    // TERMINAL STATES — no transitions out
    // =========================================================================

    @Nested
    @DisplayName("Terminal states")
    class TerminalStates {

        @Test
        void closedIsTerminal() {
            assertTrue(CLOSED.isTerminal());
            assertTrue(CsrStatusTransition.validTargets(CLOSED).isEmpty());
        }

        @Test
        void issuedIsTerminal() {
            assertTrue(ISSUED.isTerminal());
            assertTrue(CsrStatusTransition.validTargets(ISSUED).isEmpty());
        }

        @Test
        void validationFailedIsTerminal() {
            assertTrue(VALIDATION_FAILED.isTerminal());
            assertTrue(CsrStatusTransition.validTargets(VALIDATION_FAILED).isEmpty());
        }

        @ParameterizedTest
        @EnumSource(value = CsrStatus.class, names = {"SUBMITTED", "IN_REVIEW", "REVIEWED",
                "APPROVED", "REJECTED", "RETURNED", "FAILED", "RECEIVED", "VALIDATED"})
        void nonTerminalStatesAreNotTerminal(CsrStatus status) {
            assertFalse(status.isTerminal());
        }
    }

    // =========================================================================
    // VALID TARGETS — correctness of target sets
    // =========================================================================

    @Nested
    @DisplayName("Valid target sets")
    class ValidTargetSets {

        @Test
        void receivedTargets() {
            assertEquals(Set.of(VALIDATED, VALIDATION_FAILED),
                    CsrStatusTransition.validTargets(RECEIVED));
        }

        @Test
        void submittedTargets() {
            assertEquals(Set.of(IN_REVIEW),
                    CsrStatusTransition.validTargets(SUBMITTED));
        }

        @Test
        void inReviewTargets() {
            assertEquals(Set.of(APPROVED, REJECTED, REVIEWED, RETURNED),
                    CsrStatusTransition.validTargets(IN_REVIEW));
        }

        @Test
        void reviewedTargets() {
            assertEquals(Set.of(APPROVED, REJECTED),
                    CsrStatusTransition.validTargets(REVIEWED));
        }

        @Test
        void returnedTargets() {
            assertEquals(Set.of(IN_REVIEW, CLOSED),
                    CsrStatusTransition.validTargets(RETURNED));
        }

        @Test
        void rejectedTargets() {
            assertEquals(Set.of(IN_REVIEW, CLOSED),
                    CsrStatusTransition.validTargets(REJECTED));
        }

        @Test
        void approvedTargets() {
            assertEquals(Set.of(ISSUED, FAILED),
                    CsrStatusTransition.validTargets(APPROVED));
        }

        @Test
        void failedTargets() {
            assertEquals(Set.of(APPROVED),
                    CsrStatusTransition.validTargets(FAILED));
        }
    }

    // =========================================================================
    // POOL & ADMIN ACTION STATES
    // =========================================================================

    @Nested
    @DisplayName("State classification")
    class StateClassification {

        @Test
        void poolStatesContainsOnlySubmitted() {
            assertEquals(Set.of(SUBMITTED), CsrStatus.POOL_STATES);
        }

        @Test
        void adminActionStates() {
            assertEquals(Set.of(REJECTED, RETURNED, FAILED), CsrStatus.ADMIN_ACTION_STATES);
        }

        @Test
        void terminalStates() {
            assertEquals(Set.of(CLOSED, ISSUED, VALIDATION_FAILED), CsrStatus.TERMINAL_STATES);
        }
    }

    // =========================================================================
    // ENUM COMPLETENESS
    // =========================================================================

    @Test
    @DisplayName("All 12 statuses defined")
    void allStatusesDefined() {
        assertEquals(12, CsrStatus.values().length);
    }

    @Test
    @DisplayName("Every non-terminal status has at least one valid target")
    void everyNonTerminalHasTarget() {
        for (CsrStatus status : CsrStatus.values()) {
            if (!status.isTerminal()) {
                assertFalse(CsrStatusTransition.validTargets(status).isEmpty(),
                        status + " is non-terminal but has no valid targets");
            }
        }
    }

    @Test
    @DisplayName("Every status has a description")
    void everyStatusHasDescription() {
        for (CsrStatus status : CsrStatus.values()) {
            assertNotNull(status.description());
            assertFalse(status.description().isBlank());
        }
    }
}
