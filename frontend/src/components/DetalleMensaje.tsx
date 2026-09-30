import { formatearFecha } from "../utils/fechas";
import {
	htmlSegunCategoria,
	formatBytes,
	descargarAdjunto,
	type Mensaje,
} from "../utils/correo";

interface Props {
	mensaje: Mensaje;
	onResponder: () => void;
	onResponderTodos: () => void;
	onReenviar: () => void;
	onClasificar: (categoria: "SPAM" | "LEGITIMO") => void;
}

// Panel derecho de la bandeja: cabecera, adjuntos, cuerpo (iframe aislado)
// y la botonera IA + SPAM/Legít.
export default function DetalleMensaje({
	mensaje: selected,
	onResponder,
	onResponderTodos,
	onReenviar,
	onClasificar,
}: Props) {
	return (
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
						onClick={onResponder}
						className="px-3 py-1 text-xs font-bold rounded-pill"
						style={{
							backgroundColor: "var(--color-accent)",
							color: "#0F172A",
						}}
					>
						Responder
					</button>
					<button
						onClick={onResponderTodos}
						className="px-3 py-1 text-xs rounded-pill"
						style={{
							backgroundColor: "var(--color-bg-elevated)",
							color: "var(--color-text)",
						}}
					>
						Resp. todos
					</button>
					<button
						onClick={onReenviar}
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
				<div className="flex-1">
					<p
						className="text-[10px] mb-1"
						style={{ color: "var(--color-text-secondary)" }}
					>
						Respuestas IA
					</p>
					<div className="flex gap-1.5">
						{["Responder", "Agradecer", "+Info"].map((s) => (
							<button
								key={s}
								className="text-[10px] px-2.5 py-1 rounded-pill font-bold"
								style={{
									backgroundColor: "var(--color-accent)",
									color: "#0F172A",
								}}
							>
								{s}
							</button>
						))}
					</div>
				</div>
				{/* SPAM/Legít a la derecha, apilados verticalmente */}
				<div className="flex flex-col gap-1 shrink-0">
					<button
						onClick={() => onClasificar("SPAM")}
						className="px-3 py-1.5 text-[10px] font-bold rounded-pill"
						style={{ backgroundColor: "#ef4444", color: "white" }}
					>
						🚫 SPAM
					</button>
					<button
						onClick={() => onClasificar("LEGITIMO")}
						className="px-3 py-1.5 text-[10px] font-bold rounded-pill"
						style={{ backgroundColor: "#22c55e", color: "white" }}
					>
						✅ Legít
					</button>
				</div>
			</div>
		</>
	);
}
