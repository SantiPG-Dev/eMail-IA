import DOMPurify from "dompurify";
import { mensajeApi } from "../api/client";
import { mensajeDeError } from "./error";

export interface Adjunto {
	id: number;
	nombre: string;
	mimeType?: string;
	tamanoBytes?: number;
}

export interface Mensaje {
	id: number;
	uid: string;
	remitente: string;
	asunto: string;
	cuerpo: string;
	html: string;
	categoria: string;
	prioridad: string;
	fechaRecepcion: string;
	destinatarios?: string;
	adjuntos?: Adjunto[];
}

// Política anti-tracking: SOLO los correos LEGITIMOS cargan imágenes remotas.
// SPAM / PHISHING / DESCONOCIDO las bloquean (los <img> remotos son web beacons que
// confirman al remitente que abriste el correo). Al reclasificar (ej. marcar SPAM),
// cambia selected.categoria y el iframe se re-renderiza sin imágenes -> desaparecen.
// DOMPurify sanitiza ANTES de inyectar (capa extra sobre el sandbox del iframe):
// elimina <script>, handlers on* y javascript: aunque el atributo sandbox falle.
// Todos los enlaces se reescriben a target=_blank: el sandbox del iframe (sin
// allow-top-navigation) bloquearía la navegación interna, y así el click llega al
// setWindowOpenHandler de Electron -> shell.openExternal (navegador del sistema).
export function htmlSegunCategoria(html: string, categoria?: string): string {
	if (!html) return "";
	const sane = DOMPurify.sanitize(html, {
		WHOLE_DOCUMENT: true, // conserva <head> (estilos del correo)
		ADD_TAGS: ["link"], // hojas de estilo remotas del correo
		ADD_ATTR: ["target"],
	});
	let doc: Document;
	try {
		doc = new DOMParser().parseFromString(sane, "text/html");
	} catch {
		return sane;
	}
	doc.querySelectorAll("a[href]").forEach((a) => {
		a.setAttribute("target", "_blank");
		a.setAttribute("rel", "noreferrer noopener");
	});
	if (categoria === "LEGITIMO") {
		// los legítimos cargan todo
		return "<!DOCTYPE html>\n" + doc.documentElement.outerHTML;
	}
	// No legítimo: CSP img-src 'none' bloquea TODAS las imágenes (http, data URIs,
	// CSS background-image) a nivel navegador. Más robusto que quitar <img src> a mano,
	// que se dejaba las data: y los background-image (por eso seguían viéndose).
	const meta = doc.createElement("meta");
	meta.setAttribute("http-equiv", "Content-Security-Policy");
	meta.setAttribute("content", "img-src 'none';");
	doc.head.prepend(meta);
	return "<!DOCTYPE html>\n" + doc.documentElement.outerHTML;
}

// El remitente llega como 'Nombre <email@dom>' (a veces varios separados
// por coma); para el campo «Para» solo valen las direcciones, sin nombre
// ni lo que va entre <>. Si no hay <>, se usa la cadena tal cual.
export function emailDeRemitente(remitente: string): string {
	const entre = remitente.match(/<[^>]+>/g);
	return entre ? entre.map((e) => e.slice(1, -1).trim()).join(", ") : remitente.trim();
}

export function formatBytes(n?: number): string {
	if (!n || n <= 0) return "";
	if (n < 1024) return n + " B";
	if (n < 1024 * 1024) return (n / 1024).toFixed(1) + " KB";
	return (n / (1024 * 1024)).toFixed(1) + " MB";
}

// Descarga un adjunto vía API (con el JWT del interceptor) y lo guarda en disco.
export async function descargarAdjunto(
	mensajeId: number,
	adj: Adjunto,
): Promise<void> {
	try {
		const res = await mensajeApi.descargarAdjunto(mensajeId, adj.id);
		const blob = new Blob([res.data]);
		const url = URL.createObjectURL(blob);
		const a = document.createElement("a");
		a.href = url;
		a.download = adj.nombre || "adjunto";
		document.body.appendChild(a);
		a.click();
		a.remove();
		URL.revokeObjectURL(url);
	} catch (err) {
		alert(
			"No se pudo descargar el adjunto: " +
				mensajeDeError(err, "error"),
		);
	}
}

// Texto plano a partir del HTML: MimeParser deja cuerpo vacío cuando el mail
// no trae parte text/plain (solo HTML), y reenviar ese correo salía sin
// contenido. Se usa el DOM ya disponible en el renderer: sin librerías.
// Los <br> y bloques (p/div/tr/...) se vuelven saltos de línea para que el
// texto reenviado no sea un muro de palabras pegadas.
export function htmlATexto(html: string): string {
	if (!html) return "";
	try {
		const doc = new DOMParser().parseFromString(html, "text/html");
		doc.querySelectorAll("style, script").forEach((el) => el.remove());
		doc.body.querySelectorAll("br").forEach((br) => br.replaceWith("\n"));
		doc.body
			.querySelectorAll("p, div, tr, li, h1, h2, h3, blockquote")
			.forEach((b) => b.append("\n"));
		return (doc.body.textContent || "").replace(/\n{3,}/g, "\n\n").trim();
	} catch {
		return "";
	}
}

// Prefija Re:/RV: al asunto sin apilar («Re: Re: Re: …»): se quitan los
// prefijos previos (de respuesta o reenvío, en mayúsculas o minúsculas) y se
// pone exactamente uno.
export function asuntoConPrefijo(asunto: string, prefijo: "Re:" | "RV:"): string {
	const limpio = asunto.replace(/^(?:(?:re|rv):\s*)+/i, "");
	return `${prefijo} ${limpio}`.trim();
}
