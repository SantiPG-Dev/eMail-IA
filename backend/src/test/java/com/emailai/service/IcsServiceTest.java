package com.emailai.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class IcsServiceTest {

    @Autowired
    private IcsService icsService;

    @Test
    void importarIcs() throws IOException {
        // Crear un archivo ICS temporal
        String icsContent = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Test//Test//EN
            BEGIN:VEVENT
            DTSTART:20261225T100000
            DTEND:20261225T110000
            SUMMARY:Nochebuena
            DESCRIPTION:Cena familiar
            END:VEVENT
            END:VCALENDAR
            """;

        Path tempFile = Files.createTempFile("evento-", ".ics");
        Files.writeString(tempFile, icsContent);

        try {
            var eventos = icsService.procesarArchivoIcs(tempFile.toString());
            assertFalse(eventos.isEmpty());
            assertEquals(1, eventos.size());
            assertEquals("Nochebuena", eventos.get(0).titulo());
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void rutaInvalida() {
        assertThrows(SecurityException.class,
            () -> icsService.procesarArchivoIcs("/ruta/inexistente/evento.ics"));
    }
}
