package dk.billetsystem.service;

import dk.billetsystem.TestData;
import dk.billetsystem.model.Event;
import dk.billetsystem.repository.EventRepository;
import dk.billetsystem.repository.ParticipantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Kapacitet: må der bestilles (eller genåbnes) så mange pladser? */
@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    EventRepository eventRepository;
    @Mock
    ParticipantRepository participantRepository;

    EventService eventService;

    @BeforeEach
    void setUp() {
        eventService = new EventService(eventRepository, participantRepository);
    }

    private void eventWith(Integer maxParticipants, long seatsTaken) {
        Event event = TestData.event(1, maxParticipants);
        when(eventRepository.findByIdForUpdate(1)).thenReturn(Optional.of(event));
        if (maxParticipants != null) {
            when(participantRepository.countByEventAndStatus(eq(1), any())).thenReturn(seatsTaken);
        }
    }

    @Test
    void derErPlads() {
        eventWith(10, 8);
        assertDoesNotThrow(() -> eventService.reserveSeats(1, 2));
    }

    @Test
    void forFåPladserGiver400MedAntalLedige() {
        eventWith(10, 8);
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> eventService.reserveSeats(1, 3));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        assertEquals("Der er kun 2 pladser tilbage til Hobbymesse 2027", e.getReason());
    }

    @Test
    void udsolgtEvent() {
        eventWith(10, 10);
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> eventService.reserveSeats(1, 1));
        assertEquals("Hobbymesse 2027 er desværre udsolgt", e.getReason());
    }

    @Test
    void ingenGrænseBetyderAltidPlads() {
        eventWith(null, 0);
        assertDoesNotThrow(() -> eventService.reserveSeats(1, 500));
        verify(participantRepository, never()).countByEventAndStatus(any(), any());
    }

    @Test
    void eventetLåsesFørPladserneTælles() {
        eventWith(10, 0);
        eventService.reserveSeats(1, 1);
        // findByIdForUpdate = SELECT ... FOR UPDATE, så samtidige bestillinger venter på hinanden
        verify(eventRepository).findByIdForUpdate(1);
        verify(eventRepository, never()).findById(any());
    }
}
