-- Expansión: campos personalizados y ejecución persistente de flujos visuales.
BEGIN;
CREATE TABLE public.crm_campo (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 codigo varchar(60) NOT NULL UNIQUE CHECK(codigo ~ '^[a-z][a-z0-9_]{0,59}$'),
 nombre varchar(120) NOT NULL,
 tipo varchar(20) NOT NULL CHECK(tipo IN ('TEXTO','NUMERO','FECHA','BOOLEANO','OPCION')),
 opciones jsonb NOT NULL DEFAULT '[]'::jsonb CHECK(jsonb_typeof(opciones)='array'),
 activo boolean NOT NULL DEFAULT true,version int NOT NULL DEFAULT 0
);
CREATE TABLE public.crm_campo_valor (
 contacto_id bigint NOT NULL REFERENCES public.crm_contacto(id) ON DELETE CASCADE,
 campo_id bigint NOT NULL REFERENCES public.crm_campo(id),valor jsonb NOT NULL,
 PRIMARY KEY(contacto_id,campo_id)
);
CREATE TABLE public.crm_campos_revision (
 contacto_id bigint PRIMARY KEY REFERENCES public.crm_contacto(id) ON DELETE CASCADE,
 version int NOT NULL DEFAULT 0
);
CREATE TABLE public.crm_flujo (
 automatizacion_id bigint PRIMARY KEY REFERENCES public.crm_automatizacion(id) ON DELETE CASCADE,
 definicion jsonb NOT NULL CHECK(jsonb_typeof(definicion)='object')
);
ALTER TABLE public.crm_ejecucion ADD COLUMN definicion_flujo jsonb;
ALTER TABLE public.crm_ejecucion ADD COLUMN paso_id varchar(50);
ALTER TABLE public.crm_ejecucion ADD COLUMN reanudar_en timestamptz;
ALTER TABLE public.crm_automatizacion ADD COLUMN ultimo_error varchar(1000);
ALTER TABLE public.crm_automatizacion ADD COLUMN ultima_ejecucion timestamptz;
ALTER TABLE public.crm_ejecucion DROP CONSTRAINT crm_ejecucion_estado_check;
ALTER TABLE public.crm_ejecucion ADD CONSTRAINT crm_ejecucion_estado_check CHECK(estado IN ('PENDIENTE','ENVIADO','COMPLETADO','ERROR','OMITIDO','INCIERTO'));
CREATE INDEX crm_ejecucion_pendiente ON public.crm_ejecucion(automatizacion_id,reanudar_en) WHERE estado='PENDIENTE';
CREATE TABLE public.crm_flujo_paso (
 ejecucion_id bigint NOT NULL REFERENCES public.crm_ejecucion(id) ON DELETE CASCADE,
 paso_id varchar(50) NOT NULL,nombre varchar(160) NOT NULL,estado varchar(20) NOT NULL,
 detalle varchar(1000),fecha timestamptz NOT NULL DEFAULT now(),PRIMARY KEY(ejecucion_id,paso_id)
);
ALTER TABLE public.crm_campo ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.crm_campo_valor ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.crm_campos_revision ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.crm_flujo ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.crm_flujo_paso ENABLE ROW LEVEL SECURITY;
INSERT INTO public.permiso(codigo,descripcion,modulo) VALUES
 ('CRM_CAMPOS_LEER','Consultar el catálogo de campos CRM','CRM'),
 ('CRM_CAMPOS_GESTIONAR','Configurar campos CRM','CRM');
INSERT INTO public.rol_permiso(id_rol,id_permiso)
SELECT r.id_rol,p.id_permiso FROM public.rol r CROSS JOIN public.permiso p WHERE lower(r.nombre_rol) LIKE '%admin%' AND p.codigo IN ('CRM_CAMPOS_LEER','CRM_CAMPOS_GESTIONAR');
COMMIT;
