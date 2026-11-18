BEGIN;
-- Expansión compatible: no cambia datos ni contratos anteriores de Meta/SMTP.
CREATE TABLE public.crm_conector (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 fuente varchar(20) NOT NULL CHECK(fuente IN ('GOOGLE_ADS','GA4')),
 cuenta_externa varchar(30) NOT NULL CHECK(cuenta_externa ~ '^[0-9]{1,30}$'),
 campana_externa varchar(200) NOT NULL,
 campana_id bigint NOT NULL REFERENCES public.campana(id_campana),
 activo boolean NOT NULL DEFAULT true,version int NOT NULL DEFAULT 0,
 sincronizado_en timestamptz,ultimo_error varchar(500),
 UNIQUE(fuente,cuenta_externa,campana_externa)
);
CREATE TABLE public.crm_gasto_google (
 conector_id bigint NOT NULL REFERENCES public.crm_conector(id),
 campana_id bigint NOT NULL REFERENCES public.campana(id_campana),fecha date NOT NULL,
 moneda varchar(3) NOT NULL,importe numeric(20,6) NOT NULL CHECK(importe>=0),
 impresiones bigint NOT NULL CHECK(impresiones>=0),clicks bigint NOT NULL CHECK(clicks>=0),
 PRIMARY KEY(conector_id,fecha)
);
CREATE TABLE public.crm_analitica (
 conector_id bigint NOT NULL REFERENCES public.crm_conector(id),fecha date NOT NULL,
 usuarios bigint NOT NULL CHECK(usuarios>=0),sesiones bigint NOT NULL CHECK(sesiones>=0),
 eventos_clave numeric(20,6) NOT NULL CHECK(eventos_clave>=0),PRIMARY KEY(conector_id,fecha)
);
ALTER TABLE public.crm_baja_token ADD COLUMN canal varchar(20) NOT NULL DEFAULT 'EMAIL' CHECK(canal IN ('EMAIL','WHATSAPP','SMS'));
CREATE TABLE public.crm_baja_canal (
 contacto_id bigint NOT NULL REFERENCES public.crm_contacto(id) ON DELETE CASCADE,
 canal varchar(20) NOT NULL CHECK(canal IN ('WHATSAPP','SMS')),fecha timestamptz NOT NULL DEFAULT now(),PRIMARY KEY(contacto_id,canal)
);
CREATE TABLE public.crm_envio (
 ejecucion_id bigint NOT NULL REFERENCES public.crm_ejecucion(id) ON DELETE CASCADE,
 paso_id varchar(50) NOT NULL,canal varchar(20) NOT NULL CHECK(canal IN ('WHATSAPP','SMS')),
 estado varchar(20) NOT NULL CHECK(estado IN ('INCIERTO','ACEPTADO','OMITIDO')),
 proveedor_id varchar(200),fecha timestamptz NOT NULL DEFAULT now(),PRIMARY KEY(ejecucion_id,paso_id)
);
ALTER TABLE public.crm_conector ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.crm_gasto_google ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.crm_analitica ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.crm_baja_canal ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.crm_envio ENABLE ROW LEVEL SECURITY;
COMMIT;
