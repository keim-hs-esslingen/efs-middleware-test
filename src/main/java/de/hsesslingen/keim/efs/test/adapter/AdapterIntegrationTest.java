/*
 * MIT License
 * 
 * Copyright (c) 2020 Hochschule Esslingen
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE. 
 */
package de.hsesslingen.keim.efs.test.adapter;

import de.hsesslingen.keim.efs.middleware.model.Booking;
import de.hsesslingen.keim.efs.middleware.model.BookingState;
import de.hsesslingen.keim.efs.middleware.model.Customer;
import de.hsesslingen.keim.efs.middleware.model.NewBooking;
import de.hsesslingen.keim.efs.middleware.model.Option;
import static de.hsesslingen.keim.efs.test.adapter.AdapterTestBase.assertEqualsIfNotNull;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import javax.validation.constraints.NotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.springframework.test.web.servlet.ResultMatcher;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 *
 * @author boesch
 */
public abstract class AdapterIntegrationTest extends AdapterTestBase {

    private static List<Option> cachedOptions; // List is static so it is retained between tests.

    public static final void clearOptionsCache() {
        cachedOptions = null;
    }

    protected Random random = new Random();

    protected abstract String getOptionsCredentials();

    protected abstract String getBookingCredentials();

    protected abstract Customer getCustomer();

    protected abstract String getFromLatLon();

    protected abstract String getToLatLon();

    protected abstract Instant getStartTime();

    protected abstract Instant getEndTime();

    protected abstract Integer getRadius();

    protected abstract boolean getSharing();

    //<editor-fold defaultstate="collapsed" desc="Simplified BookingApi methods...">    
    protected List<Option> getOptions(
            String fromLatLon, String toLatLon,
            Instant startTime, Instant endTime,
            Integer radius, Boolean sharing,
            ResultMatcher... matchers
    ) throws Exception {
        return getOptions(fromLatLon, toLatLon, startTime, endTime, radius, sharing, getOptionsCredentials(), matchers);
    }

    /**
     * Calls {@link super.getOptions(...)} with values provided in abstract
     * methods.
     *
     * @return
     * @throws Exception
     */
    protected List<Option> getOptions() throws Exception {
        return getOptions(
                getFromLatLon(), getToLatLon(),
                getStartTime(), getEndTime(),
                getRadius(), getSharing(),
                getOptionsCredentials(),
                status().is2xxSuccessful());
    }

    protected Booking createBooking(Option option, ResultMatcher... matchers) throws Exception {
        return createBooking(option, getCustomer(), getBookingCredentials(), matchers);
    }

    protected Booking createBooking(NewBooking newBooking, ResultMatcher... matchers) throws Exception {
        return createBooking(newBooking, getBookingCredentials(), matchers);
    }

    protected Booking modifyBooking(Booking booking, ResultMatcher... matchers) throws Exception {
        return modifyBooking(booking, getBookingCredentials(), matchers);
    }

    protected List<Booking> getBookings(BookingState byState, ResultMatcher... matchers) throws Exception {
        return getBookings(byState, getBookingCredentials(), matchers);
    }

    protected Booking getBookingById(String bookingId, ResultMatcher... matchers) throws Exception {
        return getBookingById(bookingId, getBookingCredentials(), matchers);
    }
    //</editor-fold>

    /**
     * Calls {@link super.getCacheOptions(...)} with
     * {@code status().is2xxSuccessful()} matcher.
     *
     * @throws Exception
     */
    public void testGetOptions() throws Exception {
        getCacheOptions();
    }

    /**
     * Intended for being overridden and marked as test method.
     *
     * @throws Exception
     */
    public void book_tryIllegalStates_close_tryAllStates() throws Exception {
        Booking booking = bookRandomOption();

        Booking closed = null;

        if (booking.getState() == BookingState.BOOKED) {
            tryModifyBookingForFail(booking, BookingState.NEW);
            tryModifyBookingForFail(booking, BookingState.BOOKED);
            tryModifyBookingForFail(booking, BookingState.ABORTED);
            tryModifyBookingForFail(booking, BookingState.FINISHED);

            closed = tryModifyBookingForSuccess(booking, BookingState.CANCELLED);
        } else if (booking.getState() == BookingState.STARTED) {
            tryModifyBookingForFail(booking, BookingState.NEW);
            tryModifyBookingForFail(booking, BookingState.BOOKED);
            tryModifyBookingForFail(booking, BookingState.CANCELLED);
            tryModifyBookingForFail(booking, BookingState.STARTED);

            closed = tryModifyBookingForSuccess(booking, BookingState.FINISHED);
        }

        // Subsequent calls to modify booking with the returned booking object should fail.
        for (BookingState state : BookingState.values()) {
            // Mark this booking for whatever and try a modfiy call...
            tryModifyBookingForFail(closed, state);
        }
    }

    /**
     * Intended for being overridden and marked as test method.
     *
     * @throws Exception
     */
    public void book_start_tryIllegalStates_finish_tryAllStates() throws Exception {
        Booking booking = bookRandomOption();

        Booking started;

        // Only modify to STARTED if the booking did not start in that state.
        if (booking.getState().equals(BookingState.BOOKED)) {
            started = tryModifyBookingForSuccess(booking, BookingState.STARTED);

            // The following tests must only be run if the booking came with state BOOKED.
            // If it had come with state STARTED, the failing tests had already run in book_tryIllegalStates_close_tryAllStates().
            tryModifyBookingForFail(started, BookingState.NEW);
            tryModifyBookingForFail(started, BookingState.BOOKED);
            tryModifyBookingForFail(started, BookingState.CANCELLED);
            tryModifyBookingForFail(started, BookingState.STARTED);
        } else {
            started = booking;
        } // the function bookRandomOption(...) asserts that the returned booking has states of either BOOKED or STARTED.

        Booking finished = tryModifyBookingForSuccess(started, BookingState.FINISHED);

        // Subsequent calls to modify booking with the returned booking object should fail.
        for (BookingState state : BookingState.values()) {
            // Mark this booking for whatever and try a modfiy call...
            tryModifyBookingForFail(finished, state);
        }
    }

    /**
     * Intended for being overridden and marked as test method.
     *
     * @throws Exception
     */
    public void book_start_abort_tryAllStates() throws Exception {
        Booking booking = bookRandomOption();

        Booking started;

        if (booking.getState().equals(BookingState.BOOKED)) {
            started = tryModifyBookingForSuccess(booking, BookingState.STARTED);
        } else {
            started = booking;
        } // the function bookRandomOption(...) asserts that the returned booking has states of either BOOKED or STARTED so we don't need to check for other states.

        Booking aborted = tryModifyBookingForSuccess(started, BookingState.ABORTED);

        // Subsequent calls to modify booking with the returned booking object should fail.
        for (BookingState state : BookingState.values()) {
            // Mark this booking for whatever and try a modfiy call...
            tryModifyBookingForFail(aborted, state);
        }
    }

    /**
     * Intended for being overridden and marked as test method.
     *
     * @throws Exception
     */
    public void testUpdateBooking() throws Exception {

    }

    /**
     * Intended for being overridden and marked as test method.
     *
     * @throws Exception
     */
    public void testGetBookings() throws Exception {
        List<Booking> bookings = getBookings(null, getBookingCredentials(), status().is2xxSuccessful());
        assertNotNull(bookings);
        // Check that none of the stored bookings has a booking state of NEW.
        assertTrue(bookings.stream().noneMatch(b -> b.getState().equals(BookingState.NEW)));
        // Check that none of the stored bookings has a booking state of UPDATE_REQUESTED.
        assertTrue(bookings.stream().noneMatch(b -> b.getState().equals(BookingState.UPDATEREQUESTED)));

        if (!bookings.isEmpty()) {
            // Check for one of the present booking states, whether a state-filtered list contains all of the matching elements and no other ones.
            // Do this only for one otherwise the tests might take very long.
            if (containsBookingsWithState(bookings, BookingState.BOOKED)) {
                testBookingStateFilter(bookings, BookingState.BOOKED);
            } else if (containsBookingsWithState(bookings, BookingState.CANCELLED)) {
                testBookingStateFilter(bookings, BookingState.CANCELLED);
            } else if (containsBookingsWithState(bookings, BookingState.STARTED)) {
                testBookingStateFilter(bookings, BookingState.STARTED);
            } else if (containsBookingsWithState(bookings, BookingState.FINISHED)) {
                testBookingStateFilter(bookings, BookingState.FINISHED);
            } else if (containsBookingsWithState(bookings, BookingState.ABORTED)) {
                testBookingStateFilter(bookings, BookingState.ABORTED);
            }

            // Check if particular bookings can be retrieved using the getBookingById endpoint.
            Booking randomBooking = bookings.get(random.nextInt(bookings.size()));
            Booking result = getBookingById(randomBooking.getId(), getBookingCredentials(), status().is2xxSuccessful());
            assertEquals(randomBooking, result, "The two bookings should be equal.");
        }
    }

    /**
     * Calls {@link super.getOptions()} with the paramters provided in the
     * implemented abstract methods. and chaches the result list in a member
     * variable, which is returned upon subsequent calls of this method.If you
     * need fresh options once in between, call @{link getOptions()}.If you want
     * to clear the cache, call {@link #clearOptionsCache()}.
     *
     * @return
     * @throws Exception
     */
    protected List<Option> getCacheOptions() throws Exception {
        if (cachedOptions == null) {
            cachedOptions = getOptions(
                    getFromLatLon(), getToLatLon(),
                    getStartTime(), getEndTime(),
                    getRadius(), getSharing(),
                    getOptionsCredentials(),
                    status().is2xxSuccessful());
        }

        return cachedOptions;
    }

    protected Option getRandomCachedOption() throws Exception {
        List<Option> options = getCacheOptions();
        return options.get(random.nextInt(options.size()));
    }

    /**
     * Books a random option from the cached options.
     *
     * @return
     * @throws Exception
     */
    protected Booking bookRandomOption() throws Exception {
        Option option = getRandomCachedOption();

        assertNotNull(option);

        NewBooking newBooking = optionsToNewBooking(option, getCustomer());

        // Send creation request...
        Booking booking = createBooking(newBooking, getBookingCredentials(), status().is2xxSuccessful());

        assertNotNull(booking, "The newly created booking should not be null.");

        if (booking.getState() != BookingState.BOOKED && booking.getState() != BookingState.STARTED) {
            fail("The newly created booking should have a BookingState of either \"BOOKED\" or \"STARTED\" but the actual value was " + booking.getState() + ".");
        }

        return booking;
    }

    protected boolean containsBookingsWithState(List<Booking> bookings, BookingState filter) {
        return bookings.stream().anyMatch(b -> b.getState().equals(filter));
    }

    protected void testBookingStateFilter(List<Booking> bookings, BookingState state) throws Exception {
        List<Booking> filteredList = getBookings(state, getBookingCredentials(), status().is2xxSuccessful());
        assertTrue(bookings.containsAll(filteredList));
        assertTrue(filteredList.stream().allMatch(b -> b.getState().equals(state)));
    }

    /**
     * This method attempts to send an apropriate request, to close this
     * booking.This means, if the state of the given booking is BOOKED, a
     * CANCELLED modify request is sent.
     * <p>
     * If the state is RUNNING, a ABORTED request ist sent.
     * <p>
     * No assertions are tested in this code. It is intended to be used as clean
     * up for test functions.
     * <p>
     * Be aware though, that the provided object is being mutated in this
     * function. The state is changed due to the nature of the interface.
     *
     * @param booking
     * @throws java.lang.Exception
     */
    protected void tryCloseBooking(Booking booking) throws Exception {
        switch (booking.getState()) {
            case BOOKED:
                booking.setState(BookingState.CANCELLED);
                break;
            case STARTED:
                booking.setState(BookingState.ABORTED);
        }

        modifyBooking(booking, getBookingCredentials());
    }

    /**
     * Sets the given state in the provided booking and then tries to send a
     * modify request, which is expected to fail.
     *
     * @param booking
     * @param state
     * @throws Exception
     */
    protected void tryModifyBookingForFail(Booking booking, BookingState state) throws Exception {
        BookingState previousState = booking.getState();

        // Mark this booking with the given state...
        booking.setState(state);

        // Send modify request. This should fail.
        Booking result = modifyBooking(booking, getBookingCredentials(), status().is4xxClientError());
        assertEqualsIfNotNull("The state of the returned booking should not have changed after the erroneous call.", previousState, result.getState());

        // Undo changes...
        booking.setState(previousState);
    }

    /**
     * Sets the given state in the provided booking and then tries to send a
     * modify request, which is expected to succeed. Also does some after
     * checking.
     *
     * @param booking
     * @param state
     * @return
     * @throws Exception
     */
    protected @NotNull
    Booking tryModifyBookingForSuccess(Booking booking, BookingState state) throws Exception {
        BookingState previousState = booking.getState();

        // Mark this booking for...
        booking.setState(state);

        if (state == BookingState.FINISHED || state == BookingState.ABORTED) {
            // Set something for the "to" value. It can't be null if finishing or aborting.
            booking.getLeg().setTo(booking.getLeg().getFrom());
        }

        // Send modify request...
        Booking result = modifyBooking(booking, getBookingCredentials(), status().is2xxSuccessful());
        assertNotNull(result, "The returned booking should not be null.");

        if (state == BookingState.FINISHED || state == BookingState.ABORTED) {
            if (result.getState() != BookingState.FINISHED && result.getState() != BookingState.ABORTED) {
                fail("The returned booking should have a BookingState of either \"FINISHED\" or \"ABORTED\". The requested state was \"" + state + "\".");
            }
        } else {
            assertEquals(state, result.getState(), "The returned booking should have a BookingState of \"" + state + "\".");
        }

        // Undo changes...
        booking.setState(previousState);

        // Return result...
        return result;
    }

}
