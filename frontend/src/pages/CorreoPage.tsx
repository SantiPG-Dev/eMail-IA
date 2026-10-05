import { useState, useEffect, useCallback, useRef } from "react";
import { useSearchParams } from "react-router-dom";
import api, { mensajeApi, cuentaApi } from "../api/client";
import { useSync } from "../context/SyncContext";
import ComposePage from "./ComposePage";
import ContextMenu from "../components/ContextMenu";
import ListaMensajes from "../components/ListaMensajes";
import DetalleMensaje from "../components/DetalleMensaje";
import EventoDialog from "../components/EventoDialog";
import TareaDialog from "../components/TareaDialog";
import { detectarFechaHora } from "../utils/fechas";
import {
	emailDeRemitente,
	asuntoConPrefijo,
	htmlATexto,
	repartoRespuestaTodos,
	type Mensaje,
} from "../utils/correo";
import { mensajeDeError } from "../utils/error";

// Página principal de correo: orquesta estado y carga; la lista vive en
// ListaMensajes y el detalle en DetalleMensaje (split como MailService).
export default function CorreoPage() {
	const [mensajes, setMensajes] = useState<Mensaje[]>([]);
	const [loadingMensajes, setLoadingMensajes] = useState(true);
	const [errorMensajes, setErrorMensajes] = useState<string | null>(null);
	const { triggerSync, syncing, statusText, refreshKey } = useSync();
	const [selected, setSelected] = useState<Mensaje | null>(null);
	const [search, setSearch] = useState("");
	const [hasAccounts, setHasAccounts] = useState(false);
	const [cuentaHash, setCuentaHash] = useState("local");
	const [composeOpen, setComposeOpen] = useState(false);
	const [composeMode, setComposeMode] = useState<
		"nuevo" | "responder" | "responderTodos" | "reenviar"
	>("nuevo");
	const [composeTo, setComposeTo] = useState("");
	const [composeCc, setComposeCc] = useState("");
	const [feedback, setFeedback] = useState("");

	// Conservar la posición de la lista al reclasificar/sincronizar: se ancla el
	// primer mensaje visible y se restaura tras el reload. Si el usuario está al
	// principio (scrollTop ~0) NO hay ancla → la lista queda arriba y los correos
	// nuevos descargados quedan a la vista.
	const listRef = useRef<HTMLDivElement>(null);
	const anchorRef = useRef<{ mid: number; offset: number } | null>(null);

	const salvarAncla = () => {
		const c = listRef.current;
		if (!c || c.scrollTop < 8) {
			anchorRef.current = null;
			return;
		}
		const cTop = c.getBoundingClientRect().top;
		const items = Array.from(c.querySelectorAll("[data-mid]")) as HTMLElement[];
		const primero = items.find(
			(h) => h.getBoundingClientRect().bottom > cTop + 1,
		);
		anchorRef.current = primero
			? {
					mid: Number(primero.dataset.mid),
					offset: primero.getBoundingClientRect().top - cTop,
				}
			: null;
	};

	// Restaurar el ancla cuando la lista vuelve a pintarse
	useEffect(() => {
		const c = listRef.current;
		const a = anchorRef.current;
		if (!c || !a || loadingMensajes) return;
		anchorRef.current = null;
		const el = c.querySelector(`[data-mid="${a.mid}"]`) as HTMLElement | null;
		if (el) {
			c.scrollTop =
				c.scrollTop +
				(el.getBoundingClientRect().top - c.getBoundingClientRect().top) -
				a.offset;
		}
	}, [mensajes, loadingMensajes]);

	// Menú contextual (botón derecho) sobre un correo → crear evento/tarea
	const [menu, setMenu] = useState<{
		x: number;
		y: number;
		mensaje: Mensaje;
	} | null>(null);
	const [eventoOpen, setEventoOpen] = useState(false);
	const [eventoPrefill, setEventoPrefill] = useState<
		| { titulo: string; detalle: string; hora?: string; mensajeId: number }
		| undefined
	>();
	const [eventoFecha, setEventoFecha] = useState<string | undefined>();
	const [tareaOpen, setTareaOpen] = useState(false);
	const [tareaPrefill, setTareaPrefill] = useState<
		| {
				titulo: string;
				descripcion: string;
				fechaVencimiento?: string;
				mensajeId: number;
		  }
		| undefined
	>();

	// Prefijado desde correo: asunto → título, remitente+fragmento → detalle,
	// fecha/hora detectadas en asunto+cuerpo (español) con fallback a la fecha del correo
	const abrirEventoDesdeCorreo = (m: Mensaje) => {
		const det = detectarFechaHora(`${m.asunto || ""} ${m.cuerpo || ""}`);
		setEventoFecha(
			det?.fecha ||
				m.fechaRecepcion?.slice(0, 10) ||
				new Date().toISOString().slice(0, 10),
		);
		setEventoPrefill({
			titulo: m.asunto || "(sin asunto)",
			detalle: `De: ${m.remitente || "?"}\n\n${(m.cuerpo || "").slice(0, 500)}`,
			hora: det?.hora || undefined,
			mensajeId: m.id,
		});
		setEventoOpen(true);
	};

	const abrirTareaDesdeCorreo = (m: Mensaje) => {
		const det = detectarFechaHora(`${m.asunto || ""} ${m.cuerpo || ""}`);
		setTareaPrefill({
			titulo: m.asunto || "(sin asunto)",
			descripcion: `De: ${m.remitente || "?"}\n\n${(m.cuerpo || "").slice(0, 500)}`,
			fechaVencimiento: det?.fecha || m.fechaRecepcion?.slice(0, 10) || undefined,
			mensajeId: m.id,
		});
		setTareaOpen(true);
	};

	// Leer carpeta seleccionada desde query param (?carpeta=INBOX) pasado por el Layout
	const [searchParams] = useSearchParams();
	const carpetaImap = searchParams.get("carpeta") || "INBOX";

	const cargarMensajes = useCallback(
		async (carpeta?: string, preservarScroll = false) => {
			if (preservarScroll) salvarAncla();
			else anchorRef.current = null;
			setLoadingMensajes(true);
			setErrorMensajes(null);
			try {
				const cuentas = await cuentaApi.list();
				setHasAccounts(cuentas.data.length > 0);
				if (cuentas.data.length > 0) {
					const c = cuentas.data[0];
					setCuentaHash(c.email);
					const carpetaActual = carpeta || carpetaImap;
					const res = await mensajeApi.list(c.email, carpetaActual);
					setMensajes(res.data.mensajes || []);
				} else {
					setMensajes([]);
				}
			} catch (e) {
				setErrorMensajes(
					mensajeDeError(e, "Error al cargar los mensajes"),
				);
			} finally {
				setLoadingMensajes(false);
			}
		},
		[carpetaImap],
	);

	// Carga inicial y cuando cambia la carpeta
	useEffect(() => {
		cargarMensajes(carpetaImap);
	}, [carpetaImap, cargarMensajes]);

	// Recargar mensajes cuando SyncContext completa un sync (preservando posición:
	// solo salta arriba si el usuario ya estaba arriba → ve los nuevos)
	useEffect(() => {
		if (refreshKey > 0) cargarMensajes(carpetaImap, true);
	}, [refreshKey, cargarMensajes, carpetaImap]);

	const sincronizar = async () => {
		await triggerSync();
		await cargarMensajes(carpetaImap, true);
	};

	const handleSearch = async () => {
		if (!search.trim()) {
			await cargarMensajes(carpetaImap);
			return;
		}
		setLoadingMensajes(true);
		setErrorMensajes(null);
		try {
			const res = await mensajeApi.search(cuentaHash, search);
			setMensajes(res.data.mensajes || []);
		} catch (e) {
			setErrorMensajes(
				mensajeDeError(e, "Error en la búsqueda"),
			);
		} finally {
			setLoadingMensajes(false);
		}
	};

	// Borra en el servidor IMAP y en BD (misma llamada). Si se borrase solo en
	// local, el sync lo vuelve a descargar y resucita. POP3 no soporta borrado
	// en servidor: ahí queda solo local y reaparecerá al sync, no hay más tela.
	const eliminarMensaje = async () => {
		if (!selected) return;
		if (!window.confirm("¿Borrar el mensaje? Se borrará también del servidor.")) return;
		try {
			await mensajeApi.deleteServidor(selected.id);
			setMensajes((prev) => prev.filter((m) => m.id !== selected.id));
			setSelected(null);
		} catch (e) {
			setErrorMensajes(
				mensajeDeError(e, "No se pudo borrar el mensaje"),
			);
		}
	};

	const abrirCompose = (
		mode: "nuevo" | "responder" | "responderTodos" | "reenviar",
	) => {
		if (mode === "responder" && selected) {
			setComposeTo(emailDeRemitente(selected.remitente || ""));
			setComposeCc("");
		} else if (mode === "responderTodos" && selected) {
			// Remitente + destinatarios a «Para», CC originales a «CC», sin mi dirección
			const reparto = repartoRespuestaTodos(
				selected.remitente,
				selected.destinatarios,
				selected.cc,
				cuentaHash,
			);
			setComposeTo(reparto.para);
			setComposeCc(reparto.cc);
		} else {
			setComposeTo("");
			setComposeCc("");
		}
		setComposeMode(mode);
		setComposeOpen(true);
	};

	// Marcar SPAM/Legít: reclasifica el remitente en bloque y reentrena;
	// el feedback avisa de cuántos correos se han movido con él.
	const clasificar = async (categoria: "SPAM" | "LEGITIMO") => {
		if (!selected) return;
		try {
			const res = await mensajeApi.classify(selected.id, categoria);
			setSelected(res.data);
			if (res.data.reclasificados != null) {
				setFeedback(
					`Remitente marcado como ${categoria === "SPAM" ? "SPAM" : "LEGÍTIMO"} — ${res.data.reclasificados} correo(s) reclasificados`,
				);
				setTimeout(() => setFeedback(""), 4000);
			}
			if (categoria === "SPAM") {
				window.electronAPI?.clearCache(); // purge del caché HTTP (anti-tracking)
			}
			await cargarMensajes(carpetaImap, true);
		} catch (e) {
			setErrorMensajes(
				mensajeDeError(e, "No se pudo clasificar"),
			);
		}
	};

	if (composeOpen) {
		return (
			<ComposePage
				mode={composeMode}
				to={composeTo}
				cc={composeCc}
				subject={
					composeMode === "nuevo" || !selected
						? ""
						: asuntoConPrefijo(
								selected.asunto,
								composeMode === "reenviar" ? "RV:" : "Re:",
							)
				}
				body={
					composeMode === "reenviar" && selected
						? "\n\n--- Mensaje original ---\n" +
							(selected.cuerpo || htmlATexto(selected.html))
						: ""
				}
				onClose={() => {
					setComposeOpen(false);
					cargarMensajes(carpetaImap);
				}}
			/>
		);
	}

	return (
		<div className="flex h-full">
			<ListaMensajes
				mensajes={mensajes}
				loading={loadingMensajes}
				error={errorMensajes}
				hasAccounts={hasAccounts}
				seleccionadoId={selected?.id}
				statusText={statusText}
				feedback={feedback}
				syncing={syncing}
				search={search}
				setSearch={setSearch}
				onSearch={handleSearch}
				onRedactar={() => abrirCompose("nuevo")}
				onSync={sincronizar}
				onBorrar={eliminarMensaje}
				onSeleccionar={setSelected}
				onContextMenu={(x, y, mensaje) => setMenu({ x, y, mensaje })}
				onRetry={() => cargarMensajes(carpetaImap)}
				listRef={listRef}
			/>

			{/* Panel derecho: detalle */}
			<div
				className="flex-1 flex flex-col p-2.5 gap-2 overflow-hidden"
				style={{ backgroundColor: "var(--color-bg)" }}
			>
				{selected ? (
					<DetalleMensaje
						mensaje={selected}
						onResponder={() => abrirCompose("responder")}
						onResponderTodos={() => abrirCompose("responderTodos")}
						onReenviar={() => abrirCompose("reenviar")}
						onClasificar={clasificar}
					/>
				) : (
					<div
						className="flex items-center justify-center h-full text-sm"
						style={{ color: "var(--color-text-secondary)" }}
					>
						Selecciona un mensaje
					</div>
				)}
			</div>

			{/* Menú contextual: crear evento/tarea desde el correo */}
			{menu && (
				<ContextMenu
					x={menu.x}
					y={menu.y}
					onClose={() => setMenu(null)}
					items={[
						{
							icon: "📅",
							label: "Añadir evento al calendario",
							onClick: () => abrirEventoDesdeCorreo(menu.mensaje),
						},
						{
							icon: "✅",
							label: "Añadir tarea",
							onClick: () => abrirTareaDesdeCorreo(menu.mensaje),
						},
					]}
				/>
			)}
			<EventoDialog
				open={eventoOpen}
				fecha={eventoFecha}
				prefill={eventoPrefill}
				onClose={() => setEventoOpen(false)}
				onSaved={() => {}}
			/>
			<TareaDialog
				open={tareaOpen}
				prefill={tareaPrefill}
				onClose={() => setTareaOpen(false)}
				onSaved={() => {}}
			/>
		</div>
	);
}
