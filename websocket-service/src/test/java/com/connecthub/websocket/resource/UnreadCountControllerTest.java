package com.connecthub.websocket.resource;

import com.connecthub.websocket.service.UnreadCountService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnreadCountControllerTest {

    @Mock private UnreadCountService unreadCountService;

    @InjectMocks
    private UnreadCountController controller;

    @Test
    void getUnreadCounts_sameUserReturnsCounts() {
        Map<String, Long> counts = Map.of("room-1", 3L);
        when(unreadCountService.getAllForUser(5)).thenReturn(counts);

        ResponseEntity<Map<String, Long>> response = controller.getUnreadCounts(5, 5, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(counts, response.getBody());
        verify(unreadCountService).getAllForUser(5);
    }

    @Test
    void getUnreadCounts_adminMayReadAnotherUser() {
        Map<String, Long> counts = Map.of("room-2", 1L);
        when(unreadCountService.getAllForUser(5)).thenReturn(counts);

        ResponseEntity<Map<String, Long>> response = controller.getUnreadCounts(5, 9, "ADMIN");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(counts, response.getBody());
    }

    @Test
    void getUnreadCounts_nonOwnerNonAdminForbidden() {
        ResponseEntity<Map<String, Long>> response = controller.getUnreadCounts(5, 9, "USER");

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verifyNoInteractions(unreadCountService);
    }
}
