package com.ad_glasses.ai.grounding

import com.google.transit.realtime.GtfsRealtime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransportApiClientsTest {
    @Test
    fun railLiveStatusExtractsCurrentStationAndDelay() {
        val result = TransportApiParsers.parseRailLiveStatus(
            """{
              "status": true,
              "data": {
                "train_number": "12951",
                "train_name": "Mumbai Rajdhani",
                "current_station_name": "Vadodara Jn",
                "next_station_name": "Surat",
                "delay": "18 min",
                "status_as_of": "22:10"
              }
            }""".trimIndent(),
            "12951",
        )

        assertTrue(result.answer.contains("Vadodara Jn"))
        assertTrue(result.answer.contains("18 min"))
        assertTrue(result.context.contains("12951"))
    }

    @Test
    fun pnrStatusExtractsPassengerStateWithoutInventingIt() {
        val result = TransportApiParsers.parsePnrStatus(
            """{
              "status": true,
              "data": {
                "pnrNumber": "1234567890",
                "trainNumber": "12951",
                "boardingPoint": "NDLS",
                "reservationUpto": "MMCT",
                "passengerList": [
                  {"passengerNumber": 1, "bookingStatus": "RAC 4", "currentStatus": "CNF"},
                  {"passengerNumber": 2, "bookingStatus": "WL 2", "currentStatus": "RAC 1"}
                ]
              }
            }""".trimIndent(),
            "1234567890",
        )

        assertTrue(result.answer.contains("passenger 1 CNF"))
        assertTrue(result.answer.contains("passenger 2 RAC 1"))
        assertFalse(result.answer.contains("passenger 2 CNF"))
    }

    @Test
    fun aviationStatusIncludesGateTerminalAndDelay() {
        val result = TransportApiParsers.parseFlightStatus(
            """{
              "data": [{
                "flight_status": "active",
                "airline": {"name": "Example Air"},
                "flight": {"iata": "AI101"},
                "departure": {
                  "airport": "Delhi",
                  "terminal": "3",
                  "gate": "12",
                  "delay": 20,
                  "estimated": "2026-08-28T01:20:00+05:30"
                },
                "arrival": {
                  "airport": "Mumbai",
                  "terminal": "2",
                  "gate": "A4"
                }
              }]
            }""".trimIndent(),
            "AI101",
        )

        assertTrue(result.answer.contains("terminal 3"))
        assertTrue(result.answer.contains("gate 12"))
        assertTrue(result.answer.contains("delay 20 min"))
        assertTrue(result.answer.contains("Mumbai"))
    }

    @Test
    fun gtfsTripUpdateProducesArrivalPrediction() {
        val now = 1_800_000_000L
        val stopEvent = GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder()
            .setTime(now + 600)
            .setDelay(180)
            .build()
        val stopUpdate = GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder()
            .setStopId("STOP-1")
            .setArrival(stopEvent)
            .build()
        val trip = GtfsRealtime.TripDescriptor.newBuilder()
            .setTripId("TRIP-1")
            .setRouteId("R1")
            .build()
        val update = GtfsRealtime.TripUpdate.newBuilder()
            .setTrip(trip)
            .addStopTimeUpdate(stopUpdate)
            .build()
        val message = GtfsRealtime.FeedMessage.newBuilder()
            .setHeader(
                GtfsRealtime.FeedHeader.newBuilder()
                    .setGtfsRealtimeVersion("2.0")
                    .build(),
            )
            .addEntity(
                GtfsRealtime.FeedEntity.newBuilder()
                    .setId("entity-1")
                    .setTripUpdate(update)
                    .build(),
            )
            .build()

        val result = TransitRealtimeClient().parseStatus(
            feed = GtfsRealtimeFeedConfig("demo", "Demo Transit", "https://transit.example.com/gtfs.pb?token=secret"),
            message = message,
            stopId = "STOP-1",
            routeId = "R1",
            nowEpochSeconds = now,
        )

        assertTrue(result.answer.contains("about 10 min"))
        assertTrue(result.answer.contains("3 min late"))
        assertTrue(result.context.contains("delaySeconds=180"))
        assertFalse(result.sources.single().url.contains("token="))
    }

    @Test
    fun missingGtfsUpdateDoesNotClaimOnTimeService() {
        val message = GtfsRealtime.FeedMessage.newBuilder()
            .setHeader(
                GtfsRealtime.FeedHeader.newBuilder()
                    .setGtfsRealtimeVersion("2.0")
                    .build(),
            )
            .build()
        val result = TransitRealtimeClient().parseStatus(
            feed = GtfsRealtimeFeedConfig("demo", "Demo Transit", "https://transit.example.com/gtfs.pb"),
            message = message,
            stopId = "STOP-1",
            routeId = null,
            nowEpochSeconds = 1_800_000_000L,
        )

        assertTrue(result.answer.contains("does not mean service is on time"))
    }
}
