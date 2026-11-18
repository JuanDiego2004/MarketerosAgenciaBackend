-- Ejecutar DESPUÉS de crm-v1.sql, una sola vez. Migración aditiva y transaccional.
BEGIN;
CREATE TABLE public.crm_etapa (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 nombre varchar(160) NOT NULL UNIQUE, orden int NOT NULL UNIQUE CHECK(orden>=0),
 tipo varchar(10) NOT NULL CHECK(tipo IN ('ABIERTA','GANADA','PERDIDA'))
);
INSERT INTO public.crm_etapa(nombre,orden,tipo) VALUES
 ('Nuevo',0,'ABIERTA'),('Calificado',1,'ABIERTA'),('Propuesta',2,'ABIERTA'),('Ganado',3,'GANADA'),('Perdido',4,'PERDIDA');
CREATE TABLE public.crm_oportunidad (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY, nombre varchar(160) NOT NULL,
 contacto_id bigint NOT NULL REFERENCES public.crm_contacto(id) ON DELETE CASCADE,
 etapa_id bigint NOT NULL REFERENCES public.crm_etapa(id),
 responsable_id bigint NOT NULL REFERENCES public.usuario(id_usuario),
 valor numeric(16,2) NOT NULL CHECK(valor>=0), moneda varchar(3) NOT NULL DEFAULT 'PEN' CHECK(moneda IN ('PEN','USD','EUR')),
 probabilidad int NOT NULL CHECK(probabilidad BETWEEN 0 AND 100), fecha_cierre date NOT NULL,
 motivo_perdida varchar(1000), cerrada_en timestamptz,
 version int NOT NULL DEFAULT 0, created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE public.crm_actividad (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY, nombre varchar(160) NOT NULL,
 tipo varchar(20) NOT NULL CHECK(tipo IN ('LLAMADA','REUNION','SEGUIMIENTO','RECORDATORIO')),
 estado varchar(20) NOT NULL CHECK(estado IN ('PENDIENTE','COMPLETADA','CANCELADA')),
 fecha timestamptz NOT NULL, responsable_id bigint NOT NULL REFERENCES public.usuario(id_usuario),
 contacto_id bigint REFERENCES public.crm_contacto(id) ON DELETE CASCADE,
 cuenta_id bigint REFERENCES public.crm_cuenta(id) ON DELETE CASCADE,
 oportunidad_id bigint REFERENCES public.crm_oportunidad(id) ON DELETE CASCADE,
 descripcion varchar(4000), version int NOT NULL DEFAULT 0,
 CHECK(num_nonnulls(contacto_id,cuenta_id,oportunidad_id)=1)
);
CREATE INDEX crm_actividad_responsable_fecha ON public.crm_actividad(responsable_id,fecha) WHERE estado='PENDIENTE';
CREATE TABLE public.crm_segmento (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,nombre varchar(160) NOT NULL,
 estado varchar(25), etiqueta varchar(120), origen varchar(120), etapa_id bigint REFERENCES public.crm_etapa(id),
 sin_actividad_dias int CHECK(sin_actividad_dias BETWEEN 0 AND 3650), version int NOT NULL DEFAULT 0
);
CREATE TABLE public.crm_regla_score (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,nombre varchar(160) NOT NULL,
 campo varchar(20) NOT NULL CHECK(campo IN ('ESTADO','ETIQUETA','ORIGEN','ACTIVIDAD_COMPLETADA')),
 valor varchar(120) NOT NULL,puntos int NOT NULL CHECK(puntos BETWEEN -1000 AND 1000),version int NOT NULL DEFAULT 0
);
CREATE TABLE public.crm_origen (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,contacto_id bigint NOT NULL REFERENCES public.crm_contacto(id) ON DELETE CASCADE,
 campana_id bigint REFERENCES public.campana(id_campana) ON DELETE SET NULL,
 canal varchar(120), utm_source varchar(120),utm_medium varchar(120),utm_campaign varchar(120),
 created_at timestamptz NOT NULL DEFAULT now(), idempotencia uuid NOT NULL UNIQUE
);
CREATE TABLE public.crm_formulario (
 id uuid PRIMARY KEY, campana_id bigint NOT NULL REFERENCES public.campana(id_campana),
 nombre varchar(160) NOT NULL, responsable_id bigint NOT NULL REFERENCES public.usuario(id_usuario),
 activo boolean NOT NULL DEFAULT true, secreto_hash varchar(64) NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE public.crm_meta (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,nombre varchar(160) NOT NULL,
 campana_id bigint NOT NULL REFERENCES public.campana(id_campana),desde date NOT NULL,hasta date NOT NULL,
 leads int NOT NULL CHECK(leads>=0),ingresos numeric(16,2) NOT NULL CHECK(ingresos>=0),
 moneda varchar(3) NOT NULL CHECK(moneda IN ('PEN','USD','EUR')),version int NOT NULL DEFAULT 0,CHECK(hasta>=desde)
);
CREATE TABLE public.crm_contenido (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,nombre varchar(160) NOT NULL,
 campana_id bigint REFERENCES public.campana(id_campana),responsable_id bigint NOT NULL REFERENCES public.usuario(id_usuario),
 estado varchar(20) NOT NULL CHECK(estado IN ('BORRADOR','EN_REVISION','APROBADO','RECHAZADO')),
 texto varchar(10000) NOT NULL,observacion varchar(1000),version int NOT NULL DEFAULT 0
);
CREATE TABLE public.crm_automatizacion (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,nombre varchar(160) NOT NULL,
 segmento_id bigint NOT NULL REFERENCES public.crm_segmento(id),
 accion varchar(20) NOT NULL CHECK(accion IN ('CREAR_TAREA','EMAIL')),
 asunto varchar(160) NOT NULL,mensaje varchar(10000),activa boolean NOT NULL DEFAULT false,
 version int NOT NULL DEFAULT 0,autor_id bigint NOT NULL REFERENCES public.usuario(id_usuario)
);
CREATE TABLE public.crm_ejecucion (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,automatizacion_id bigint NOT NULL REFERENCES public.crm_automatizacion(id),
 contacto_id bigint NOT NULL REFERENCES public.crm_contacto(id) ON DELETE CASCADE,
 estado varchar(20) NOT NULL CHECK(estado IN ('PENDIENTE','ENVIADO','COMPLETADO','ERROR','OMITIDO')),
 detalle varchar(1000),created_at timestamptz NOT NULL DEFAULT now(),UNIQUE(automatizacion_id,contacto_id)
);
CREATE TABLE public.crm_gasto_meta (
 campana_id bigint NOT NULL REFERENCES public.campana(id_campana),fecha date NOT NULL,
 moneda varchar(3) NOT NULL,importe numeric(16,2) NOT NULL CHECK(importe>=0),impresiones bigint NOT NULL,clicks bigint NOT NULL,
 cuenta_externa varchar(120) NOT NULL,campana_externa varchar(120) NOT NULL,
 PRIMARY KEY(campana_id,fecha,cuenta_externa,campana_externa)
);
CREATE TABLE public.crm_integracion_meta (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,nombre varchar(160) NOT NULL,
 campana_id bigint NOT NULL REFERENCES public.campana(id_campana),
 campana_externa varchar(120) NOT NULL CHECK(campana_externa ~ '^[0-9]+$'),
 version int NOT NULL DEFAULT 0
);
DO $$ DECLARE t text; BEGIN
 FOREACH t IN ARRAY ARRAY['crm_etapa','crm_oportunidad','crm_actividad','crm_segmento','crm_regla_score','crm_origen','crm_formulario','crm_meta','crm_contenido','crm_automatizacion','crm_ejecucion','crm_gasto_meta','crm_integracion_meta'] LOOP
 EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY',t);
 END LOOP;
END $$;
INSERT INTO public.permiso(codigo,descripcion,modulo)
SELECT 'CRM_'||r||'_'||a,'CRM: '||r||' / '||a,'CRM'
FROM unnest(ARRAY['PIPELINE','ETAPAS','ACTIVIDADES','SEGMENTOS','SCORING','ATRIBUCION','METAS','CONTENIDO','AUTOMATIZACIONES','INTEGRACIONES','AUDITORIA']) r
CROSS JOIN unnest(ARRAY['LEER','GESTIONAR']) a
WHERE NOT EXISTS(SELECT 1 FROM public.permiso p WHERE p.codigo='CRM_'||r||'_'||a);
INSERT INTO public.rol_permiso(id_rol,id_permiso)
SELECT r.id_rol,p.id_permiso FROM public.rol r CROSS JOIN public.permiso p
WHERE p.modulo='CRM' AND (
 lower(r.nombre_rol) ~ 'admin'
 OR lower(r.nombre_rol) ~ '(lider|líder|manager|lead)' AND p.codigo !~ '(ETAPAS|INTEGRACIONES|AUDITORIA)'
 OR lower(r.nombre_rol) ~ '(analista|operador)' AND (p.codigo ~ 'CRM_(ACTIVIDADES|SEGMENTOS|CONTENIDO)_' OR p.codigo='CRM_ATRIBUCION_LEER'))
AND NOT EXISTS(SELECT 1 FROM public.rol_permiso rp WHERE rp.id_rol=r.id_rol AND rp.id_permiso=p.id_permiso);
COMMIT;
