import { useState, useEffect } from 'react';
import { contactoApi } from '../api/client';

interface Contacto {
  id: number; nombre: string; email: string; telefono: string;
}

// CRUD de contactos con datos cifrados (email, teléfono) en backend.
export default function ContactosPage() {
  const [contactos, setContactos] = useState<Contacto[]>([]);
  const [selected, setSelected] = useState<Contacto | null>(null);
  const [form, setForm] = useState({ nombre: '', email: '', telefono: '' });

  useEffect(() => { load(); }, []);

  const load = () => contactoApi.list().then(r => setContactos(r.data)).catch(() => {});

  const save = async () => {
    if (selected) {
      await contactoApi.update(selected.id, { ...form, apellido: '', notas: '' });
    } else {
      await contactoApi.create({ ...form, apellido: '', notas: '' });
    }
    setForm({ nombre: '', email: '', telefono: '' });
    setSelected(null);
    load();
  };

  const edit = (c: Contacto) => {
    setSelected(c);
    setForm({ nombre: c.nombre, email: c.email || '', telefono: c.telefono || '' });
  };

  const remove = async (id: number) => {
    await contactoApi.delete(id);
    if (selected?.id === id) { setSelected(null); setForm({ nombre: '', email: '', telefono: '' }); }
    load();
  };

  return (
    <div className="flex h-full p-4 gap-4" style={{ backgroundColor: 'var(--color-bg)' }}>
      {/* Lista */}
      <div className="w-[300px] shrink-0 space-y-1 overflow-y-auto">
        {contactos.map(c => (
          <div key={c.id} onClick={() => edit(c)}
            className="p-2 rounded-lg cursor-pointer text-sm"
            style={{
              backgroundColor: selected?.id === c.id ? 'var(--color-accent-selected)' : 'var(--color-bg-card)',
              color: selected?.id === c.id ? '#0F172A' : 'var(--color-text)',
            }}>
            <div className="font-bold">{c.nombre}</div>
            <div className="text-xs" style={{ color: 'var(--color-text-secondary)' }}>{c.email}</div>
          </div>
        ))}
        {contactos.length === 0 && (
          <p className="text-xs text-center" style={{ color: 'var(--color-text-secondary)' }}>
            Sin contactos</p>
        )}
      </div>

      {/* Formulario */}
      <div className="flex-1 max-w-md space-y-3">
        <h3 className="text-sm font-bold" style={{ color: 'var(--color-text)' }}>
          {selected ? 'Editar contacto' : 'Nuevo contacto'}</h3>
        <input placeholder="Nombre" value={form.nombre}
          onChange={e => setForm(f => ({ ...f, nombre: e.target.value }))}
          className="w-full px-2 py-1.5 text-sm rounded-lg border outline-none"
          style={{ backgroundColor: 'var(--color-bg)', color: 'var(--color-text)',
                   borderColor: 'var(--color-border)' }} />
        <input placeholder="Email" value={form.email}
          onChange={e => setForm(f => ({ ...f, email: e.target.value }))}
          className="w-full px-2 py-1.5 text-sm rounded-lg border outline-none"
          style={{ backgroundColor: 'var(--color-bg)', color: 'var(--color-text)',
                   borderColor: 'var(--color-border)' }} />
        <input placeholder="Teléfono" value={form.telefono}
          onChange={e => setForm(f => ({ ...f, telefono: e.target.value }))}
          className="w-full px-2 py-1.5 text-sm rounded-lg border outline-none"
          style={{ backgroundColor: 'var(--color-bg)', color: 'var(--color-text)',
                   borderColor: 'var(--color-border)' }} />
        <div className="flex gap-2">
          <button onClick={save}
            className="px-4 py-1.5 text-sm font-bold rounded-pill"
            style={{ backgroundColor: 'var(--color-accent)', color: '#0F172A' }}>
            {selected ? 'Actualizar' : 'Añadir'}</button>
          {selected && <button onClick={() => remove(selected.id)}
            className="px-4 py-1.5 text-sm rounded-pill"
            style={{ backgroundColor: '#ef4444', color: 'white' }}>Eliminar</button>}
        </div>
      </div>
    </div>
  );
}
