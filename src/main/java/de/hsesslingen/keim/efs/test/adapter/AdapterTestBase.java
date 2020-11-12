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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.hsesslingen.keim.efs.middleware.model.Booking;
import de.hsesslingen.keim.efs.middleware.model.BookingState;
import de.hsesslingen.keim.efs.middleware.model.Customer;
import de.hsesslingen.keim.efs.middleware.model.NewBooking;
import de.hsesslingen.keim.efs.middleware.model.Leg;
import de.hsesslingen.keim.efs.middleware.model.Option;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import static org.springframework.test.web.servlet.ResultMatcher.matchAll;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 *
 * @author ben
 */
public abstract class AdapterTestBase {

    private static Logger logger = LoggerFactory.getLogger(AdapterTestBase.class);
    private static final String CREDENTIALS_HEADER_KEY = "x-credentials";

    @Autowired
    protected ObjectMapper mapper;
    // The following ObjectMapper might be useful for some debugging cases.
    // protected ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    @Autowired
    protected MockMvc mockMvc;

    protected static <T> void assertEqualsIfNotNull(String text, T expected, T actual) {
        if (actual != null) {
            assertEquals(expected, actual, text);
        }
    }

    protected String stringify(Object o) throws JsonProcessingException {
        return mapper.writeValueAsString(o);
    }

    protected <T> T parse(MvcResult mvcResult, Class<T> clazz) throws IOException {
        String response = mvcResult.getResponse().getContentAsString();

        try {
            return mapper.readValue(response, clazz);
        } catch (JsonProcessingException ex) {
            logger.error("{}", ex);
            throw ex;
        }
    }

    protected <T> T parse(MvcResult mvcResult, TypeReference<T> typeRef) throws IOException {
        return mapper.readValue(mvcResult.getResponse().getContentAsString(), typeRef);
    }

    protected MvcResult request(MockHttpServletRequestBuilder builder, ResultMatcher... matchers) throws Exception {
        try {
            return mockMvc.perform(builder)
                    .andExpect(matchAll(matchers))
                    .andReturn();
        } catch (Exception ex) {
            logger.error("{}", ex);
            throw ex;
        }
    }

    protected <T> T request(MockHttpServletRequestBuilder builder, Class<T> expectedReturnType, ResultMatcher... matchers) throws Exception {
        MvcResult result = request(builder, matchers);
        return parse(result, expectedReturnType);
    }

    protected <T> T request(MockHttpServletRequestBuilder builder, TypeReference<T> expectedReturnType, ResultMatcher... matchers) throws Exception {
        MvcResult result = request(builder, matchers);
        return parse(result, expectedReturnType);
    }

    protected <T> T requestCheck2xx(MockHttpServletRequestBuilder builder, Class<T> expectedReturnType) throws Exception {
        return request(builder, expectedReturnType, status().is2xxSuccessful());
    }

    protected <T> T requestCheck2xx(MockHttpServletRequestBuilder builder, TypeReference<T> expected) throws Exception {
        return request(builder, expected, status().is2xxSuccessful());
    }

    /**
     * Creates a NewBooking object from the given options. The returned object
     * is valid and should be ok to submit to the createBooking function.
     *
     * @param option
     * @param customer
     * @return
     */
    protected NewBooking optionsToNewBooking(Option option, Customer customer) {
        var optLeg = option.getLeg();

        var optStartTime = optLeg.getStartTime();
        var endTime = optLeg.getEndTime();
        var now = Instant.now();

        var startTime = optStartTime.isAfter(now) ? optStartTime : now.plusSeconds(5);

        if (endTime != null && endTime.isBefore(startTime)) {
            // If the original end time is now before the start time, adjust it to be equally distant to startTime than in the option.
            var originalDiff = optStartTime.until(endTime, ChronoUnit.MILLIS);
            endTime = startTime.plusMillis(originalDiff);
        }

        var legMode = optLeg.getMode() != null
                ? optLeg.getMode()
                : optLeg.getAsset() != null
                ? optLeg.getAsset().getMode()
                : null;

        var leg = new Leg()
                .setMode(legMode)
                .setFrom(optLeg.getFrom())
                .setTo(optLeg.getTo())
                .setStartTime(startTime)
                .setEndTime(endTime);

        return new NewBooking(leg, customer);
    }

    private void addCredentialsToRequestBuilder(MockHttpServletRequestBuilder builder, String credentials) {
        if (credentials != null) {
            builder.header(CREDENTIALS_HEADER_KEY, credentials);
        }
    }

    protected List<Option> getOptions(
            String fromLatLon, String toLatLon,
            Instant startTime, Instant endTime,
            Integer radius, Boolean sharing,
            String credentials, ResultMatcher... matchers
    ) throws Exception {
        var sb = new StringBuilder("/api/bookings/options?");

        sb.append("from=").append(fromLatLon);

        if (toLatLon != null) {
            sb.append("&to=").append(toLatLon);
        }

        if (startTime != null) {
            sb.append("&startTime=").append(startTime.toEpochMilli());
        }

        if (endTime != null) {
            sb.append("&endTime=").append(endTime.toEpochMilli());
        }

        if (radius != null) {
            sb.append("&radius=").append(radius);
        }

        if (sharing != null) {
            sb.append("&sharing=").append(sharing);
        }

        MockHttpServletRequestBuilder builder = get(sb.toString());

        addCredentialsToRequestBuilder(builder, credentials);

        List<Option> options = request(builder, new TypeReference<List<Option>>() {
        }, matchers);

        return options;
    }

    protected Booking createBooking(Option option, Customer customer, String credentials, ResultMatcher... matchers) throws Exception {
        return createBooking(optionsToNewBooking(option, customer), credentials, matchers);
    }

    protected Booking createBooking(NewBooking newBooking, String credentials, ResultMatcher... matchers) throws Exception {
        String json = stringify(newBooking);

        MockHttpServletRequestBuilder builder = post("/api/bookings")
                .content(json)
                .header("Content-Type", "application/json");

        addCredentialsToRequestBuilder(builder, credentials);

        // By sending "newBooking" to the API, we receive a Booking object.
        Booking booking = request(builder, Booking.class, matchers);

        return booking;
    }

    protected Booking modifyBooking(Booking booking, String credentials, ResultMatcher... matchers) throws Exception {
        MockHttpServletRequestBuilder builder = put("/api/bookings/" + booking.getId())
                .content(stringify(booking))
                .header("Content-Type", "application/json");

        addCredentialsToRequestBuilder(builder, credentials);

        // By sending "newBooking" to the API, we receive a Booking object.
        return request(builder, Booking.class, matchers);
    }

    protected List<Booking> getBookings(BookingState byState, String credentials, ResultMatcher... matchers) throws Exception {
        String byStateParam = byState != null ? "?state=" + byState : "";

        MockHttpServletRequestBuilder builder = get("/api/bookings" + byStateParam)
                .header("Content-Type", "application/json");

        addCredentialsToRequestBuilder(builder, credentials);

        // By sending "newBooking" to the API, we receive a Booking object.
        return request(builder, new TypeReference<List<Booking>>() {
        }, matchers);
    }

    protected Booking getBookingById(String bookingId, String credentials, ResultMatcher... matchers) throws Exception {
        MockHttpServletRequestBuilder builder = get("/api/bookings/" + bookingId)
                .header("Content-Type", "application/json");

        addCredentialsToRequestBuilder(builder, credentials);

        // By sending "newBooking" to the API, we receive a Booking object.
        return request(builder, Booking.class, matchers);
    }

}
