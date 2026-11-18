-- Ejecutar una vez con el propietario de la BD. No elimina tablas existentes.
BEGIN;
CREATE TABLE public.crm_cuenta (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 nombre varchar(160) NOT NULL, sector varchar(120), sitio_web varchar(300),
 cliente_id bigint REFERENCES public.cliente(id_cliente),
 created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE public.crm_contacto (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 nombre varchar(160) NOT NULL, correo varchar(180), telefono varchar(30),
 estado varchar(25) NOT NULL CHECK (estado IN ('NUEVO','CONTACTADO','CALIFICADO','CLIENTE','DESCARTADO')),
 origen varchar(120) NOT NULL, etiquetas varchar(500) NOT NULL DEFAULT '',
 cuenta_id bigint REFERENCES public.crm_cuenta(id) ON DELETE SET NULL,
 campana_id bigint REFERENCES public.campana(id_campana) ON DELETE SET NULL,
 responsable_id bigint NOT NULL REFERENCES public.usuario(id_usuario),
 created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
 CHECK (correo IS NOT NULL OR telefono IS NOT NULL)
);
CREATE UNIQUE INDEX crm_contacto_correo_unico ON public.crm_contacto(lower(correo)) WHERE correo IS NOT NULL;
CREATE UNIQUE INDEX crm_contacto_telefono_unico ON public.crm_contacto(telefono) WHERE telefono IS NOT NULL;
CREATE INDEX crm_contacto_cuenta ON public.crm_contacto(cuenta_id);
CREATE TABLE public.crm_consentimiento (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 contacto_id bigint NOT NULL REFERENCES public.crm_contacto(id) ON DELETE CASCADE,
 tipo varchar(60) NOT NULL, canal varchar(60) NOT NULL,
 fecha timestamptz NOT NULL, revocado_en timestamptz,
 registrado_por bigint NOT NULL REFERENCES public.usuario(id_usuario)
);
CREATE TABLE public.crm_nota (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 contacto_id bigint REFERENCES public.crm_contacto(id) ON DELETE CASCADE,
 cuenta_id bigint REFERENCES public.crm_cuenta(id) ON DELETE CASCADE,
 texto varchar(4000) NOT NULL, autor_id bigint NOT NULL REFERENCES public.usuario(id_usuario),
 created_at timestamptz NOT NULL DEFAULT now(),
 CHECK (num_nonnulls(contacto_id,cuenta_id)=1)
);
CREATE TABLE public.crm_evento (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 contacto_id bigint REFERENCES public.crm_contacto(id) ON DELETE CASCADE,
 cuenta_id bigint REFERENCES public.crm_cuenta(id) ON DELETE CASCADE,
 tipo varchar(60) NOT NULL, detalle varchar(1000) NOT NULL,
 autor_id bigint NOT NULL REFERENCES public.usuario(id_usuario),
 created_at timestamptz NOT NULL DEFAULT now(),
 CHECK (num_nonnulls(contacto_id,cuenta_id)=1)
);
CREATE TABLE public.crm_archivo (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 contacto_id bigint REFERENCES public.crm_contacto(id) ON DELETE CASCADE,
 cuenta_id bigint REFERENCES public.crm_cuenta(id) ON DELETE CASCADE,
 nombre varchar(200) NOT NULL, contenido bytea NOT NULL CHECK (octet_length(contenido)<=5242880),
 autor_id bigint NOT NULL REFERENCES public.usuario(id_usuario), created_at timestamptz NOT NULL DEFAULT now(),
 CHECK (num_nonnulls(contacto_id,cuenta_id)=1)
);
-- Impedir acceso directo a datos CRM desde clientes Supabase. El backend usa JDBC.
CREATE TABLE public.crm_auditoria (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY, recurso varchar(30) NOT NULL,
 registro_id bigint NOT NULL, accion varchar(60) NOT NULL,
 autor_id bigint NOT NULL REFERENCES public.usuario(id_usuario), created_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE public.crm_auditoria ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.crm_cuenta ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.crm_contacto ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.crm_consentimiento ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.crm_nota ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.crm_evento ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.crm_archivo ENABLE ROW LEVEL SECURITY;
INSERT INTO public.permiso(codigo,descripcion,modulo)
SELECT v.codigo,v.descripcion,'CRM' FROM (VALUES
 ('CRM_CONTACTOS_LEER','Consultar contactos e historial'),
 ('CRM_CONTACTOS_GESTIONAR','Gestionar contactos e importar CSV'),
 ('CRM_CUENTAS_LEER','Consultar cuentas'),
 ('CRM_CUENTAS_GESTIONAR','Gestionar cuentas')
) AS v(codigo,descripcion) WHERE NOT EXISTS (SELECT 1 FROM public.permiso p WHERE p.codigo=v.codigo);
INSERT INTO public.rol_permiso(id_rol,id_permiso)
SELECT r.id_rol,p.id_permiso FROM public.rol r CROSS JOIN public.permiso p
WHERE (lower(r.nombre_rol) ~ '(admin|lider|líder|manager|lead)' AND p.codigo LIKE 'CRM_%'
 OR lower(r.nombre_rol) ~ '(analista|operador)' AND p.codigo='CRM_CONTACTOS_LEER')
AND NOT EXISTS (SELECT 1 FROM public.rol_permiso rp WHERE rp.id_rol=r.id_rol AND rp.id_permiso=p.id_permiso);
COMMIT;
