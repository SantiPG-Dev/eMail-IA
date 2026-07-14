package com.emailai.service;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import weka.classifiers.Classifier;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.meta.FilteredClassifier;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;
import weka.filters.unsupervised.attribute.StringToWordVector;

import com.emailai.domain.entities.Mensaje;

/**
 * Clasificador spam/phishing basado en Naive Bayes con bolsa de palabras.
 *
 * <p>Usa StringToWordVector para convertir el texto del correo en un vector
 * TF-IDF y NaiveBayes para clasificar como LEGITIMO/SPAM/PHISHING.
 * Modelos por cuenta guardados en disco ({@code modelosDir/modelo_{cuentaHash}.model}).
 */
@Service
public class SpamIaService {

    private final Path modelosDir;
    private final Object modelLock = new Object();

    private final ArrayList<Attribute> atributos;
    private final Attribute attrTexto;
    private final Attribute attrClase;

    public enum ClaseCorreo {
        LEGITIMO, SPAM, PHISHING
    }

    public SpamIaService() throws IOException {
        this.modelosDir = Path.of("DB", "ia");
        if (!Files.exists(modelosDir)) {
            Files.createDirectories(modelosDir);
        }

        atributos = new ArrayList<>();
        attrTexto = new Attribute("texto", (List<String>) null);
        atributos.add(attrTexto);

        ArrayList<String> clases = new ArrayList<>();
        clases.add("LEGITIMO");
        clases.add("SPAM");
        clases.add("PHISHING");
        attrClase = new Attribute("clase", clases);
        atributos.add(attrClase);
    }

    private Instances crearDatasetVacio(String nombre) {
        Instances data = new Instances(nombre, atributos, 0);
        data.setClass(attrClase);
        return data;
    }

    private FilteredClassifier crearClasificadorBase() {
        StringToWordVector filter = new StringToWordVector();
        filter.setAttributeIndices("first");
        Classifier base = new NaiveBayes();
        FilteredClassifier fc = new FilteredClassifier();
        fc.setFilter(filter);
        fc.setClassifier(base);
        return fc;
    }

    private Path modeloPath(String cuentaHash) {
        return modelosDir.resolve("modelo_" + cuentaHash + ".model");
    }

    /**
     * Entrena o reentrena el modelo de una cuenta con mensajes etiquetados.
     */
    public void entrenarModelo(String cuentaHash, List<Mensaje> ejemplos) throws Exception {
        if (cuentaHash == null || ejemplos == null || ejemplos.isEmpty()) return;

        Instances data = crearDatasetVacio("correos_" + cuentaHash);

        for (Mensaje m : ejemplos) {
            if (m.getCategoria() == null) continue;
            String texto = (m.getCuerpo() != null ? m.getCuerpo() : "") +
                           " " + (m.getAsunto() != null ? m.getAsunto() : "");
            DenseInstance inst = new DenseInstance(2);
            inst.setValue(attrTexto, texto);
            inst.setValue(attrClase, m.getCategoria().toUpperCase());
            data.add(inst);
        }

        if (data.isEmpty()) return;

        FilteredClassifier fc = crearClasificadorBase();
        fc.buildClassifier(data);

        synchronized (modelLock) {
            try (ObjectOutputStream oos = new ObjectOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(modeloPath(cuentaHash))))) {
                oos.writeObject(fc);
            }
        }
    }

    /**
     * Clasifica un mensaje usando el modelo de su cuenta.
     */
    public ClaseCorreo clasificar(String cuentaHash, Mensaje mensaje) throws Exception {
        Path path = modeloPath(cuentaHash);
        if (!Files.exists(path)) return ClaseCorreo.LEGITIMO;

        FilteredClassifier fc;
        synchronized (modelLock) {
            try (ObjectInputStream ois = new ObjectInputStream(
                    new BufferedInputStream(Files.newInputStream(path)))) {
                ois.setObjectInputFilter(new ModelFilter());
                fc = (FilteredClassifier) ois.readObject();
            }
        }

        Instances data = crearDatasetVacio("test_" + cuentaHash);
        String texto = (mensaje.getCuerpo() != null ? mensaje.getCuerpo() : "") +
                       " " + (mensaje.getAsunto() != null ? mensaje.getAsunto() : "");
        DenseInstance inst = new DenseInstance(2);
        inst.setDataset(data);
        inst.setValue(attrTexto, texto);
        data.add(inst);

        double idx = fc.classifyInstance(inst);
        return ClaseCorreo.valueOf(data.classAttribute().value((int) idx));
    }

    /**
     * Elimina el modelo de una cuenta.
     */
    public void borrarModelo(String cuentaHash) throws IOException {
        synchronized (modelLock) {
            Path p = modeloPath(cuentaHash);
            if (Files.exists(p)) Files.delete(p);
        }
    }
}
