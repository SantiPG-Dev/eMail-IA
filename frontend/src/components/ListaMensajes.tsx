import type React from "react";
import { Spinner, EmptyState, ErrorState } from "./StateViews";
import { formatearFecha } from "../utils/fechas";
import type { Mensaje } from "../utils/correo";

interface Props {
	mensajes: Mensaje[];
	loading: boolean;
	error: string | null;
	hasAccounts: boolean;
	seleccionadoId?: number;
	statusText: string;
	feedback: string;
	syncing: boolean;
	search: string;
	setSearch: (s: string) => void;
	onSearch: () => void;
	onRedactar: () => void;
	onSync: () => void;
	onBorrar: () => void;
	onSeleccionar: (m: Mensaje) => void;
	onContextMenu: (x: number, y: number, m: Mensaje) => void;
	onRetry: () => void;
	listRef: React.RefObject<HTMLDivElement | null>;
}

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

// Panel izquierdo de la bandeja: botones, buscador y la lista scrolleable.
// Conservar la posición de la lista al reclasificar/sincronizar: se ancla el
// primer mensaje visible y se restaura tras el reload. Si el usuario está al
// principio (scrollTop ~0) NO hay ancla → la lista queda arriba y los correos
// nuevos descargados quedan a la vista.
export default function ListaMensajes({
	mensajes, loading, error, hasAccounts, seleccionadoId, statusText, feedback,
	syncing, search, setSearch, onSearch, onRedactar, onSync, onBorrar,
	onSeleccionar, onContextMenu, onRetry, listRef,
}: Props) {
	return (
		<div
			className="w-[340px] min-w-[260px] flex flex-col gap-2 p-2.5"
			style={{ backgroundColor: "var(--color-bg)" }}
		>
			{/* Botones superiores: Redactar + Borrar */}
			<div className="flex gap-1.5 items-center">
				<button
					onClick={onRedactar}
					className="px-3 py-1.5 text-xs font-bold rounded-pill"
					style={{ backgroundColor: "var(--color-accent)", color: "#0F172A" }}
				>
					Redactar
				</button>
				<div className="flex-1 flex justify-center">
					<button
						onClick={onSync}
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
					onClick={onBorrar}
					disabled={!seleccionadoId}
					className="px-3 py-1.5 text-xs font-bold rounded-pill disabled:opacity-30"
					style={{ backgroundColor: "#ef4444", color: "white" }}
				>
					Borrar
				</button>
			</div>

			{/* Buscador + Sync */}
			<div className="flex gap-1">
				<input
					value={search}
					onChange={(e) => setSearch(e.target.value)}
					onKeyDown={(e) => e.key === "Enter" && onSearch()}
					placeholder="Buscar..."
					className="flex-1 px-2 py-1.5 text-xs rounded-lg border outline-none"
					style={{
						backgroundColor: "var(--color-bg)",
						color: "var(--color-text)",
						borderColor: "var(--color-border)",
					}}
				/>
				<button
					onClick={onSearch}
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
				{loading ? (
					<Spinner label="Cargando mensajes..." />
				) : error ? (
					<ErrorState message={error} onRetry={onRetry} />
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
							onClick={() => onSeleccionar(m)}
							onContextMenu={(e) => {
								e.preventDefault();
								onContextMenu(e.clientX, e.clientY, m);
							}}
							className="px-2.5 py-1.5 rounded-lg cursor-pointer text-xs"
							style={{
								border: categoriaBorder(m.categoria),
								backgroundColor:
									seleccionadoId === m.id
										? "var(--color-accent-selected)"
										: categoriaBg(m.categoria),
								color: seleccionadoId === m.id ? "#0F172A" : "var(--color-text)",
							}}
						>
							<div className="font-bold truncate flex items-center gap-1">
							{!m.leido && <span aria-hidden>●</span>}
							<span className="truncate">{m.remitente || "(sin remitente)"}</span>
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
	);
}
