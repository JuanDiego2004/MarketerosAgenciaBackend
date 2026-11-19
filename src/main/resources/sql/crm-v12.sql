BEGIN;
CREATE TABLE public.crm_alta_contrato (
 id uuid PRIMARY KEY,autor_id bigint NOT NULL REFERENCES public.usuario(id_usuario),datos jsonb NOT NULL DEFAULT '{}',
 version int NOT NULL DEFAULT 0,contrato_id bigint UNIQUE REFERENCES public.crm_acuerdo(id),updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE public.crm_alta_archivo (
 id uuid PRIMARY KEY,alta_id uuid NOT NULL REFERENCES public.crm_alta_contrato(id),nombre varchar(160) NOT NULL,mime varchar(40) NOT NULL,
 contenido bytea NOT NULL CHECK(octet_length(contenido) BETWEEN 1 AND 20971520),created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE public.crm_contrato_config (
 contrato_id bigint PRIMARY KEY REFERENCES public.crm_acuerdo(id),datos jsonb NOT NULL,alta_id uuid UNIQUE REFERENCES public.crm_alta_contrato(id)
);
CREATE TABLE public.crm_contrato_anuncio (
 contrato_id bigint NOT NULL REFERENCES public.crm_acuerdo(id),anuncio_id bigint NOT NULL REFERENCES public.crm_anuncio(id),
 origen varchar(15) NOT NULL CHECK(origen IN ('CREADO','REFERENCIA')),PRIMARY KEY(contrato_id,anuncio_id)
);
INSERT INTO public.crm_contrato_anuncio SELECT contrato_id,id,CASE WHEN estado='EXTERNO_SIMULADO' THEN 'REFERENCIA' ELSE 'CREADO' END FROM public.crm_anuncio;
ALTER TABLE public.crm_anuncio ADD COLUMN referencia_externa varchar(160);
ALTER TABLE public.crm_anuncio ADD COLUMN programado_en timestamptz;
ALTER TABLE public.crm_anuncio ADD COLUMN requiere_aprobacion_cliente boolean NOT NULL DEFAULT false;
CREATE TABLE public.crm_contrato_evento (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,contrato_id bigint NOT NULL REFERENCES public.crm_acuerdo(id),
 accion varchar(100) NOT NULL,autor_id bigint NOT NULL REFERENCES public.usuario(id_usuario),created_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE public.crm_alta_contrato ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.crm_alta_archivo ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.crm_contrato_config ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.crm_contrato_anuncio ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.crm_contrato_evento ENABLE ROW LEVEL SECURITY;
CREATE INDEX crm_alta_autor ON public.crm_alta_contrato(autor_id,updated_at DESC);
CREATE INDEX crm_vinculo_anuncio ON public.crm_contrato_anuncio(anuncio_id);
CREATE INDEX crm_referencia_anuncio ON public.crm_anuncio(cuenta_id,referencia_externa);
COMMIT;
