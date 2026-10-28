import { useState, useEffect, useCallback, useRef } from "react";
import { useSearchParams } from "react-router-dom";
import api, { mensajeApi, cuentaApi } from "../api/client";
import { useSync } from "../context/SyncContext";
import { Spinner, EmptyState, ErrorState } from "../components/StateViews";
import ComposePage from "./ComposePage";
import ContextMenu from "../components/ContextMenu";
import { formatearFecha, detectarFechaHora } from "../utils/fechas";
import EventoDialog from "../components/EventoDialog";
import TareaDialog from "../components/TareaDialog";
import {
	htmlSegunCategoria,
	formatBytes,
	descargarAdjunto,
	emailDeRemitente,
	asuntoConPrefijo,
	htmlATexto,
	repartoRespuestaTodos,
	type Mensaje,
} from "../utils/correo";

// Página principal de correo: lista de mensajes (split 30/70) con detalle,
// iframe HTML, panel IA (resumen/sugerir), y acciones (clasificar, borrar, mover).
export default function CorreoPage() {
	const [mensajes, setMensajes] = useState<Mensaje[]>([]);
	const [loadingMensajes, setLoadingMensajes] = useState(true);
	const [errorMensajes, setErrorMensajes] = useState<string | null>(null);
	const { triggerSync, syncing, statusText, refreshKey } = useSync();
	const [selected, setSelected] = useState<Mensaje | null>(null);
	const [search, setSearch] = useState("");
	const [hasAccounts, setHasAccounts] = useState(false);
	// Multi-cuenta: la cuenta activa se recuerda entre sesiones; por defecto
	// la marcada como predeterminada en Configuración.
	const [cuentas, setCuentas] = useState<
		{ id: number; email: string; nombre: string; esDefault: boolean }[]
	>([]);
	const [cuentaEmail, setCuentaEmail] = useState("");
	const [composeOpen, setComposeOpen] = useState(false);
	const [composeMode, setComposeMode] = useState<
		"nuevo" | "responder" | "responderTodos" | "reenviar"
	>("nuevo");
	const [composeTo, setComposeTo] = useState("");
	const [composeCc, setComposeCc] = useState("");
	const [feedback, setFeedback] = useState("");
	const [iaTexto, setIaTexto] = useState("");
	const [iaCargando, setIaCargando] = useState<"" | "resumen" | "sugerir">("");

	// Resumen / respuesta sugerida por IA del mensaje abierto
	const pedirIa = async (tipo: "resumen" | "sugerir") => {
		if (!selected) return;
		setIaCargando(tipo);
		setIaTexto("");
		try {
			const res =
				tipo === "resumen"
					? await mensajeApi.summarize(selected.id)
					: await mensajeApi.suggest(selected.id);
			setIaTexto(String(res.data || ""));
		} catch (e: any) {
			setIaTexto(
				"Error: " + (e?.response?.data?.error || e?.message || "sin respuesta"),
			);
		} finally {
			setIaCargando("");
		}
	};

	// Conservar la posición de la lista al reclasificar/sincronizar: se ancla el
	// primer mensaje visible y se restaura tras el reload. Si el usuario está al
	// principio (scrollTop ~0) NO hay ancla → la lista queda arriba y los correos
	// nuevos descargados quedan a la vista.
	const listRef = useRef<HTMLDivElement>(null);
	const anchorRef = useRef<{ mid: number; offset: number } | null>(null);
	// Evita solapar dos refrescos automaticos si uno tarda mas de 5 s
	const refrescoEnCurso = useRef(false);

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
		async (carpeta?: string, preservarScroll = false, silencioso = false) => {
			if (preservarScroll) salvarAncla();
			else anchorRef.current = null;
			// En modo silencioso (refresco automatico) no se toca el spinner ni
			// el estado de error: la lista sigue visible y no parpadea.
			if (!silencioso) {
				setLoadingMensajes(true);
				setErrorMensajes(null);
			}
			try {
				if (!cuentaEmail) {
					setMensajes([]);
					return;
				}
				const carpetaActual = carpeta || carpetaImap;
				const res = await mensajeApi.list(cuentaEmail, carpetaActual);
				setMensajes(res.data.mensajes || []);
			} catch (e: any) {
				if (silencioso) return;
				setErrorMensajes(
					e?.response?.data?.error || e?.message || "Error al cargar los mensajes",
				);
			} finally {
				if (!silencioso) setLoadingMensajes(false);
			}
		},
		[carpetaImap, cuentaEmail],
	);

	// Cuentas disponibles (selector de multi-cuenta). La activa se guarda en
	// localStorage y cae a la predeterminada o a la primera.
	useEffect(() => {
		cuentaApi
			.list()
			.then((r) => {
				const lista = r.data || [];
				setCuentas(lista);
				setHasAccounts(lista.length > 0);
				if (lista.length === 0) return;
				const guardada = localStorage.getItem("emailai_cuenta");
				const elegida =
					lista.find((c: any) => c.email === guardada) ||
					lista.find((c: any) => c.esDefault) ||
					lista[0];
				setCuentaEmail(elegida.email);
			})
			.catch(() => {
				setCuentas([]);
				setHasAccounts(false);
			});
	}, []);

	const cambiarCuenta = (email: string) => {
		setCuentaEmail(email);
		localStorage.setItem("emailai_cuenta", email);
		setSelected(null);
		setSearch("");
	};

	// Carga inicial y cuando cambia la carpeta
	useEffect(() => {
		cargarMensajes(carpetaImap);
	}, [carpetaImap, cargarMensajes]);

	// Recargar mensajes cuando SyncContext completa un sync (preservando posición:
	// solo salta arriba si el usuario ya estaba arriba → ve los nuevos)
	useEffect(() => {
		if (refreshKey > 0) cargarMensajes(carpetaImap, true);
	}, [refreshKey, cargarMensajes, carpetaImap]);

	// Refresco automatico cada 5 s, sin spinner y sin mover el scroll. Se para
	// mientras hay una busqueda activa para no pisar sus resultados.
	useEffect(() => {
		if (!cuentaEmail || search.trim()) return;
		const id = setInterval(async () => {
			if (refrescoEnCurso.current) return;
			refrescoEnCurso.current = true;
			try {
				await cargarMensajes(carpetaImap, true, true);
			} finally {
				refrescoEnCurso.current = false;
			}
		}, 5000);
		return () => clearInterval(id);
	}, [cargarMensajes, carpetaImap, cuentaEmail, search]);

	// Al abrir un correo se marca como leído (estado local; el SEEN del servidor
	// no se toca). Lanzamiento silencioso: si falla, quedará sin marcar y se
	// reintentará la próxima vez que se abra.
	const seleccionar = (m: Mensaje) => {
		setIaTexto("");
		if (m.leido) {
			setSelected(m);
			return;
		}
		const leido = { ...m, leido: true };
		setSelected(leido);
		setMensajes((prev) => prev.map((x) => (x.id === m.id ? leido : x)));
		mensajeApi.marcarLeido(m.id).catch(() => {});
	};

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
			const res = await mensajeApi.search(cuentaEmail, search);
			setMensajes(res.data.mensajes || []);
		} catch (e: any) {
			setErrorMensajes(
				e?.response?.data?.error || e?.message || "Error en la búsqueda",
			);
		} finally {
			setLoadingMensajes(false);
		}
	};

	const eliminarMensaje = async () => {
		if (!selected) return;
		if (
			!window.confirm(
				"¿Borrar el mensaje seleccionado? Se intentara tambien en el servidor.",
			)
		)
			return;
		const id = selected.id;
		try {
			await mensajeApi.deleteServidor(id);
		} catch (e: any) {
			// Si el servidor no lo borra (sin credenciales IMAP, sin red), el
			// mensaje se queda: borrarlo solo en local haria que resucitara en el
			// siguiente sync.
			setFeedback(
				e?.response?.data?.error ||
					e?.message ||
					"No se pudo borrar el mensaje en el servidor",
			);
			setTimeout(() => setFeedback(""), 6000);
			return;
		}
		setMensajes((prev) => prev.filter((m) => m.id !== id));
		setSelected(null);
		setFeedback("Borrado del servidor");
		setTimeout(() => setFeedback(""), 4000);
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
				cuentaEmail,
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

	const categoriaBorder = (cat: string) => {
		switch (cat) {
			case "SPAM":
			case "PHISHING":
				return "2px solid #ef4444";
			case "LEGITIMO":
				return "2px solid #22c55e";
			default:
				return "2px solid #fbbf24";
		}
	};

	const categoriaBg = (cat: string) => {
		switch (cat) {
			case "SPAM":
			case "PHISHING":
				return "#2d1619";
			case "LEGITIMO":
				return "#13281b";
			default:
				return "#2b2412";
		}
	};

	if (composeOpen) {
		return (
			<ComposePage
				mode={composeMode}
				to={composeTo}
				cc={composeCc}
				cuenta={cuentaEmail}
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
			{/* Panel izquierdo: lista */}
			<div
				className="w-[340px] min-w-[260px] flex flex-col gap-2 p-2.5"
				style={{ backgroundColor: "var(--color-bg)" }}
			>
				{/* Botones superiores: Redactar + Borrar */}
				<div className="flex gap-1.5 items-center">
					<button
						onClick={() => abrirCompose("nuevo")}
						className="px-3 py-1.5 text-xs font-bold rounded-pill"
						style={{ backgroundColor: "var(--color-accent)", color: "#0F172A" }}
					>
						Redactar
					</button>
					<div className="flex-1 flex justify-center">
						<button
							onClick={sincronizar}
							disabled={syncing}
							className="shrink-0 text-xs px-2 py-1.5 rounded-pill font-bold transition-colors disabled:opacity-40"
							style={{
								backgroundColor: syncing
									? "var(--color-bg-elevated)"
									: "var(--color-accent)",
								color: syncing ? "var(--color-text-muted)" : "#0F172A",
							}}
							title={syncing ? "Sincronizando..." : "Enviar/Recibir"}
						>
							{syncing ? "⟳" : "↕"}
						</button>
					</div>
					<button
						onClick={eliminarMensaje}
						disabled={!selected}
						className="px-3 py-1.5 text-xs font-bold rounded-pill disabled:opacity-30"
						style={{ backgroundColor: "#ef4444", color: "white" }}
					>
						Borrar
					</button>
				</div>

				{/* Selector de cuenta (solo si hay varias) */}
				{cuentas.length > 1 && (
					<select
						value={cuentaEmail}
						onChange={(e) => cambiarCuenta(e.target.value)}
						className="w-full px-2 py-1.5 text-xs rounded-lg border outline-none cursor-pointer"
						style={{
							backgroundColor: "var(--color-bg)",
							color: "var(--color-text)",
							borderColor: "var(--color-border)",
						}}
						title="Cuenta activa"
					>
						{cuentas.map((c) => (
							<option key={c.id} value={c.email}>
								{c.nombre} — {c.email}
							</option>
						))}
					</select>
				)}

				{/* Buscador + Sync */}
				<div className="flex gap-1">
					<input
						value={search}
						onChange={(e) => setSearch(e.target.value)}
						onKeyDown={(e) => e.key === "Enter" && handleSearch()}
						placeholder="Buscar..."
						className="flex-1 px-2 py-1.5 text-xs rounded-lg border outline-none"
						style={{
							backgroundColor: "var(--color-bg)",
							color: "var(--color-text)",
							borderColor: "var(--color-border)",
						}}
					/>
					<button
						onClick={handleSearch}
						className="px-2 py-1.5 text-xs font-bold rounded-pill"
						style={{ backgroundColor: "var(--color-accent)", color: "#0F172A" }}
					>
						🔍
					</button>
				</div>

				{statusText && statusText !== "Inactivo" && (
					<p
						className="text-xs"
						style={{
							color: statusText.includes("Error") ? "#ef4444" : "var(--color-accent)",
						}}
					>
						{statusText}
					</p>
				)}

				{feedback && (
					<p
						className="text-xs font-medium"
						style={{ color: "var(--color-accent)" }}
					>
						{feedback}
					</p>
				)}

				<div ref={listRef} className="flex-1 overflow-y-auto space-y-1">
					{loadingMensajes ? (
						<Spinner label="Cargando mensajes..." />
					) : errorMensajes ? (
						<ErrorState
							message={errorMensajes}
							onRetry={() => cargarMensajes(carpetaImap)}
						/>
					) : mensajes.length === 0 ? (
						<EmptyState
							icon={hasAccounts ? "📬" : "⚙️"}
							title={hasAccounts ? "Bandeja vacía" : "Sin cuenta configurada"}
							hint={
								hasAccounts
									? "Pulsa ↕ para sincronizar tu correo"
									: "Añade una cuenta en Configuración"
							}
						/>
					) : (
						mensajes.map((m) => (
							<div
								key={m.id}
								data-mid={m.id}
								onClick={() => seleccionar(m)}
								onContextMenu={(e) => {
									e.preventDefault();
									setMenu({ x: e.clientX, y: e.clientY, mensaje: m });
								}}
								className="px-2.5 py-1.5 rounded-lg cursor-pointer text-xs"
								style={{
									border: categoriaBorder(m.categoria),
									backgroundColor:
										selected?.id === m.id
											? "var(--color-accent-selected)"
											: categoriaBg(m.categoria),
									color: selected?.id === m.id ? "#0F172A" : "var(--color-text)",
								}}
							>
								<div className="font-bold truncate flex items-center gap-1">
									{!m.leido && <span aria-hidden>●</span>}
									<span className="truncate">
										{m.remitente || "(sin remitente)"}
									</span>
								</div>
								<div className="truncate opacity-80">{m.asunto}</div>
								<div className="text-[10px] opacity-60">
									{formatearFecha(m.fechaRecepcion)}
								</div>
							</div>
						))
					)}
				</div>
			</div>

			{/* Panel derecho: detalle */}
			<div
				className="flex-1 flex flex-col p-2.5 gap-2 overflow-hidden"
				style={{ backgroundColor: "var(--color-bg)" }}
			>
				{selected ? (
					<>
						{/* Fila: izq = asunto+remitente, dcha = botones */}
						<div className="flex items-start gap-4">
							{/* Izquierda: asunto + remitente + fecha */}
							<div className="flex-1 min-w-0">
								<h3
									className="text-base font-bold truncate"
									style={{ color: "var(--color-accent-selected)" }}
								>
									{selected.asunto}
								</h3>
								<div
									className="flex items-center gap-2 text-xs mt-0.5"
									style={{ color: "var(--color-text-secondary)" }}
								>
									<span className="truncate">{selected.remitente}</span>
									<span>·</span>
									<span className="shrink-0">
										{formatearFecha(selected.fechaRecepcion)}
									</span>
									{selected.categoria && selected.categoria !== "DESCONOCIDO" && (
										<span
											className="px-2 py-0.5 rounded text-[10px] font-bold uppercase tracking-wider shrink-0"
											style={{
												backgroundColor:
													selected.categoria === "SPAM" || selected.categoria === "PHISHING"
														? "#dc2626"
														: selected.categoria === "LEGITIMO"
															? "#16a34a"
															: "#ca8a04",
												color: "#fff",
												border:
													selected.categoria === "SPAM" || selected.categoria === "PHISHING"
														? "1px solid #ef4444"
														: selected.categoria === "LEGITIMO"
															? "1px solid #22c55e"
															: "1px solid #fbbf24",
											}}
										>
											{selected.categoria}
										</span>
									)}
								</div>
							</div>
							{/* Derecha: botones de acción alineados a la derecha */}
							<div className="flex gap-1.5 shrink-0">
								<button
									onClick={() => abrirCompose("responder")}
									className="px-3 py-1 text-xs font-bold rounded-pill"
									style={{
										backgroundColor: "var(--color-accent)",
										color: "#0F172A",
									}}
								>
									Responder
								</button>
								<button
									onClick={() => abrirCompose("responderTodos")}
									className="px-3 py-1 text-xs rounded-pill"
									style={{
										backgroundColor: "var(--color-bg-elevated)",
										color: "var(--color-text)",
									}}
								>
									Resp. todos
								</button>
								<button
									onClick={() => abrirCompose("reenviar")}
									className="px-3 py-1 text-xs rounded-pill"
									style={{
										backgroundColor: "var(--color-bg-elevated)",
										color: "var(--color-text)",
									}}
								>
									Reenviar
								</button>
							</div>
						</div>

						{/* Adjuntos */}
						{selected.adjuntos && selected.adjuntos.length > 0 && (
							<div
								className="rounded-lg p-2 flex flex-wrap gap-2"
								style={{ backgroundColor: "var(--color-bg-card)" }}
							>
								{selected.adjuntos.map((a) => (
									<button
										key={a.id}
										onClick={() => descargarAdjunto(selected.id, a)}
										title={`Descargar ${a.nombre}`}
										className="flex items-center gap-2 px-2.5 py-1.5 rounded-lg text-xs max-w-full"
										style={{
											backgroundColor: "var(--color-bg-elevated)",
											color: "var(--color-text)",
										}}
									>
										<span aria-hidden>📎</span>
										<span className="truncate max-w-[220px]">{a.nombre}</span>
										{a.tamanoBytes ? (
											<span className="shrink-0 text-[10px] opacity-60">
												{formatBytes(a.tamanoBytes)}
											</span>
										) : null}
									</button>
								))}
							</div>
						)}

						{/* Cuerpo */}
						<div
							className="flex-1 overflow-auto rounded-lg p-2"
							style={{ backgroundColor: "var(--color-bg-card)" }}
						>
							{selected.html ? (
								// Iframe totalmente aislado: sin allow-scripts (scripts del
								// correo inertes) y SIN allow-same-origin (origen único opaco:
								// el correo no puede tocar el localStorage de la app donde
								// vive el JWT). DOMPurify + CSP añaden capas por si sandbox falla.
								<iframe
									key={selected.id + ":" + (selected.categoria || "x")}
									srcDoc={htmlSegunCategoria(selected.html, selected.categoria)}
									className="w-full h-full border-0"
									title="Cuerpo"
									sandbox="allow-popups allow-popups-to-escape-sandbox"
								/>
							) : (
								<pre
									className="text-sm whitespace-pre-wrap font-sans"
									style={{ color: "var(--color-text)" }}
								>
									{selected.cuerpo}
								</pre>
							)}
						</div>

						{/* IA suggestions + SPAM/Legít (derecha) */}
						<div
							className="rounded-lg p-2 flex items-start gap-2"
							style={{ backgroundColor: "var(--color-bg-card)" }}
						>
							{/* Botones IA a la izquierda */}
							<div className="flex-1 min-w-0">
								<p
									className="text-[10px] mb-1"
									style={{ color: "var(--color-text-secondary)" }}
								>
									Respuestas IA
								</p>
								<div className="flex gap-1.5">
									{(
										[
											["resumen", "Resumir"],
											["sugerir", "Sugerir respuesta"],
										] as const
									).map(([tipo, label]) => (
										<button
											key={tipo}
											onClick={() => pedirIa(tipo)}
											disabled={!!iaCargando}
											className="text-[10px] px-2.5 py-1 rounded-pill font-bold disabled:opacity-50"
											style={{
												backgroundColor: "var(--color-accent)",
												color: "#0F172A",
											}}
										>
											{iaCargando === tipo ? "..." : label}
										</button>
									))}
								</div>
								{iaTexto && (
									<div className="mt-1.5">
										<pre
											className="text-[11px] whitespace-pre-wrap font-sans max-h-32 overflow-auto rounded-lg p-2"
											style={{
												backgroundColor: "var(--color-bg-elevated)",
												color: "var(--color-text)",
											}}
										>
											{iaTexto}
										</pre>
										<button
											onClick={() => setIaTexto("")}
											className="text-[10px] mt-1"
											style={{ color: "var(--color-text-secondary)" }}
										>
											Ocultar
										</button>
									</div>
								)}
							</div>
							{/* SPAM/Legít a la derecha, apilados verticalmente */}
							<div className="flex flex-col gap-1 shrink-0">
								<button
									onClick={async () => {
										if (!selected) return;
										try {
											const res = await mensajeApi.classify(selected.id, "SPAM");
											setSelected(res.data);
											if (res.data.reclasificados != null) {
												setFeedback(
													`Remitente marcado como SPAM — ${res.data.reclasificados} correo(s) reclasificados`,
												);
												setTimeout(() => setFeedback(""), 4000);
											}
											window.electronAPI?.clearCache(); // purge del caché HTTP (anti-tracking)
											await cargarMensajes(carpetaImap, true);
										} catch (e: any) {
											setErrorMensajes(
												e?.response?.data?.error || e?.message || "No se pudo clasificar",
											);
										}
									}}
									className="px-3 py-1.5 text-[10px] font-bold rounded-pill"
									style={{ backgroundColor: "#ef4444", color: "white" }}
								>
									🚫 SPAM
								</button>
								<button
									onClick={async () => {
										if (!selected) return;
										try {
											const res = await mensajeApi.classify(selected.id, "LEGITIMO");
											setSelected(res.data);
											if (res.data.reclasificados != null) {
												setFeedback(
													`Remitente marcado como LEGÍTIMO — ${res.data.reclasificados} correo(s) reclasificados`,
												);
												setTimeout(() => setFeedback(""), 4000);
											}
											await cargarMensajes(carpetaImap, true);
										} catch (e: any) {
											setErrorMensajes(
												e?.response?.data?.error || e?.message || "No se pudo clasificar",
											);
										}
									}}
									className="px-3 py-1.5 text-[10px] font-bold rounded-pill"
									style={{ backgroundColor: "#22c55e", color: "white" }}
								>
									✅ Legít
								</button>
							</div>
						</div>
					</>
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
