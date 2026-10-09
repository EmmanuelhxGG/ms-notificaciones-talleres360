package com.talleres360.notificaciones.service;

import com.talleres360.notificaciones.model.Notificacion;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.springframework.stereotype.Service;

/** Comprobante de servicio: no emite ni reemplaza documentos tributarios del SII. */
@Service
public class GeneradorComprobante {
  public byte[] generar(Notificacion notificacion) {
    try (var documento = new PDDocument();
        var salida = new ByteArrayOutputStream()) {
      var lineas = new java.util.ArrayList<String>();
      String texto = "Talleres360 - Comprobante de atención\n" + notificacion.getMensaje();
      for (String linea : texto.split("\\R")) {
        linea = linea.replaceAll("[^\\x20-\\x7E\\xA0-\\xFF]", "?");
        for (int inicio = 0; inicio < Math.max(1, linea.length()); inicio += 85)
          lineas.add(linea.substring(inicio, Math.min(linea.length(), inicio + 85)));
      }
      for (int comienzo = 0; comienzo < lineas.size(); comienzo += 42) {
        PDPage pagina = new PDPage();
        documento.addPage(pagina);
        try (var contenido = new PDPageContentStream(documento, pagina)) {
          contenido.beginText();
          contenido.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
          contenido.setLeading(16);
          contenido.newLineAtOffset(45, 740);
          for (int indice = comienzo; indice < Math.min(lineas.size(), comienzo + 42); indice++) {
            contenido.showText(lineas.get(indice));
            contenido.newLine();
          }
          contenido.endText();
        }
      }
      documento.save(salida);
      return salida.toByteArray();
    } catch (IOException error) {
      throw new IllegalStateException("No se pudo generar el comprobante", error);
    }
  }
}
