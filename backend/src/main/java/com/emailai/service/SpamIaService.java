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
import java.util.Set;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import weka.classifiers.Classifier;
import weka.classifiers.bayes.NaiveBayesMultinomial;
import weka.classifiers.meta.FilteredClassifier;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;
import weka.filters.unsupervised.attribute.StringToWordVector;

import com.emailai.domain.entities.Mensaje;

// Clasificador Naive Bayes con bolsa de palabras (TF-IDF).
// Modelos por cuenta guardados en DB/ia/modelo_{cuentaHash}.model
// Clasifica en: LEGITIMO, SPAM, PHISHING.
@Service
public class SpamIaService {

    private static final Logger log = LoggerFactory.getLogger(SpamIaService.class);

    private final Path modelosDir;
    private final Object modelLock = new Object();

    private final ArrayList<Attribute> atributos;
    private final Attribute attrTexto;
    private final Attribute attrClase;

    public enum ClaseCorreo {
        LEGITIMO, SPAM, PHISHING
    }

    // Clases válidas para el atributo nominal. Si intentamos setValue con una
    // etiqueta que no esté aquí, Weka lanza IllegalArgumentException
    // ("Value not defined for nominal attribute") y se fastidia el reentrenamiento.
    public static final Set<String> CLASES_VALIDAS =
            Set.of("LEGITIMO", "SPAM", "PHISHING");

    public static boolean esClaseValida(String c) {
        return c != null && CLASES_VALIDAS.contains(c.toUpperCase());
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
        // "@@class@@" no puede colisionar con ninguna palabra real del texto.
        // StringToWordVector monta un atributo por palabra, y si un correo trae
        // "clase" chocaba con el atributo class -> "Attribute names are not unique".
        attrClase = new Attribute("@@class@@", clases);
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
        // Multinomial NB + TF va mucho mejor que el gaussiano para texto: el gaussiano
        // asume distribución normal sobre los conteos y con correos reales (HTML,
        // firmas, stopwords) tiende a meterlo todo en la clase mayoritaria.
        // Multinomial modeliza la frecuencia de términos por clase.
        filter.setTFTransform(true);
        Classifier base = new NaiveBayesMultinomial();
        FilteredClassifier fc = new FilteredClassifier();
        fc.setFilter(filter);
        fc.setClassifier(base);
        return fc;
    }

    private Path modeloPath(String cuentaHash) {
        // cuentaHash es en la práctica el email en claro: sanitizar para que
        // no pueda escapar de modelosDir (path traversal via "../" en el email)
        String seguro = cuentaHash == null ? "" : cuentaHash.replaceAll("[^A-Za-z0-9@._-]", "_");
        return modelosDir.resolve("modelo_" + seguro + ".model");
    }

    /** ¿Existe modelo entrenado para esta cuenta? */
    public boolean modeloExiste(String cuentaHash) {
        return Files.exists(modeloPath(cuentaHash));
    }

    // Entrena o reentrena el modelo Weka de una cuenta
    public void entrenarModelo(String cuentaHash, List<Mensaje> ejemplos) throws Exception {
        if (cuentaHash == null || ejemplos == null || ejemplos.isEmpty()) return;

        Instances data = crearDatasetVacio("correos_" + cuentaHash);

        for (Mensaje m : ejemplos) {
            if (m.getCategoria() == null) continue;
            // Fuera del enum nominal haría explotar buildClassifier. Mejor saltarse
            // la fila que abortar todo el reentrenamiento (puede venir de un
            // ?categoria=foo histórico en el controller).
            if (!CLASES_VALIDAS.contains(m.getCategoria().toUpperCase())) continue;
            String texto = (m.getCuerpo() != null ? m.getCuerpo() : "") +
                           " " + (m.getAsunto() != null ? m.getAsunto() : "");
            DenseInstance inst = new DenseInstance(2);
            inst.setValue(attrTexto, texto);
            inst.setValue(attrClase, m.getCategoria().toUpperCase());
            data.add(inst);
        }

        if (data.isEmpty()) return;

        // Log del histograma para ver cómo queda el reparto de clases; si está
        // muy desbalanceado el modelo tiende a clasificar todo como LEGITIMO.
        int[] hist = new int[attrClase.numValues()];
        for (int i = 0; i < data.numInstances(); i++) hist[(int) data.instance(i).classValue()]++;
        log.info("Entrenando modelo Weka cuenta {}: {} instancias, histograma LEGITIMO/SPAM/PHISHING={}",
                cuentaHash, data.numInstances(), java.util.Arrays.toString(hist));

        FilteredClassifier fc = crearClasificadorBase();
        fc.buildClassifier(data);

        synchronized (modelLock) {
            try (ObjectOutputStream oos = new ObjectOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(modeloPath(cuentaHash))))) {
                oos.writeObject(fc);
            }
        }
    }

    // Clasifica un mensaje con el modelo guardado (si existe)
    // Si no hay modelo entrenado, devuelve LEGITIMO por defecto
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

    // Elimina el modelo de una cuenta (ej: al borrar la cuenta)
    public void borrarModelo(String cuentaHash) throws IOException {
        synchronized (modelLock) {
            Path p = modeloPath(cuentaHash);
            if (Files.exists(p)) Files.delete(p);
        }
    }
}
