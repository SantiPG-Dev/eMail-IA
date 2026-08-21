package com.emailai.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

// ReadyFileWriter: escribe port+pid al inicializarse Tomcat y borra en shutdown.
class ReadyFileWriterTest {

    @TempDir
    Path tmp;

    @Test
    void escribePuertoYPidYBorraEnShutdown() throws Exception {
        Path ready = tmp.resolve("backend.ready");
        MockEnvironment env = new MockEnvironment()
                .withProperty("emailai.ready-file", ready.toString());
        ReadyFileWriter writer = new ReadyFileWriter(env);

        writer.writeReady(8123);

        assertThat(Files.exists(ready)).isTrue();
        String contenido = Files.readString(ready);
        assertThat(contenido).contains("\"port\":8123");
        assertThat(contenido).containsPattern("\"pid\":\\d+");

        writer.deleteReadyFile();
        assertThat(Files.exists(ready)).isFalse();
    }

    @Test
    void sinPropiedadNoEscribeNada() {
        MockEnvironment env = new MockEnvironment();
        ReadyFileWriter writer = new ReadyFileWriter(env);

        // No debe lanzar: simplemente no hay ready file configurado
        writer.writeReady(9999);
        writer.deleteReadyFile();
        assertThat(tmp).isEmptyDirectory();
    }

    @Test
    void reescrituraActualizaElPuerto() throws Exception {
        // Un reinicio de Tomcat en la misma JVM debe actualizar el puerto
        Path ready = tmp.resolve("backend.ready");
        MockEnvironment env = new MockEnvironment()
                .withProperty("emailai.ready-file", ready.toString());
        ReadyFileWriter writer = new ReadyFileWriter(env);

        writer.writeReady(8123);
        writer.writeReady(9990);

        assertThat(Files.readString(ready)).contains("\"port\":9990");
        assertThat(Files.readString(ready)).doesNotContain("\"port\":8123");
    }
}
